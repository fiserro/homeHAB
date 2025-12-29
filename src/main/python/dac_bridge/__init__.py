#!/usr/bin/env python3
"""
HRV Bridge - Simple MQTT to PWM bridge for HRV power output control.

Receives PWM duty cycle values (0-100) from OpenHAB via MQTT and sets
them directly on GPIO pins. All calculation (source selection, calibration)
is done in OpenHAB/HrvCalculator.

Input: 0-100 (PWM duty cycle percentage from OpenHAB)
Output: PWM signal on GPIO pins

MQTT Topics:
  - {prefix}/pwm/gpio18  -> PWM value for GPIO 18 (0-100)
  - {prefix}/pwm/gpio19  -> PWM value for GPIO 19 (0-100)
  - {prefix}/gpio17      -> Digital output GPIO 17 (ON/OFF)
"""

import argparse
import logging
import signal
import sys

import paho.mqtt.client as mqtt

# Try to import lgpio for PWM
try:
    import lgpio
    LGPIO_AVAILABLE = True
except ImportError:
    LGPIO_AVAILABLE = False

# Default configuration
DEFAULT_MQTT_HOST = "localhost"
DEFAULT_MQTT_PORT = 1883
DEFAULT_TOPIC_PREFIX = "homehab/hrv"
DEFAULT_CLIENT_ID = "hrv-bridge"
DEFAULT_GPIO17 = 17  # Bypass valve (digital output)
DEFAULT_GPIO18 = 18  # PWM output
DEFAULT_GPIO19 = 19  # PWM output
DEFAULT_PWM_FREQ = 2000

# Logging setup
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
log = logging.getLogger("hrv-bridge")


class PwmOutput:
    """PWM output driver using lgpio."""

    def __init__(self, gpio17: int = DEFAULT_GPIO17, gpio18: int = DEFAULT_GPIO18,
                 gpio19: int = DEFAULT_GPIO19, freq: int = DEFAULT_PWM_FREQ):
        self.gpio17 = gpio17  # Bypass valve (digital)
        self.gpio18 = gpio18  # PWM
        self.gpio19 = gpio19  # PWM
        self.freq = freq
        self.handle = None

        if not LGPIO_AVAILABLE:
            raise RuntimeError("lgpio library not available")

        self.handle = lgpio.gpiochip_open(0)
        lgpio.gpio_claim_output(self.handle, self.gpio17)
        lgpio.gpio_claim_output(self.handle, self.gpio18)
        lgpio.gpio_claim_output(self.handle, self.gpio19)
        log.info(f"GPIO initialized: GPIO{self.gpio17} (bypass), GPIO{self.gpio18}, GPIO{self.gpio19} (PWM at {self.freq} Hz)")

    def set_pwm(self, gpio: int, duty: float):
        """Set PWM duty cycle for specified GPIO."""
        if self.handle is None:
            return
        duty = max(0, min(100, duty))
        pin = self.gpio18 if gpio == 18 else self.gpio19
        lgpio.tx_pwm(self.handle, pin, self.freq, duty)
        log.debug(f"GPIO{gpio} PWM set to {duty:.1f}%")

    def set_digital(self, gpio: int, value: bool):
        """Set digital output for specified GPIO (0 or 1)."""
        if self.handle is None:
            return
        pin = self.gpio17 if gpio == 17 else gpio
        lgpio.gpio_write(self.handle, pin, 1 if value else 0)
        log.debug(f"GPIO{gpio} set to {1 if value else 0}")

    def stop(self):
        """Stop PWM and cleanup."""
        if self.handle is not None:
            lgpio.gpio_write(self.handle, self.gpio17, 0)
            lgpio.tx_pwm(self.handle, self.gpio18, self.freq, 0)
            lgpio.tx_pwm(self.handle, self.gpio19, self.freq, 0)
            lgpio.gpiochip_close(self.handle)
            self.handle = None


class HrvBridge:
    """Simple MQTT to PWM bridge for HRV control."""

    def __init__(self, mqtt_host: str, mqtt_port: int, topic_prefix: str,
                 client_id: str, gpio17: int, gpio18: int, gpio19: int, pwm_freq: int):
        self.mqtt_host = mqtt_host
        self.mqtt_port = mqtt_port
        self.topic_prefix = topic_prefix.rstrip('/')

        # MQTT client
        self.client = mqtt.Client(client_id=client_id)
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self.client.on_message = self._on_message

        # Initialize output driver
        self.output = None
        try:
            self.output = PwmOutput(gpio17=gpio17, gpio18=gpio18, gpio19=gpio19, freq=pwm_freq)
        except Exception as e:
            log.warning(f"GPIO initialization failed: {e} - running in simulation mode")

        self.running = False

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            log.info(f"Connected to MQTT broker at {self.mqtt_host}:{self.mqtt_port}")

            # Subscribe to topics
            client.subscribe(f"{self.topic_prefix}/gpio17")
            client.subscribe(f"{self.topic_prefix}/pwm/gpio18")
            client.subscribe(f"{self.topic_prefix}/pwm/gpio19")
            log.info(f"Subscribed to topics: {self.topic_prefix}/gpio17, pwm/gpio18, pwm/gpio19")
        else:
            log.error(f"Connection failed with code {rc}")

    def _on_disconnect(self, client, userdata, rc):
        if rc != 0:
            log.warning(f"Unexpected disconnect (rc={rc}), will reconnect...")

    def _on_message(self, client, userdata, msg):
        try:
            payload = msg.payload.decode("utf-8").strip()
            topic = msg.topic

            # Handle GPIO17 digital output (ON/OFF)
            if topic == f"{self.topic_prefix}/gpio17":
                value = payload.upper() in ("ON", "1", "TRUE")
                log.info(f"GPIO17 set to {1 if value else 0}")
                if self.output:
                    self.output.set_digital(17, value)
                return

            # Parse PWM value for GPIO18/19
            payload = payload.replace(",", ".")
            value = float(payload)
            value = max(0, min(100, value))

            # Determine GPIO from topic
            if topic == f"{self.topic_prefix}/pwm/gpio18":
                gpio = 18
            elif topic == f"{self.topic_prefix}/pwm/gpio19":
                gpio = 19
            else:
                log.warning(f"Unknown topic: {topic}")
                return

            log.info(f"GPIO{gpio} PWM set to {value:.1f}%")

            if self.output:
                self.output.set_pwm(gpio, value)

        except ValueError as e:
            log.error(f"Invalid value: {msg.payload}")
        except Exception as e:
            log.exception(f"Error processing message: {e}")

    def start(self):
        """Start the bridge."""
        self.running = True

        # Initialize GPIOs to 0
        if self.output:
            self.output.set_digital(17, False)
            self.output.set_pwm(18, 0)
            self.output.set_pwm(19, 0)

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
        log.info("Shutting down - setting outputs to 0")

        if self.output:
            self.output.set_digital(17, False)
            self.output.set_pwm(18, 0)
            self.output.set_pwm(19, 0)
            self.output.stop()

        self.client.disconnect()
        log.info("Shutdown complete")


def main():
    parser = argparse.ArgumentParser(
        description="HRV Bridge - Simple MQTT to PWM bridge"
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
        "--gpio17",
        type=int,
        default=DEFAULT_GPIO17,
        help=f"GPIO pin 17 - digital output (default: {DEFAULT_GPIO17})"
    )
    parser.add_argument(
        "--gpio18",
        type=int,
        default=DEFAULT_GPIO18,
        help=f"GPIO pin 18 - PWM output (default: {DEFAULT_GPIO18})"
    )
    parser.add_argument(
        "--gpio19",
        type=int,
        default=DEFAULT_GPIO19,
        help=f"GPIO pin 19 - PWM output (default: {DEFAULT_GPIO19})"
    )
    parser.add_argument(
        "--pwm-freq",
        type=int,
        default=DEFAULT_PWM_FREQ,
        help=f"PWM frequency in Hz (default: {DEFAULT_PWM_FREQ})"
    )
    parser.add_argument(
        "--verbose", "-v",
        action="store_true",
        help="Enable verbose logging"
    )

    args = parser.parse_args()

    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    bridge = HrvBridge(
        mqtt_host=args.mqtt_host,
        mqtt_port=args.mqtt_port,
        topic_prefix=args.topic_prefix,
        client_id=args.client_id,
        gpio17=args.gpio17,
        gpio18=args.gpio18,
        gpio19=args.gpio19,
        pwm_freq=args.pwm_freq
    )

    def signal_handler(signum, frame):
        log.info(f"Received signal {signum}")
        bridge.stop()
        sys.exit(0)

    signal.signal(signal.SIGTERM, signal_handler)
    signal.signal(signal.SIGINT, signal_handler)

    bridge.start()


if __name__ == "__main__":
    main()
