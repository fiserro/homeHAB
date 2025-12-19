#!/usr/bin/env python3
"""
DAC Bridge - MQTT to DAC8532 bridge for HRV power output control.

Subscribes to MQTT topic and sets DAC output voltage based on received values.
Input: 0-100 (percentage from OpenHAB hrvOutputPower)
Output: 0-5V on DAC8532 channel A

Usage:
    dac-bridge [--mqtt-host HOST] [--mqtt-port PORT] [--topic-prefix PREFIX]
"""

import argparse
import logging
import signal
import sys
import time

# Import bundled Waveshare library
try:
    from . import waveshare_dac8532 as DAC8532
    from . import waveshare_config as config
    config.module_init()
    DAC_AVAILABLE = True
except ImportError as e:
    DAC_AVAILABLE = False
    logging.warning(f"DAC8532 library not available - running in simulation mode: {e}")

import paho.mqtt.client as mqtt

# Default configuration
DEFAULT_MQTT_HOST = "localhost"
DEFAULT_MQTT_PORT = 1883
DEFAULT_TOPIC_PREFIX = "homehab/dac"
DEFAULT_CLIENT_ID = "dac-bridge"

# DAC parameters
V_MIN = 0.0
V_MAX = 5.0
PERCENT_MIN = 0
PERCENT_MAX = 100

# Logging setup
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
log = logging.getLogger("dac-bridge")


class DacBridge:
    """MQTT to DAC8532 bridge."""

    def __init__(self, mqtt_host: str, mqtt_port: int, topic_prefix: str, client_id: str):
        self.mqtt_host = mqtt_host
        self.mqtt_port = mqtt_port
        self.topic_prefix = topic_prefix.rstrip('/')
        self.topic_set = f"{self.topic_prefix}/power/set"
        self.topic_state = f"{self.topic_prefix}/power/state"
        self.topic_voltage = f"{self.topic_prefix}/voltage/state"

        self.client = mqtt.Client(client_id=client_id)
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self.client.on_message = self._on_message

        self.dac = None
        self.running = False
        self.current_percent = 0
        self.current_voltage = 0.0

        if DAC_AVAILABLE:
            self.dac = DAC8532.DAC8532()
            log.info("DAC8532 initialized")
        else:
            log.warning("Running in simulation mode (no DAC hardware)")

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            log.info(f"Connected to MQTT broker at {self.mqtt_host}:{self.mqtt_port}")
            client.subscribe(self.topic_set)
            log.info(f"Subscribed to {self.topic_set}")
            # Publish initial state
            self._publish_state()
        else:
            log.error(f"Connection failed with code {rc}")

    def _on_disconnect(self, client, userdata, rc):
        if rc != 0:
            log.warning(f"Unexpected disconnect (rc={rc}), will reconnect...")

    def _on_message(self, client, userdata, msg):
        try:
            payload = msg.payload.decode("utf-8").strip()
            # Handle both comma and dot as decimal separator
            payload = payload.replace(",", ".")

            percent = float(payload)
            self._set_power(percent)

        except ValueError as e:
            log.error(f"Invalid payload '{msg.payload}': {e}")
        except Exception as e:
            log.exception(f"Error processing message: {e}")

    def _percent_to_voltage(self, percent: float) -> float:
        """Convert percentage (0-100) to voltage (0-5V)."""
        percent = max(PERCENT_MIN, min(PERCENT_MAX, percent))
        return (percent / PERCENT_MAX) * V_MAX

    def _set_power(self, percent: float):
        """Set DAC output based on percentage value."""
        percent = max(PERCENT_MIN, min(PERCENT_MAX, percent))
        voltage = self._percent_to_voltage(percent)

        if self.dac:
            self.dac.DAC8532_Out_Voltage(DAC8532.channel_A, voltage)

        self.current_percent = percent
        self.current_voltage = voltage

        log.info(f"Power set: {percent:.1f}% -> {voltage:.3f}V")
        self._publish_state()

    def _publish_state(self):
        """Publish current state to MQTT."""
        self.client.publish(self.topic_state, f"{self.current_percent:.1f}", retain=True)
        self.client.publish(self.topic_voltage, f"{self.current_voltage:.3f}", retain=True)

    def start(self):
        """Start the bridge."""
        self.running = True

        # Set initial output to 0
        self._set_power(0)

        log.info(f"Connecting to MQTT broker at {self.mqtt_host}:{self.mqtt_port}")
        self.client.connect(self.mqtt_host, self.mqtt_port, keepalive=60)

        try:
            self.client.loop_forever()
        except KeyboardInterrupt:
            log.info("Interrupted by user")
        finally:
            self.stop()

    def stop(self):
        """Stop the bridge and cleanup."""
        self.running = False

        # Set output to 0 on shutdown
        log.info("Shutting down - setting output to 0V")
        if self.dac:
            self.dac.DAC8532_Out_Voltage(DAC8532.channel_A, 0.0)

        self.client.disconnect()

        # Cleanup Waveshare library
        if DAC_AVAILABLE:
            try:
                config.module_exit()
            except Exception:
                pass

        log.info("Shutdown complete")


def main():
    parser = argparse.ArgumentParser(
        description="DAC Bridge - MQTT to DAC8532 bridge for HRV power output"
    )
    parser.add_argument(
        "--mqtt-host", "-H",
        default=DEFAULT_MQTT_HOST,
        help=f"MQTT broker host (default: {DEFAULT_MQTT_HOST})"
    )
    parser.add_argument(
        "--mqtt-port", "-p",
        type=int,
        default=DEFAULT_MQTT_PORT,
        help=f"MQTT broker port (default: {DEFAULT_MQTT_PORT})"
    )
    parser.add_argument(
        "--topic-prefix", "-t",
        default=DEFAULT_TOPIC_PREFIX,
        help=f"MQTT topic prefix (default: {DEFAULT_TOPIC_PREFIX})"
    )
    parser.add_argument(
        "--client-id", "-c",
        default=DEFAULT_CLIENT_ID,
        help=f"MQTT client ID (default: {DEFAULT_CLIENT_ID})"
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Enable verbose logging"
    )

    args = parser.parse_args()

    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    bridge = DacBridge(
        mqtt_host=args.mqtt_host,
        mqtt_port=args.mqtt_port,
        topic_prefix=args.topic_prefix,
        client_id=args.client_id
    )

    # Handle signals
    def signal_handler(signum, frame):
        log.info(f"Received signal {signum}")
        bridge.stop()
        sys.exit(0)

    signal.signal(signal.SIGTERM, signal_handler)
    signal.signal(signal.SIGINT, signal_handler)

    bridge.start()


if __name__ == "__main__":
    main()
