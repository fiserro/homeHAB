#!/usr/bin/env python3
"""
Generate hrv-config.items from HrvConfig.java interface.

Usage:
    python3 scripts/generate-hrv-config.py
"""

import re
from pathlib import Path
from typing import List, Tuple


def parse_hrv_config(java_file: Path) -> List[Tuple[str, int, str]]:
    """
    Parse HrvConfig.java and extract @Option methods with defaults.
    Returns list of (method_name, default_value, description) tuples.
    """
    content = java_file.read_text()
    options = []

    # Pattern to match @Option methods with default values
    # Matches: @Option ... default int methodName() { return value; }
    pattern = r'@Option\s+default\s+int\s+(\w+)\s*\(\s*\)\s*\{\s*return\s+(\d+)\s*;'

    matches = re.finditer(pattern, content, re.MULTILINE)

    for match in matches:
        method_name = match.group(1)
        default_value = int(match.group(2))

        # Convert camelCase to human-readable label
        # Example: humidityThreshold -> Humidity Threshold
        label = re.sub(r'([A-Z])', r' \1', method_name)
        label = label.strip().title()

        # Convert method name to item name: humidityThreshold -> humidity_threshold
        item_name = re.sub(r'([A-Z])', r'_\1', method_name).lower()

        options.append((item_name, default_value, label))

    return options


def generate_items_file(options: List[Tuple[str, int, str]], output_file: Path):
    """Generate hrv-config.items file."""
    lines = [
        '// HRV Configuration Items',
        '// Auto-generated from HrvConfig.java interface',
        '// DO NOT EDIT MANUALLY - regenerate using generate-hrv-config.py',
        '',
        'Group gHrvConfig "HRV Configuration"',
        '',
    ]

    # Group by category
    threshold_items = []
    power_items = []
    timeout_items = []

    for item_name, default_value, label in options:
        if 'threshold' in item_name:
            unit = '%%' if 'humidity' in item_name else 'ppm'
            threshold_items.append(
                f'Number hrv_config_{item_name} "{label} [%d {unit}]" (gHrvConfig)'
            )
        elif 'timeout' in item_name:
            timeout_items.append(
                f'Number hrv_config_{item_name} "{label} [%d min]" (gHrvConfig)'
            )
        else:
            power_items.append(
                f'Number hrv_config_{item_name} "{label} [%d %%]" (gHrvConfig)'
            )

    # Write sections
    if threshold_items:
        lines.append('// Threshold values')
        lines.extend(threshold_items)
        lines.append('')

    if power_items:
        lines.append('// Power levels for different modes (0-100%)')
        lines.extend(power_items)
        lines.append('')

    if timeout_items:
        lines.append('// Timeouts')
        lines.extend(timeout_items)
        lines.append('')

    # Write file
    output_file.parent.mkdir(parents=True, exist_ok=True)
    output_file.write_text('\n'.join(lines))
    print(f"✅ Generated: {output_file}")
    print(f"   {len(options)} configuration items")


def main():
    # Paths
    project_root = Path(__file__).parent.parent
    java_file = project_root / "src/main/java/io/github/fiserro/homehab/hrv/HrvConfig.java"
    output_file = project_root / "openhab-dev/conf/items/hrv-config.items"

    print("🔧 Generating HRV configuration items...")
    print(f"   Source: {java_file}")

    if not java_file.exists():
        print(f"❌ Error: {java_file} not found")
        return 1

    # Parse and generate
    options = parse_hrv_config(java_file)

    if not options:
        print("❌ No @Option methods found in HrvConfig.java")
        return 1

    generate_items_file(options, output_file)

    print("\n✨ Configuration items generated successfully!")
    print("\n📝 Next steps:")
    print("  1. Review generated file")
    print("  2. Restart OpenHAB: docker-compose restart openhab")

    return 0


if __name__ == '__main__':
    exit(main())
