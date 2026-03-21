#!/usr/bin/env python3
"""
MQTT Device Simulator for homeHAB Development Environment

Simulates Zigbee devices by publishing MQTT messages in Zigbee2MQTT format.
Reads device configuration from devices.yaml.
"""

import json
import os
import random
import signal
import sys
import threading
import time
from dataclasses import dataclass
from typing import Any

import paho.mqtt.client as mqtt
import yaml


@dataclass
class ValueConfig:
    """Configuration for a simulated value."""
    name: str
    value_type: str  # 'numeric', 'boolean', or 'enum'
    min_val: float = 0
    max_val: float = 100
    precision: int = 0
    probability_true: float = 0.5
    enum_values: list[str] = None
    enum_probabilities: list[float] = None
    current_value: Any = None

    def generate(self) -> Any:
        """Generate a new value based on configuration."""
        if self.value_type == 'boolean':
            return random.random() < self.probability_true
        elif self.value_type == 'enum':
            if self.enum_probabilities:
                return random.choices(self.enum_values, weights=self.enum_probabilities)[0]
            else:
                return random.choice(self.enum_values)
        else:
            # For numeric values, simulate gradual changes
            if self.current_value is None:
                # Initialize with random value in range
                value = random.uniform(self.min_val, self.max_val)
            else:
                # Random walk with bounds
                range_size = self.max_val - self.min_val
                max_change = range_size * 0.1  # Max 10% change
                change = random.uniform(-max_change, max_change)
                value = self.current_value + change
                value = max(self.min_val, min(self.max_val, value))

            self.current_value = value

            if self.precision == 0:
                return int(round(value))
            else:
                return round(value, self.precision)


@dataclass
class Device:
    """Configuration for a simulated device."""
    name: str
    device_type: str
    interval: int
    values: list[ValueConfig]
    last_publish: float = 0


class MqttSimulator:
    """MQTT Device Simulator."""

    def __init__(self, config_path: str = '/app/devices.yaml'):
        self.config_path = config_path
        self.mqtt_host = os.environ.get('MQTT_HOST', 'localhost')
        self.mqtt_port = int(os.environ.get('MQTT_PORT', '1883'))
        self.topic_prefix = os.environ.get('MQTT_TOPIC_PREFIX', 'zigbee2mqtt')

        self.devices: list[Device] = []
        self.client: mqtt.Client = None
        self.running = False
        self._lock = threading.Lock()

    def load_config(self) -> None:
        """Load device configuration from YAML file."""
        print(f"Loading configuration from {self.config_path}")

        with open(self.config_path, 'r') as f:
            config = yaml.safe_load(f)

        self.devices = []
        for device_cfg in config.get('devices', []):
            values = []
            for name, val_cfg in device_cfg.get('values', {}).items():
                if isinstance(val_cfg, dict):
                    val_type = val_cfg.get('type', 'numeric')
                    if val_type == 'boolean':
                        values.append(ValueConfig(
                            name=name,
                            value_type='boolean',
                            probability_true=val_cfg.get('probability_true', 0.5)
                        ))
                    elif val_type == 'enum':
                        values.append(ValueConfig(
                            name=name,
                            value_type='enum',
                            enum_values=val_cfg.get('values', []),
                            enum_probabilities=val_cfg.get('probabilities')
                        ))
                    else:
                        values.append(ValueConfig(
                            name=name,
                            value_type='numeric',
                            min_val=val_cfg.get('min', 0),
                            max_val=val_cfg.get('max', 100),
                            precision=val_cfg.get('precision', 0)
                        ))

            device = Device(
                name=device_cfg['name'],
                device_type=device_cfg.get('type', 'generic'),
                interval=device_cfg.get('interval', 30),
                values=values
            )
            self.devices.append(device)
            print(f"  Loaded device: {device.name} ({device.device_type})")

        print(f"Loaded {len(self.devices)} devices")

    def connect(self) -> None:
        """Connect to MQTT broker."""
        print(f"Connecting to MQTT broker at {self.mqtt_host}:{self.mqtt_port}")

        self.client = mqtt.Client(
            client_id=f"mqtt-simulator-{random.randint(1000, 9999)}",
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2
        )

        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect

        # Retry connection
        max_retries = 30
        for attempt in range(max_retries):
            try:
                self.client.connect(self.mqtt_host, self.mqtt_port, 60)
                break
            except Exception as e:
                print(f"Connection attempt {attempt + 1}/{max_retries} failed: {e}")
                if attempt < max_retries - 1:
                    time.sleep(2)
                else:
                    raise

    def _on_connect(self, client, userdata, flags, reason_code, properties) -> None:
        """Handle MQTT connection."""
        if reason_code == 0:
            print("Connected to MQTT broker")
        else:
            print(f"Connection failed with code: {reason_code}")

    def _on_disconnect(self, client, userdata, flags, reason_code, properties) -> None:
        """Handle MQTT disconnection."""
        print(f"Disconnected from MQTT broker: {reason_code}")

    def publish_device(self, device: Device) -> None:
        """Publish simulated data for a device."""
        payload = {}

        for value_cfg in device.values:
            payload[value_cfg.name] = value_cfg.generate()

        topic = f"{self.topic_prefix}/{device.name}"
        message = json.dumps(payload)

        self.client.publish(topic, message, qos=0, retain=False)
        print(f"Published to {topic}: {message}")

    def run(self) -> None:
        """Main simulation loop."""
        self.running = True
        self.client.loop_start()

        print("Starting simulation loop...")

        while self.running:
            current_time = time.time()

            for device in self.devices:
                if current_time - device.last_publish >= device.interval:
                    self.publish_device(device)
                    device.last_publish = current_time

            # Sleep for a short interval
            time.sleep(1)

        self.client.loop_stop()
        self.client.disconnect()
        print("Simulation stopped")

    def stop(self) -> None:
        """Stop the simulator."""
        self.running = False


def main():
    simulator = MqttSimulator()

    def signal_handler(signum, frame):
        print("\nReceived shutdown signal")
        simulator.stop()

    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    try:
        simulator.load_config()
        simulator.connect()
        simulator.run()
    except Exception as e:
        print(f"Error: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()
