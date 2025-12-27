#!/usr/bin/env python3
"""
HRV Bridge - MQTT to PWM/DAC bridge for HRV power output control.

New architecture with configurable GPIO routing:
- Four source values: power, intake, exhaust, test
- Each GPIO (18, 19) can be configured to use any source or "off" to disable
- Calibration tables per GPIO for PWM-to-voltage mapping

Input: 0-100 (percentage from OpenHAB)
Output: PWM signal (default) or DAC voltage

MQTT Topics:
  Source values (OpenHAB → Bridge):
    - {prefix}/power/set    -> Base power value
    - {prefix}/intake/set   -> Intake power value
    - {prefix}/exhaust/set  -> Exhaust power value
    - {prefix}/test/set     -> Test power value (for calibration)

  GPIO configuration (bidirectional, retained):
    - {prefix}/gpio18/source  -> Source: "power"|"intake"|"exhaust"|"test"|"off"
    - {prefix}/gpio19/source  -> Source: "power"|"intake"|"exhaust"|"test"|"off"

  Calibration tables (bidirectional, retained):
    - {prefix}/calibration/gpio18/table -> JSON calibration table
    - {prefix}/calibration/gpio19/table -> JSON calibration table
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

# Try to import Waveshare library for DAC
try:
    from . import waveshare_dac8532 as DAC8532
    from . import waveshare_config as waveshare_config
    DAC_AVAILABLE = True
except ImportError:
    DAC_AVAILABLE = False

# Import PWM calibration
from .pwm_calibration import CalibrationManager

# Default configuration
DEFAULT_MQTT_HOST = "localhost"
DEFAULT_MQTT_PORT = 1883
DEFAULT_TOPIC_PREFIX = "homehab/hrv"
DEFAULT_CLIENT_ID = "hrv-bridge"
DEFAULT_MODE = "pwm"
DEFAULT_PWM_PIN_INTAKE = 18
DEFAULT_PWM_PIN_EXHAUST = 19
DEFAULT_PWM_FREQ = 2000

# Output parameters
PERCENT_MIN = 0
PERCENT_MAX = 100

# Valid source names (including "off" to disable GPIO)
VALID_SOURCES = ("power", "intake", "exhaust", "test", "off")

# Logging setup
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
log = logging.getLogger("hrv-bridge")


class PwmOutput:
    """PWM output driver using lgpio for dual-channel HRV control."""

    def __init__(self, calibration_manager: CalibrationManager,
                 pin_intake: int = DEFAULT_PWM_PIN_INTAKE,
                 pin_exhaust: int = DEFAULT_PWM_PIN_EXHAUST,
                 freq: int = DEFAULT_PWM_FREQ):
        self.calibration = calibration_manager
        self.pin_intake = pin_intake
        self.pin_exhaust = pin_exhaust
        self.freq = freq
        self.handle = None

        if not LGPIO_AVAILABLE:
            raise RuntimeError("lgpio library not available")

        self.handle = lgpio.gpiochip_open(0)
        lgpio.gpio_claim_output(self.handle, self.pin_intake)
        lgpio.gpio_claim_output(self.handle, self.pin_exhaust)
        log.info(f"PWM initialized: GPIO{self.pin_intake}, GPIO{self.pin_exhaust} at {self.freq} Hz")

    def set_gpio(self, gpio: int, percent: float):
        """Set PWM duty cycle for specified GPIO with calibration."""
        if self.handle is None:
            return
        percent = max(0, min(100, percent))
        calibrated_duty = self.calibration.get_pwm_for_percent(gpio, percent)
        pin = self.pin_intake if gpio == 18 else self.pin_exhaust
        lgpio.tx_pwm(self.handle, pin, self.freq, calibrated_duty)

    def stop(self):
        """Stop PWM and cleanup."""
        if self.handle is not None:
            lgpio.tx_pwm(self.handle, self.pin_intake, self.freq, 0)
            lgpio.tx_pwm(self.handle, self.pin_exhaust, self.freq, 0)
            lgpio.gpiochip_close(self.handle)
            self.handle = None


class DacOutput:
    """DAC output driver using Waveshare DAC8532."""

    def __init__(self):
        if not DAC_AVAILABLE:
            raise RuntimeError("Waveshare DAC8532 library not available")

        waveshare_config.module_init()
        self.dac = DAC8532.DAC8532()
        self.v_max = 5.0
        log.info("DAC8532 initialized")

    def set_gpio(self, gpio: int, percent: float):
        """Set DAC output for specified GPIO."""
        percent = max(0, min(100, percent))
        voltage = (percent / 100.0) * self.v_max
        channel = DAC8532.channel_A if gpio == 18 else DAC8532.channel_B
        self.dac.DAC8532_Out_Voltage(channel, voltage)

    def stop(self):
        """Set outputs to 0 and cleanup."""
        self.dac.DAC8532_Out_Voltage(DAC8532.channel_A, 0.0)
        self.dac.DAC8532_Out_Voltage(DAC8532.channel_B, 0.0)
        try:
            waveshare_config.module_exit()
        except Exception:
            pass


class GpioConfig:
    """Configuration for a single GPIO."""

    def __init__(self, gpio: int, source: str = "power"):
        self.gpio = gpio
        self.source = source if source in VALID_SOURCES else "power"


class HrvBridge:
    """MQTT to PWM/DAC bridge for HRV control with configurable GPIO routing."""

    def __init__(self, mqtt_host: str, mqtt_port: int, topic_prefix: str,
                 client_id: str, mode: str, pwm_pin_intake: int,
                 pwm_pin_exhaust: int, pwm_freq: int):
        self.mqtt_host = mqtt_host
        self.mqtt_port = mqtt_port
        self.topic_prefix = topic_prefix.rstrip('/')
        self.mode = mode

        # Current source values
        self.sources = {
            "power": 0.0,
            "intake": 0.0,
            "exhaust": 0.0,
            "test": 0.0,
        }

        # GPIO configuration (default: GPIO18=intake, GPIO19=exhaust)
        self.gpio_config = {
            18: GpioConfig(18, source="intake"),
            19: GpioConfig(19, source="exhaust"),
        }

        # MQTT client
        self.client = mqtt.Client(client_id=client_id)
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self.client.on_message = self._on_message

        # Initialize calibration manager
        self.calibration_manager = CalibrationManager()

        # Initialize output driver
        self.output = None
        try:
            if mode == "pwm":
                self.output = PwmOutput(
                    calibration_manager=self.calibration_manager,
                    pin_intake=pwm_pin_intake,
                    pin_exhaust=pwm_pin_exhaust,
                    freq=pwm_freq
                )
            elif mode == "dac":
                self.output = DacOutput()
            else:
                raise ValueError(f"Unknown mode: {mode}")
        except Exception as e:
            log.warning(f"Output initialization failed: {e} - running in simulation mode")

        self.running = False

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            log.info(f"Connected to MQTT broker at {self.mqtt_host}:{self.mqtt_port}")

            # Subscribe to source value topics
            for source in VALID_SOURCES:
                topic = f"{self.topic_prefix}/{source}/set"
                client.subscribe(topic)
            log.info(f"Subscribed to source topics: power, intake, exhaust, test")

            # Subscribe to GPIO config topics (source only, "off" disables GPIO)
            for gpio in (18, 19):
                client.subscribe(f"{self.topic_prefix}/gpio{gpio}/source")
            log.info(f"Subscribed to GPIO config topics")

            # Subscribe to calibration table topics
            client.subscribe(f"{self.topic_prefix}/calibration/gpio18/table")
            client.subscribe(f"{self.topic_prefix}/calibration/gpio19/table")
            log.info(f"Subscribed to calibration topics")

        else:
            log.error(f"Connection failed with code {rc}")

    def _on_disconnect(self, client, userdata, rc):
        if rc != 0:
            log.warning(f"Unexpected disconnect (rc={rc}), will reconnect...")

    def _on_message(self, client, userdata, msg):
        try:
            payload = msg.payload.decode("utf-8").strip()
            topic = msg.topic

            # Calibration table updates
            if topic == f"{self.topic_prefix}/calibration/gpio18/table":
                self.calibration_manager.update_from_mqtt(18, payload)
                self._update_gpio(18)  # Re-apply with new calibration
                return
            elif topic == f"{self.topic_prefix}/calibration/gpio19/table":
                self.calibration_manager.update_from_mqtt(19, payload)
                self._update_gpio(19)  # Re-apply with new calibration
                return

            # GPIO config: source (including "off" to disable)
            if topic == f"{self.topic_prefix}/gpio18/source":
                self._handle_gpio_source(18, payload)
                return
            elif topic == f"{self.topic_prefix}/gpio19/source":
                self._handle_gpio_source(19, payload)
                return

            # Source value updates
            for source in VALID_SOURCES:
                if topic == f"{self.topic_prefix}/{source}/set":
                    self._handle_source_value(source, payload)
                    return

            log.warning(f"Unknown topic: {topic}")

        except Exception as e:
            log.exception(f"Error processing message: {e}")

    def _handle_gpio_source(self, gpio: int, payload: str):
        """Handle GPIO source config change."""
        source = payload.lower()
        if source not in VALID_SOURCES:
            log.warning(f"Invalid source '{source}' for GPIO{gpio}, ignoring")
            return

        old_source = self.gpio_config[gpio].source
        self.gpio_config[gpio].source = source

        if source != old_source:
            log.info(f"GPIO{gpio} source changed: {old_source} -> {source}")
            self._update_gpio(gpio)

    def _handle_source_value(self, source: str, payload: str):
        """Handle source value update."""
        try:
            payload = payload.replace(",", ".")
            value = float(payload)
            value = max(PERCENT_MIN, min(PERCENT_MAX, value))

            old_value = self.sources[source]
            self.sources[source] = value

            if value != old_value:
                log.info(f"Source '{source}' set to {value:.1f}%")
                # Update any GPIO using this source
                self._update_gpios_using_source(source)

        except ValueError as e:
            log.error(f"Invalid value for source '{source}': {payload}")

    def _update_gpios_using_source(self, source: str):
        """Update all GPIOs that are using the specified source."""
        for gpio in (18, 19):
            if self.gpio_config[gpio].source == source:
                self._update_gpio(gpio)

    def _update_gpio(self, gpio: int):
        """Update a single GPIO based on its config and source value."""
        config = self.gpio_config[gpio]

        # "off" source disables the GPIO (sets to 0)
        if config.source == "off":
            if self.output:
                self.output.set_gpio(gpio, 0)
                log.debug(f"GPIO{gpio} disabled (source: off)")
            return

        value = self.sources.get(config.source, 0)

        if self.output:
            self.output.set_gpio(gpio, value)
            log.debug(f"GPIO{gpio} set to {value:.1f}% (source: {config.source})")

    def start(self):
        """Start the bridge."""
        self.running = True

        # Initialize GPIOs to 0
        if self.output:
            self.output.set_gpio(18, 0)
            self.output.set_gpio(19, 0)

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
            self.output.set_gpio(18, 0)
            self.output.set_gpio(19, 0)
            self.output.stop()

        self.client.disconnect()
        log.info("Shutdown complete")


def main():
    parser = argparse.ArgumentParser(
        description="HRV Bridge - MQTT to PWM/DAC bridge with configurable GPIO routing"
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
        "--pwm-pin-intake",
        type=int,
        default=DEFAULT_PWM_PIN_INTAKE,
        help=f"GPIO pin for intake motor PWM (default: {DEFAULT_PWM_PIN_INTAKE})"
    )
    parser.add_argument(
        "--pwm-pin-exhaust",
        type=int,
        default=DEFAULT_PWM_PIN_EXHAUST,
        help=f"GPIO pin for exhaust motor PWM (default: {DEFAULT_PWM_PIN_EXHAUST})"
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
        pwm_pin_intake=args.pwm_pin_intake,
        pwm_pin_exhaust=args.pwm_pin_exhaust,
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
