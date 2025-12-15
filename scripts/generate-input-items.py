#!/usr/bin/env python3
"""
Generate OpenHAB items for HabState @InputItem annotations.

This script:
1. Parses HabState.java to find all @InputItem annotated fields
2. Extracts field names, types, and default values from HabStateBuilder
3. Generates input-items.items with proper item definitions
"""

import os
import re
import sys
from pathlib import Path


def parse_default_values(content):
    """Parse HabStateBuilder constructor to extract default values."""
    defaults = {}

    # Find the constructor body
    constructor_pattern = r'public HabStateBuilder\(\)\s*\{([^}]+)\}'
    constructor_match = re.search(constructor_pattern, content, re.DOTALL)

    if constructor_match:
        constructor_body = constructor_match.group(1)

        # Pattern: fieldName = value;
        assignment_pattern = r'(\w+)\s*=\s*([^;]+);'
        for match in re.finditer(assignment_pattern, constructor_body):
            field_name = match.group(1).strip()
            value = match.group(2).strip()

            # Handle boolean values
            if value == 'false':
                defaults[field_name] = 'OFF'
            elif value == 'true':
                defaults[field_name] = 'ON'
            else:
                # Handle numeric values (including expressions like "8 * 60 * 60")
                try:
                    # Evaluate simple arithmetic expressions
                    evaluated = eval(value)
                    defaults[field_name] = str(evaluated)
                except:
                    defaults[field_name] = value

    return defaults


def parse_hab_state(hab_state_path):
    """Parse HabState.java to extract @InputItem annotations and field information."""
    with open(hab_state_path, 'r') as f:
        content = f.read()

    # Extract default values from constructor
    defaults = parse_default_values(content)

    # Pattern: @InputItem type fieldName
    pattern = r'@InputItem\s+(\w+)\s+(\w+)'

    items = []
    for match in re.finditer(pattern, content):
        field_type = match.group(1)
        field_name = match.group(2)

        # Map Java types to OpenHAB item types
        if field_type == 'boolean':
            item_type = 'Switch'
        elif field_type == 'int':
            item_type = 'Number'
        else:
            item_type = 'String'

        # Use field name directly (camelCase)
        item_name = field_name

        # Generate label from field name (camelCase to Title Case)
        label = field_name[0].upper() + re.sub(r'([A-Z])', r' \1', field_name[1:])

        # Get default value
        default_value = defaults.get(field_name, None)

        items.append({
            'name': item_name,
            'type': item_type,
            'label': f"HRV - {label}",
            'field_type': field_type,
            'default': default_value
        })

    return items


def generate_items_file(items, output_path):
    """Generate the input-items.items file."""
    lines = [
        "// Auto-generated from HabState.java @InputItem annotations",
        "// DO NOT EDIT - changes will be overwritten",
        "",
        "Group gHrvInputs \"HRV Inputs\"",
        ""
    ]

    for item in items:
        # Determine icon based on item name (check duration first, then mode)
        if 'duration' in item['name'].lower():
            icon = 'time'
        elif 'mode' in item['name'].lower():
            icon = 'switch'
        elif 'power' in item['name'].lower():
            icon = 'energy'
        elif 'threshold' in item['name'].lower():
            icon = 'line'
        else:
            icon = 'settings'

        # Get default value
        default = item.get('default')
        default_comment = f"  // default: {default}" if default else ""

        # Generate item definition with default value
        item_def = (
            f'{item["type"]} {item["name"]} "{item["label"]}" '
            f'<{icon}> (gHrvInputs)'
        )
        lines.append(item_def + default_comment)

    lines.append("")

    with open(output_path, 'w') as f:
        f.write('\n'.join(lines))

    print(f"Generated {len(items)} input items in {output_path}")

    # Generate initialization script
    init_script_path = output_path.parent / 'input-items-init.py'
    generate_init_script(items, init_script_path)


def generate_init_script(items, output_path):
    """Generate Python initialization script to set default values via REST API."""
    lines = [
        "#!/usr/bin/env python3",
        '"""',
        "Initialize input items with default values via OpenHAB REST API.",
        "Usage: python3 input-items-init.py [openhab-url]",
        '"""',
        "",
        "import requests",
        "import sys",
        "",
        "OPENHAB_URL = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8888'",
        "REST_API = f'{OPENHAB_URL}/rest/items'",
        "",
        "items_to_init = [",
    ]

    for item in items:
        default = item.get('default')
        if default:
            lines.append(f"    ('{item['name']}', '{default}'),")

    lines.extend([
        "]",
        "",
        "print(f'Initializing {len(items_to_init)} items...')",
        "for item_name, default_value in items_to_init:",
        "    try:",
        "        response = requests.post(",
        "            f'{REST_API}/{item_name}/state',",
        "            data=default_value,",
        "            headers={'Content-Type': 'text/plain', 'Accept': 'application/json'}",
        "        )",
        "        if response.status_code in [200, 201, 202]:",
        "            print(f'  ✓ {item_name} = {default_value}')",
        "        else:",
        "            print(f'  ✗ {item_name}: {response.status_code} - {response.text}')",
        "    except Exception as e:",
        "        print(f'  ✗ {item_name}: {e}')",
        "",
        "print('Done!')",
    ])

    with open(output_path, 'w') as f:
        f.write('\n'.join(lines))

    # Make script executable
    output_path.chmod(0o755)

    print(f"Generated initialization script: {output_path}")


def main():
    # Determine project root
    script_dir = Path(__file__).parent
    project_root = script_dir.parent

    # Input file
    hab_state_path = project_root / 'src/main/java/io/github/fiserro/homehab/HabState.java'

    # Output file
    output_path = project_root / 'openhab-dev/conf/items/input-items.items'

    # Verify input file exists
    if not hab_state_path.exists():
        print(f"Error: {hab_state_path} not found", file=sys.stderr)
        sys.exit(1)

    # Parse HabState
    print(f"Parsing HabState from {hab_state_path}...")
    items = parse_hab_state(hab_state_path)
    print(f"Found {len(items)} @InputItem annotations")

    # Generate items file
    output_path.parent.mkdir(parents=True, exist_ok=True)
    generate_items_file(items, output_path)

    print("\nDone! Restart OpenHAB to load new items:")
    print("  docker-compose restart openhab")


if __name__ == '__main__':
    main()