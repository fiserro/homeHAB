#!/usr/bin/env python3
"""
Generate output items from HabState.java @OutputItem annotations.

Usage:
    python3 scripts/generate-output-items.py
"""

import re
from pathlib import Path
from typing import List, Dict, Any


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


def parse_output_items(java_file: Path) -> List[Dict[str, Any]]:
    """
    Parse HabState.java and extract @OutputItem annotated fields.
    Returns list of output item definitions.
    """
    content = java_file.read_text()

    # Extract default values from constructor
    defaults = parse_default_values(content)

    items = []

    # Pattern to match @OutputItem annotation followed by field declaration
    # Matches: @With @OutputItem type fieldName
    # or: @OutputItem type fieldName
    pattern = r'(?:@\w+\s+)*@OutputItem\s+(\w+)\s+(\w+)'

    matches = re.finditer(pattern, content, re.MULTILINE)

    for match in matches:
        field_type = match.group(1)  # e.g., "int"
        field_name = match.group(2)  # e.g., "hrvOutputPower"

        # Map Java types to OpenHAB item types
        if field_type == 'boolean':
            item_type = 'Switch'
        elif field_type == 'int':
            item_type = 'Dimmer'  # Output power items typically use Dimmer
        elif field_type == 'float' or field_type == 'double':
            item_type = 'Number'
        else:
            item_type = 'String'

        # Generate label from field name (camelCase to Title Case)
        label = field_name[0].upper() + re.sub(r'([A-Z])', r' \1', field_name[1:])

        # Determine icon based on field name
        if 'power' in field_name.lower():
            icon = 'energy'
        elif 'hrv' in field_name.lower():
            icon = 'fan'
        else:
            icon = 'settings'

        # Get default value
        default_value = defaults.get(field_name, None)

        items.append({
            'type': item_type,
            'label': label,
            'icon': icon,
            'name': field_name,
            'source_file': java_file.name,
            'default': default_value
        })

    return items


def generate_items_file(all_items: List[Dict[str, Any]], output_file: Path):
    """Generate output-items.items file."""
    lines = [
        '// Output Items',
        '// Auto-generated from HabState.java @OutputItem annotations',
        '// DO NOT EDIT MANUALLY - regenerate using generate-output-items.py',
        '',
        'Group gOutputs "Outputs"',
        ''
    ]

    if not all_items:
        lines.append('// No output items found')
        lines.append('')
    else:
        for item in all_items:
            item_type = item['type']
            item_name = item['name']
            label = item['label']
            icon = item['icon']
            source = item['source_file']
            default = item.get('default')

            default_comment = f"  // default: {default}" if default else ""

            lines.append(f'// From {source}')
            lines.append(f'{item_type} {item_name} "{label}" <{icon}> (gOutputs)' + default_comment)
            lines.append('')

    # Write file
    output_file.parent.mkdir(parents=True, exist_ok=True)
    output_file.write_text('\n'.join(lines))
    print(f"✅ Generated: {output_file}")
    print(f"   {len(all_items)} output items")

    # Generate initialization script
    init_script_path = output_file.parent / 'output-items-init.sh'
    generate_init_script(all_items, init_script_path)


def generate_init_script(items: List[Dict[str, Any]], output_path: Path):
    """Generate bash initialization script to set default values via REST API."""
    lines = [
        '#!/bin/bash',
        '# Initialize output items with default values via OpenHAB REST API',
        '# Usage: ./output-items-init.sh [openhab-url]',
        '',
        'OPENHAB_URL="${1:-http://localhost:8888}"',
        '',
        'items=(',
    ]

    for item in items:
        default = item.get('default')
        if default:
            lines.append(f'    "{item["name"]}:{default}"')

    lines.extend([
        ')',
        '',
        'echo "Initializing ${#items[@]} output items..."',
        '',
        'for item in "${items[@]}"; do',
        '    IFS=\':\' read -r name value <<< "$item"',
        '    response=$(curl -s -o /dev/null -w "%{http_code}" \\',
        '        -X PUT \\',
        '        -H "Content-Type: text/plain" \\',
        '        --data "$value" \\',
        '        "$OPENHAB_URL/rest/items/$name/state")',
        '    ',
        '    if [[ "$response" =~ ^(200|201|202|204)$ ]]; then',
        '        echo "  ✓ $name = $value"',
        '    else',
        '        echo "  ✗ $name: HTTP $response"',
        '    fi',
        'done',
        '',
        'echo "Done!"',
    ])

    output_path.write_text('\n'.join(lines))
    output_path.chmod(0o755)

    print(f"✅ Generated initialization script: {output_path}")


def main():
    # Paths
    project_root = Path(__file__).parent.parent
    hab_state_file = project_root / "src/main/java/io/github/fiserro/homehab/HabState.java"
    output_file = project_root / "openhab-dev/conf/items/output-items.items"

    print("🔧 Generating output items...")
    print(f"   Scanning: {hab_state_file}")

    if not hab_state_file.exists():
        print(f"❌ Error: {hab_state_file} not found")
        return 1

    # Parse HabState.java
    items = parse_output_items(hab_state_file)

    if items:
        print(f"   ✓ {hab_state_file.name}: {len(items)} output items")
    else:
        print(f"   ⚠️  {hab_state_file.name}: no output items found")

    # Generate items file
    generate_items_file(items, output_file)

    print("\n✨ Output items generated successfully!")
    print("\n📝 Next steps:")
    print("  1. Review generated file")
    print("  2. Restart OpenHAB: docker-compose restart openhab")

    return 0


if __name__ == '__main__':
    exit(main())