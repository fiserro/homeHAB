#!/usr/bin/env python3
"""
Generate output items from *Control.java files.

Usage:
    python3 scripts/generate-output-items.py
"""

import re
from pathlib import Path
from typing import List, Dict, Any


def parse_output_items(java_file: Path) -> List[Dict[str, Any]]:
    """
    Parse *Control.java file and extract @OutputItem annotations with constants.
    Returns list of output item definitions.
    """
    content = java_file.read_text()
    items = []

    # Pattern to match @OutputItem annotation followed by constant declaration
    # Matches:
    #   @OutputItem(type = "Dimmer", label = "HRV Output Power", icon = "fan")
    #   public static final String OUTPUT_ITEM = "hrv_output_power";
    pattern = r'@OutputItem\s*\(\s*type\s*=\s*"([^"]+)"\s*,\s*label\s*=\s*"([^"]+)"\s*,\s*icon\s*=\s*"([^"]+)"\s*\)\s+public\s+static\s+final\s+String\s+\w+\s*=\s*"([^"]+)"\s*;'

    matches = re.finditer(pattern, content, re.MULTILINE | re.DOTALL)

    for match in matches:
        item_type = match.group(1)  # e.g., "Dimmer"
        label = match.group(2)       # e.g., "HRV Output Power"
        icon = match.group(3)        # e.g., "fan"
        item_name = match.group(4)   # e.g., "hrv_output_power"

        items.append({
            'type': item_type,
            'label': label,
            'icon': icon,
            'name': item_name,
            'source_file': java_file.name
        })

    return items


def generate_items_file(all_items: List[Dict[str, Any]], output_file: Path):
    """Generate output-items.items file."""
    lines = [
        '// Output Items',
        '// Auto-generated from *Control.java files',
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

            lines.append(f'// From {source}')
            lines.append(f'{item_type} {item_name} "{label}" <{icon}> (gOutputs)')
            lines.append('')

    # Write file
    output_file.parent.mkdir(parents=True, exist_ok=True)
    output_file.write_text('\n'.join(lines))
    print(f"✅ Generated: {output_file}")
    print(f"   {len(all_items)} output items")


def main():
    # Paths
    project_root = Path(__file__).parent.parent
    scripts_dir = project_root / "openhab-dev/conf/automation/jsr223"
    output_file = project_root / "openhab-dev/conf/items/output-items.items"

    print("🔧 Generating output items...")
    print(f"   Scanning: {scripts_dir}")

    if not scripts_dir.exists():
        print(f"❌ Error: {scripts_dir} not found")
        return 1

    # Find all *Control.java files
    control_files = list(scripts_dir.glob("*Control.java"))

    if not control_files:
        print(f"⚠️  No *Control.java files found in {scripts_dir}")
        return 1

    print(f"   Found {len(control_files)} Control files:")
    for f in control_files:
        print(f"     - {f.name}")

    # Parse all files
    all_items = []
    for java_file in control_files:
        items = parse_output_items(java_file)
        if items:
            print(f"   ✓ {java_file.name}: {len(items)} output items")
            all_items.extend(items)
        else:
            print(f"   ⚠️  {java_file.name}: no output items found")

    # Generate items file
    generate_items_file(all_items, output_file)

    print("\n✨ Output items generated successfully!")
    print("\n📝 Next steps:")
    print("  1. Review generated file")
    print("  2. Restart OpenHAB: docker-compose restart openhab")

    return 0


if __name__ == '__main__':
    exit(main())
