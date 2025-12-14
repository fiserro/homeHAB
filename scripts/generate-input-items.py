#!/usr/bin/env python3
"""
Generate OpenHAB items for HrvState @InputItem annotations.

This script:
1. Parses HrvState.java to find all @InputItem annotated fields
2. Extracts InputType enum values from the annotations
3. Reads InputType.java to get default values and data types
4. Generates input-items.items with proper item definitions
"""

import os
import re
import sys
from pathlib import Path


def parse_input_type_enum(input_type_path):
    """Parse InputType.java to extract default values and data types."""
    with open(input_type_path, 'r') as f:
        content = f.read()

    # Extract enum constants with their properties
    # Pattern: ENUM_NAME(DataType.class, AggregationType.XXX, defaultValue)
    pattern = r'(\w+)\s*\(\s*(\w+)\.class\s*,\s*\w+\.\w+\s*,\s*(\d+|Constants\.\w+)\s*\)'

    # First, extract Constants values
    constants = {}
    const_pattern = r'public static final int (\w+)\s*=\s*(\d+);'
    for match in re.finditer(const_pattern, content):
        constants[f'Constants.{match.group(1)}'] = int(match.group(2))

    input_types = {}
    for match in re.finditer(pattern, content):
        name = match.group(1)
        data_type = match.group(2)
        default_str = match.group(3)

        # Resolve default value
        if default_str.startswith('Constants.'):
            default_value = constants.get(default_str, 0)
        else:
            default_value = int(default_str)

        # Map Java types to OpenHAB item types
        if data_type == 'Boolean':
            item_type = 'Switch'
        elif data_type == 'Number':
            item_type = 'Number'
        else:
            item_type = 'String'

        input_types[name] = {
            'type': item_type,
            'default': default_value
        }

    return input_types


def parse_hrv_state(hrv_state_path, input_types):
    """Parse HrvState.java to extract @InputItem annotations and field names."""
    with open(hrv_state_path, 'r') as f:
        content = f.read()

    # Pattern: @InputItem(ENUM_VALUE) type fieldName
    pattern = r'@InputItem\((\w+)\)\s+\w+\s+(\w+)'

    items = []
    for match in re.finditer(pattern, content):
        input_type = match.group(1)
        field_name = match.group(2)

        if input_type not in input_types:
            print(f"Warning: Unknown InputType: {input_type}", file=sys.stderr)
            continue

        type_info = input_types[input_type]

        # Convert camelCase to snake_case for item name
        item_name = 'hrv_' + re.sub(r'([A-Z])', r'_\1', field_name).lower().lstrip('_')

        # Generate label from field name
        label = field_name[0].upper() + re.sub(r'([A-Z])', r' \1', field_name[1:])

        items.append({
            'name': item_name,
            'type': type_info['type'],
            'label': f"HRV - {label}",
            'default': type_info['default'],
            'input_type': input_type
        })

    return items


def generate_items_file(items, output_path):
    """Generate the input-items.items file."""
    lines = [
        "// Auto-generated from HrvState.java @InputItem annotations",
        "// DO NOT EDIT - changes will be overwritten",
        "",
        "Group gHrvInputs \"HRV Inputs\"",
        ""
    ]

    for item in items:
        # Determine icon based on item type
        if 'mode' in item['name'].lower():
            icon = 'switch'
        elif 'power' in item['name'].lower():
            icon = 'energy'
        elif 'threshold' in item['name'].lower():
            icon = 'line'
        else:
            icon = 'settings'

        # Generate item definition
        lines.append(
            f'{item["type"]} {item["name"]} "{item["label"]}" '
            f'<{icon}> (gHrvInputs) '
            f'{{ stateDescription="" [pattern="%.0f"] }}'
        )

    lines.append("")

    with open(output_path, 'w') as f:
        f.write('\n'.join(lines))

    print(f"Generated {len(items)} input items in {output_path}")


def main():
    # Determine project root
    script_dir = Path(__file__).parent
    project_root = script_dir.parent

    # Input files
    input_type_path = project_root / 'src/main/java/io/github/fiserro/homehab/hrv/InputType.java'
    hrv_state_path = project_root / 'src/main/java/io/github/fiserro/homehab/hrv/HrvState.java'

    # Output file
    output_path = project_root / 'openhab-dev/conf/items/input-items.items'

    # Verify input files exist
    if not input_type_path.exists():
        print(f"Error: {input_type_path} not found", file=sys.stderr)
        sys.exit(1)

    if not hrv_state_path.exists():
        print(f"Error: {hrv_state_path} not found", file=sys.stderr)
        sys.exit(1)

    # Parse InputType enum
    print(f"Parsing InputType enum from {input_type_path}...")
    input_types = parse_input_type_enum(input_type_path)
    print(f"Found {len(input_types)} InputType enum values")

    # Parse HrvState
    print(f"Parsing HrvState from {hrv_state_path}...")
    items = parse_hrv_state(hrv_state_path, input_types)
    print(f"Found {len(items)} @InputItem annotations")

    # Generate items file
    output_path.parent.mkdir(parents=True, exist_ok=True)
    generate_items_file(items, output_path)

    print("\nDone! Restart OpenHAB to load new items:")
    print("  docker-compose restart openhab")


if __name__ == '__main__':
    main()