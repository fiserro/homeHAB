#!/usr/bin/env python3
"""
HRV Bridge - MQTT to PWM/DAC bridge for HRV power output control.

Subscribes to MQTT topic and controls HRV output based on received values.
Input: 0-100 (percentage from OpenHAB hrvOutputPower)
Output: PWM signal (default) or DAC voltage

Modes:
  - PWM mode (default): GPIO PWM → PWM-to-0-10V module → HRV
  - DAC mode: Waveshare DAC8532 → 0-5V output

Usage:
    hrv-bridge [--mqtt-host HOST] [--mode pwm|dac] [--pwm-pin PIN]
"""

import argparse
import logging
import signal
import sys
import time

import paho.mqtt.client as mqtt

# Try to import lgpio for PWM
try:
    import lgpio
    LGPIO_AVAILABLE = True
except ImportError:
    LGPIO_AVAILABLE = False

# Try to import Waveshare library for DAC
try:
    from . import waveshare_dac8532 as DAC8532
    from . import waveshare_config as waveshare_config
    DAC_AVAILABLE = True
except ImportError:
    DAC_AVAILABLE = False

# Import PWM calibration
from .pwm_calibration import percent_to_pwm

# Default configuration
DEFAULT_MQTT_HOST = "localhost"
DEFAULT_MQTT_PORT = 1883
DEFAULT_TOPIC_PREFIX = "homehab/hrv"
DEFAULT_CLIENT_ID = "hrv-bridge"
DEFAULT_MODE = "pwm"
DEFAULT_PWM_PIN = 18  # GPIO 18 (hardware PWM capable)
DEFAULT_PWM_FREQ = 2000  # 2 kHz (PWM module accepts 1-3 kHz)


# Output parameters
PERCENT_MIN = 0
PERCENT_MAX = 100

# Logging setup
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
log = logging.getLogger("hrv-bridge")


class PwmOutput:
    """PWM output driver using lgpio."""

    def __init__(self, pin: int = DEFAULT_PWM_PIN, freq: int = DEFAULT_PWM_FREQ):
        self.pin = pin
        self.freq = freq
        self.handle = None
        self.duty_cycle = 0

        if not LGPIO_AVAILABLE:
            raise RuntimeError("lgpio library not available")

        self.handle = lgpio.gpiochip_open(0)
        lgpio.gpio_claim_output(self.handle, self.pin)
        log.info(f"PWM initialized on GPIO {self.pin} at {self.freq} Hz")

    def set_duty_cycle(self, percent: float):
        """Set PWM duty cycle using calibration table."""
        percent = max(0, min(100, percent))
        self.duty_cycle = percent
        # Use calibration to find actual PWM duty for desired output
        calibrated_duty = percent_to_pwm(percent)
        lgpio.tx_pwm(self.handle, self.pin, self.freq, calibrated_duty)

    def stop(self):
        """Stop PWM and cleanup."""
        if self.handle is not None:
            lgpio.tx_pwm(self.handle, self.pin, self.freq, 0)
            lgpio.gpiochip_close(self.handle)
            self.handle = None


class DacOutput:
    """DAC output driver using Waveshare DAC8532."""

    def __init__(self):
        if not DAC_AVAILABLE:
            raise RuntimeError("Waveshare DAC8532 library not available")

        waveshare_config.module_init()
        self.dac = DAC8532.DAC8532()
        self.voltage = 0.0
        self.v_max = 5.0
        log.info("DAC8532 initialized")

    def set_duty_cycle(self, percent: float):
        """Set output voltage based on percentage (0-100% → 0-5V)."""
        percent = max(0, min(100, percent))
        self.voltage = (percent / 100.0) * self.v_max
        self.dac.DAC8532_Out_Voltage(DAC8532.channel_A, self.voltage)

    def stop(self):
        """Set output to 0 and cleanup."""
        self.dac.DAC8532_Out_Voltage(DAC8532.channel_A, 0.0)
        try:
            waveshare_config.module_exit()
        except Exception:
            pass


class HrvBridge:
    """MQTT to PWM/DAC bridge for HRV control."""

    def __init__(self, mqtt_host: str, mqtt_port: int, topic_prefix: str,
                 client_id: str, mode: str, pwm_pin: int, pwm_freq: int):
        self.mqtt_host = mqtt_host
        self.mqtt_port = mqtt_port
        self.topic_prefix = topic_prefix.rstrip('/')
        self.topic_set = f"{self.topic_prefix}/power/set"
        self.topic_state = f"{self.topic_prefix}/power/state"
        self.mode = mode

        self.client = mqtt.Client(client_id=client_id)
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self.client.on_message = self._on_message

        self.output = None
        self.running = False
        self.current_percent = 0

        # Initialize output driver
        try:
            if mode == "pwm":
                self.output = PwmOutput(pin=pwm_pin, freq=pwm_freq)
            elif mode == "dac":
                self.output = DacOutput()
            else:
                raise ValueError(f"Unknown mode: {mode}")
        except Exception as e:
            log.warning(f"Output initialization failed: {e} - running in simulation mode")
            self.output = None

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            log.info(f"Connected to MQTT broker at {self.mqtt_host}:{self.mqtt_port}")
            client.subscribe(self.topic_set)
            log.info(f"Subscribed to {self.topic_set}")
            self._publish_state()
        else:
            log.error(f"Connection failed with code {rc}")

    def _on_disconnect(self, client, userdata, rc):
        if rc != 0:
            log.warning(f"Unexpected disconnect (rc={rc}), will reconnect...")

    def _on_message(self, client, userdata, msg):
        try:
            payload = msg.payload.decode("utf-8").strip()
            payload = payload.replace(",", ".")
            percent = float(payload)
            self._set_power(percent)
        except ValueError as e:
            log.error(f"Invalid payload '{msg.payload}': {e}")
        except Exception as e:
            log.exception(f"Error processing message: {e}")

    def _set_power(self, percent: float):
        """Set output power level."""
        percent = max(PERCENT_MIN, min(PERCENT_MAX, percent))

        if self.output:
            self.output.set_duty_cycle(percent)

        self.current_percent = percent
        log.info(f"Power set: {percent:.1f}% (mode: {self.mode})")
        self._publish_state()

    def _publish_state(self):
        """Publish current state to MQTT."""
        self.client.publish(self.topic_state, f"{self.current_percent:.1f}", retain=True)

    def start(self):
        """Start the bridge."""
        self.running = True
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
        log.info("Shutting down - setting output to 0")

        if self.output:
            self.output.set_duty_cycle(0)
            self.output.stop()

        self.client.disconnect()
        log.info("Shutdown complete")


def main():
    parser = argparse.ArgumentParser(
        description="HRV Bridge - MQTT to PWM/DAC bridge for HRV power output"
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
        "--mode", "-m",
        choices=["pwm", "dac"],
        default=DEFAULT_MODE,
        help=f"Output mode: pwm or dac (default: {DEFAULT_MODE})"
    )
    parser.add_argument(
        "--pwm-pin",
        type=int,
        default=DEFAULT_PWM_PIN,
        help=f"GPIO pin for PWM output (default: {DEFAULT_PWM_PIN})"
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
        mode=args.mode,
        pwm_pin=args.pwm_pin,
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
