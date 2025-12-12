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
from typing import List, Dict, Any, Optional


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

    def safe_name(self, name: str) -> str:
        """Convert friendly name to safe identifier."""
        # Remove special characters, replace spaces with underscores
        safe = re.sub(r'[^a-zA-Z0-9_]', '_', name)
        safe = re.sub(r'_+', '_', safe)  # Remove duplicate underscores
        return safe.strip('_')

    def map_expose_to_channel(self, expose: Dict[str, Any], device_topic: str) -> Optional[Dict[str, str]]:
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
                return self.map_expose_to_channel(features[0], device_topic)
            channel_type = 'switch'
        else:
            return None

        # Build channel configuration
        channel = {
            'type': channel_type,
            'name': exp_name,
            'label': expose.get('label', exp_name.title()),
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

    def generate_thing(self, device: Dict[str, Any]) -> Optional[str]:
        """Generate OpenHAB Thing definition for a device."""
        # Skip coordinator
        if device.get('type') == 'Coordinator':
            return None

        friendly_name = device.get('friendly_name', device.get('ieee_address', 'unknown'))
        safe_id = self.safe_name(friendly_name)
        ieee = device.get('ieee_address', '')
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
            channel = self.map_expose_to_channel(expose, topic)
            if channel:
                channels.append(channel)

        if not channels:
            print(f"  ⚠️  No channels found for {friendly_name}")
            return None

        # Build Thing definition
        thing_lines = [
            f'// {vendor} {model} - {description}',
            f'// IEEE: {ieee}',
            f'Thing mqtt:topic:zigbee:{safe_id} "{thing_label}" (mqtt:broker:zigbee) {{',
            '    Channels:'
        ]

        for ch in channels:
            ch_type = ch['type']
            ch_name = ch['name']
            ch_label = ch['label']
            state_topic = ch['stateTopic']
            transform = ch['transformationPattern']

            config_parts = [f'stateTopic="{state_topic}"', f'transformationPattern="{transform}"']

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

        return '\n'.join(thing_lines)

    def generate_item(self, device: Dict[str, Any]) -> Optional[str]:
        """Generate OpenHAB Item definitions for a device."""
        if device.get('type') == 'Coordinator':
            return None

        friendly_name = device.get('friendly_name', device.get('ieee_address', 'unknown'))
        safe_id = self.safe_name(friendly_name)
        safe_prefix = safe_id.title().replace('_', '')

        definition = device.get('definition', {})
        exposes = definition.get('exposes', [])

        items = []

        for expose in exposes:
            exp_type = expose.get('type')
            exp_name = expose.get('name') or expose.get('property', '')

            if not exp_name:
                continue

            # Determine item type and format
            if exp_type == 'binary':
                item_type = 'Switch' if expose.get('access', 0) & 2 else 'Contact'
                state_format = ''
            elif exp_type == 'numeric':
                item_type = 'Number'
                unit = expose.get('unit', '')
                if unit == '°C':
                    state_format = ' "Temperature [%.1f °C]"'
                elif unit == '%':
                    state_format = ' "Humidity [%.0f %%]"'
                elif unit == 'hPa':
                    state_format = ' "Pressure [%.0f hPa]"'
                elif unit == 'lqi':
                    state_format = ' "Link Quality [%.0f]"'
                else:
                    state_format = f' "[%.1f {unit}]"' if unit else ' "[%.1f]"'
            elif exp_type == 'enum':
                item_type = 'String'
                state_format = ' "[%s]"'
            elif exp_type == 'switch':
                item_type = 'Switch'
                state_format = ''
            else:
                continue

            label = expose.get('label', exp_name.title())
            item_name = f'{safe_prefix}_{exp_name.title()}'
            channel_id = f'mqtt:topic:zigbee:{safe_id}:{exp_name}'

            items.append(f'{item_type} {item_name}{state_format} {{ channel="{channel_id}" }}')

        if not items:
            return None

        header = f'// {friendly_name}'
        return '\n'.join([header] + items + [''])

    def generate_all(self, devices: List[Dict[str, Any]]):
        """Generate all things and items files."""
        print("\n🔧 Generating OpenHAB configuration...")

        things = []
        items = []

        for device in devices:
            friendly_name = device.get('friendly_name', 'unknown')

            thing = self.generate_thing(device)
            if thing:
                things.append(thing)
                print(f"  ✓ Thing: {friendly_name}")

            item = self.generate_item(device)
            if item:
                items.append(item)
                print(f"  ✓ Items: {friendly_name}")

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
        if items:
            self.items_file.parent.mkdir(parents=True, exist_ok=True)
            header = [
                '// Zigbee2MQTT Device Items',
                '// Auto-generated by generate-zigbee-config.py',
                '// DO NOT EDIT MANUALLY - regenerate using the script',
                ''
            ]
            content = '\n'.join(header + items)
            self.items_file.write_text(content)
            print(f"✅ Generated: {self.items_file}")


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
