#!/usr/bin/env python3
"""
Generate OpenHAB things and items configuration from Zigbee2MQTT devices.

Usage:
    python3 scripts/generate-zigbee-config.py [--mqtt-host HOST] [--ssh USER@HOST]

Examples:
    # Via SSH (recommended for remote Zigbee2MQTT)
    python3 scripts/generate-zigbee-config.py --ssh robertfiser@192.168.1.132

    # Via MQTT (requires mosquitto_sub)
    python3 scripts/generate-zigbee-config.py --mqtt-host zigbee.home
"""

import json
import subprocess
import argparse
import re
from pathlib import Path
from typing import List, Dict, Any, Optional, Set, Tuple
from collections import defaultdict


class ZigbeeConfigGenerator:
    """Generates OpenHAB configuration from Zigbee2MQTT devices."""

    def __init__(self, output_dir: str = "openhab-dev/conf"):
        self.output_dir = Path(output_dir)
        self.things_file = self.output_dir / "things" / "zigbee-devices.things"
        self.items_file = self.output_dir / "items" / "zigbee-devices.items"

    def fetch_devices_via_ssh(self, ssh_host: str) -> List[Dict[str, Any]]:
        """Fetch devices from Zigbee2MQTT via SSH."""
        print(f"📡 Fetching devices via SSH from {ssh_host}...")
        cmd = [
            "ssh", ssh_host,
            "mosquitto_sub -h localhost -t 'zigbee2mqtt/bridge/devices' -C 1"
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        devices = json.loads(result.stdout)
        print(f"✓ Found {len(devices)} devices")
        return devices

    def fetch_devices_via_mqtt(self, mqtt_host: str) -> List[Dict[str, Any]]:
        """Fetch devices from Zigbee2MQTT via MQTT."""
        print(f"📡 Fetching devices via MQTT from {mqtt_host}...")
        cmd = [
            "mosquitto_sub", "-h", mqtt_host,
            "-t", "zigbee2mqtt/bridge/devices", "-C", "1"
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        devices = json.loads(result.stdout)
        print(f"✓ Found {len(devices)} devices")
        return devices

    def get_metric_category(self, exp_name: str) -> str:
        """Get normalized metric category name."""
        # Normalize common metric names
        category_map = {
            'temperature': 'temperature',
            'humidity': 'humidity',
            'pressure': 'pressure',
            'co2': 'co2',
            'smoke': 'smoke',
            'contact': 'contact',
            'occupancy': 'occupancy',
            'illuminance': 'illuminance',
            'battery': 'battery',
            'voltage': 'voltage',
            'linkquality': 'linkquality',
            'link_quality': 'linkquality',
        }

        exp_lower = exp_name.lower()
        return category_map.get(exp_lower, exp_name.lower())

    def get_icon_for_category(self, category: str) -> str:
        """Get OpenHAB icon for metric category."""
        icon_map = {
            'temperature': 'temperature',
            'humidity': 'humidity',
            'pressure': 'pressure',
            'co2': 'carbondioxide',
            'smoke': 'smoke',
            'contact': 'contact',
            'occupancy': 'motion',
            'illuminance': 'light',
            'battery': 'battery',
            'voltage': 'energy',
            'linkquality': 'network',
        }
        return icon_map.get(category, 'none')

    def map_expose_to_channel(self, expose: Dict[str, Any], device_topic: str, ieee: str) -> Optional[Dict[str, Any]]:
        """Map Zigbee2MQTT expose to OpenHAB channel definition."""
        exp_type = expose.get('type')
        exp_name = expose.get('name') or expose.get('property', '')

        if not exp_name:
            return None

        # Determine OpenHAB channel type
        if exp_type == 'binary':
            channel_type = 'switch' if expose.get('access', 0) & 2 else 'contact'
        elif exp_type == 'numeric':
            channel_type = 'number'
        elif exp_type == 'enum':
            channel_type = 'string'
        elif exp_type == 'switch':
            # Composite switch type with state feature
            features = expose.get('features', [])
            if features:
                return self.map_expose_to_channel(features[0], device_topic, ieee)
            channel_type = 'switch'
        else:
            return None

        category = self.get_metric_category(exp_name)

        # Build channel configuration
        channel = {
            'type': channel_type,
            'name': exp_name,
            'category': category,
            'label': f'[zigbee, {category}]',
            'stateTopic': device_topic,
            'transformationPattern': f'JSONPATH:$.{exp_name}'
        }

        # Add commandTopic if writable (access bit 2 = set)
        if expose.get('access', 0) & 2:
            channel['commandTopic'] = f"{device_topic}/set"
            channel['transformationPattern'] = f'JSONPATH:$.{exp_name}'

            # For switches, add special formatting
            if channel_type == 'switch':
                channel['on'] = expose.get('value_on', 'ON')
                channel['off'] = expose.get('value_off', 'OFF')

        return channel

    def generate_thing(self, device: Dict[str, Any]) -> Optional[Tuple[str, str]]:
        """Generate OpenHAB Thing definition for a device.

        Returns:
            Tuple of (thing_definition, ieee_address) or None
        """
        # Skip coordinator
        if device.get('type') == 'Coordinator':
            return None

        friendly_name = device.get('friendly_name', device.get('ieee_address', 'unknown'))
        ieee = device.get('ieee_address', '').lower()
        topic = f"zigbee2mqtt/{friendly_name}"

        definition = device.get('definition', {})
        model = definition.get('model', 'Unknown')
        vendor = definition.get('vendor', 'Unknown')
        description = definition.get('description', '')

        # Generate descriptive label for Thing
        if description and description != 'Unknown':
            thing_label = description
        elif model != 'Unknown' and vendor != 'Unknown':
            thing_label = f"{model} ({vendor})"
        elif model != 'Unknown':
            thing_label = model
        else:
            thing_label = friendly_name

        # Generate channels from exposes
        channels = []
        exposes = definition.get('exposes', [])

        for expose in exposes:
            channel = self.map_expose_to_channel(expose, topic, ieee)
            if channel:
                channels.append(channel)

        if not channels:
            print(f"  ⚠️  No channels found for {friendly_name}")
            return None

        # Build Thing definition - UID format: mqtt:zigbee:<ieee>
        thing_lines = [
            f'// {vendor} {model} - {description}',
            f'// IEEE: {ieee}',
            f'Thing mqtt:topic:zigbee:{ieee} "{thing_label}" (mqtt:broker:zigbee) {{',
            '    Channels:'
        ]

        for ch in channels:
            ch_type = ch['type']
            ch_name = ch['name']
            ch_label = ch['label']
            state_topic = ch['stateTopic']
            transform = ch['transformationPattern']

            config_parts = [f'stateTopic="{state_topic}"', f'transformationPattern="{transform}"', 'retained=true']

            if 'commandTopic' in ch:
                config_parts.append(f'commandTopic="{ch["commandTopic"]}"')
            if 'on' in ch:
                config_parts.append(f'on="{ch["on"]}"')
            if 'off' in ch:
                config_parts.append(f'off="{ch["off"]}"')

            config = ', '.join(config_parts)
            thing_lines.append(f'        Type {ch_type} : {ch_name} "{ch_label}" [ {config} ]')

        thing_lines.append('}')
        thing_lines.append('')

        return ('\n'.join(thing_lines), ieee)

    def generate_items_for_device(self, device: Dict[str, Any]) -> Tuple[List[str], Dict[str, List[str]]]:
        """Generate OpenHAB Item definitions for a device.

        Returns:
            Tuple of (item_lines, category_to_items_map)
        """
        if device.get('type') == 'Coordinator':
            return ([], {})

        friendly_name = device.get('friendly_name', device.get('ieee_address', 'unknown'))
        ieee = device.get('ieee_address', '').lower()

        definition = device.get('definition', {})
        exposes = definition.get('exposes', [])

        # Get device description for better labels
        description = definition.get('description', '')
        if not description or description == 'Unknown':
            description = friendly_name

        items = []
        category_items = defaultdict(list)

        for expose in exposes:
            exp_type = expose.get('type')
            exp_name = expose.get('name') or expose.get('property', '')

            if not exp_name:
                continue

            category = self.get_metric_category(exp_name)
            icon = self.get_icon_for_category(category)

            # Determine item type
            if exp_type == 'binary':
                item_type = 'Switch' if expose.get('access', 0) & 2 else 'Contact'
            elif exp_type == 'numeric':
                item_type = 'Number'
            elif exp_type == 'enum':
                item_type = 'String'
            elif exp_type == 'switch':
                item_type = 'Switch'
            else:
                continue

            # Item name format: mqttZigbee<Category>_<ieee>
            category_title = category.title().replace('_', '')
            item_name = f'mqttZigbee{category_title}_{ieee}'

            # Label format: <device> - <category>
            label = f'{description} - {category_title}'

            # Channel ID format: mqtt:topic:zigbee:<ieee>:<exp_name>
            channel_id = f'mqtt:topic:zigbee:{ieee}:{exp_name}'

            # Group name
            # TEMPORARILY COMMENTED OUT - Groups disabled
            # group_name = f'gZigbee{category_title}'

            # Build item line (without group assignment)
            items.append(f'{item_type} {item_name} "{label}" <{icon}> {{ channel="{channel_id}" }}')

            # Track for group generation
            category_items[category].append(item_name)

        if not items:
            return ([], {})

        # Add header
        header = f'// {friendly_name} ({ieee})'
        item_lines = [header] + items + ['']

        return (item_lines, category_items)

    def generate_groups(self, all_categories: Set[str]) -> List[str]:
        """Generate Group items for all metric categories."""
        groups = []
        groups.append('// Zigbee Device Groups')
        groups.append('// Auto-generated groups by metric category')
        groups.append('')

        for category in sorted(all_categories):
            category_title = category.title().replace('_', '')
            group_name = f'gZigbee{category_title}'
            label = f'Zigbee {category_title}'
            icon = self.get_icon_for_category(category)

            groups.append(f'Group {group_name} "{label}" <{icon}>')

        groups.append('')
        return groups

    def generate_all(self, devices: List[Dict[str, Any]]):
        """Generate all things and items files."""
        print("\n🔧 Generating OpenHAB configuration...")

        things = []
        all_item_lines = []
        all_categories = set()

        for device in devices:
            friendly_name = device.get('friendly_name', 'unknown')

            # Generate Thing
            thing_result = self.generate_thing(device)
            if thing_result:
                thing_def, ieee = thing_result
                things.append(thing_def)
                print(f"  ✓ Thing: {friendly_name}")

            # Generate Items
            item_lines, category_items = self.generate_items_for_device(device)
            if item_lines:
                all_item_lines.extend(item_lines)
                all_categories.update(category_items.keys())
                print(f"  ✓ Items: {friendly_name} ({len(item_lines)-2} items)")

        # Write things file
        if things:
            self.things_file.parent.mkdir(parents=True, exist_ok=True)
            header = [
                '// Zigbee2MQTT Devices',
                '// Auto-generated by generate-zigbee-config.py',
                '// DO NOT EDIT MANUALLY - regenerate using the script',
                ''
            ]
            content = '\n'.join(header + things)
            self.things_file.write_text(content)
            print(f"\n✅ Generated: {self.things_file}")

        # Write items file
        if all_item_lines:
            self.items_file.parent.mkdir(parents=True, exist_ok=True)
            header = [
                '// Zigbee2MQTT Device Items',
                '// Auto-generated by generate-zigbee-config.py',
                '// DO NOT EDIT MANUALLY - regenerate using the script',
                ''
            ]

            # Generate groups first
            # TEMPORARILY COMMENTED OUT - Groups generation disabled
            # groups = self.generate_groups(all_categories)
            groups = []

            content = '\n'.join(header + groups + all_item_lines)
            self.items_file.write_text(content)
            print(f"✅ Generated: {self.items_file}")
            print(f"  {len(all_item_lines)} item lines (groups disabled)")


def main():
    parser = argparse.ArgumentParser(
        description='Generate OpenHAB configuration from Zigbee2MQTT devices'
    )
    parser.add_argument('--ssh', help='SSH host (e.g., user@host)')
    parser.add_argument('--mqtt-host', help='MQTT broker hostname')
    parser.add_argument('--output-dir', default='openhab-dev/conf',
                       help='Output directory for configuration files')

    args = parser.parse_args()

    if not args.ssh and not args.mqtt_host:
        parser.error('Either --ssh or --mqtt-host must be specified')

    generator = ZigbeeConfigGenerator(args.output_dir)

    try:
        if args.ssh:
            devices = generator.fetch_devices_via_ssh(args.ssh)
        else:
            devices = generator.fetch_devices_via_mqtt(args.mqtt_host)

        generator.generate_all(devices)

        print("\n✨ Configuration generated successfully!")
        print("\n📝 Next steps:")
        print("  1. Review generated files")
        print("  2. Restart OpenHAB: docker-compose restart openhab")
        print("  3. Check logs for any errors")

    except subprocess.CalledProcessError as e:
        print(f"❌ Error: {e}")
        print(f"   Output: {e.stderr}")
        return 1
    except Exception as e:
        print(f"❌ Error: {e}")
        return 1

    return 0


if __name__ == '__main__':
    exit(main())