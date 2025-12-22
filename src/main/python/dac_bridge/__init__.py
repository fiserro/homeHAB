#!/usr/bin/env python3
"""
HRV Bridge - MQTT to PWM/DAC bridge for HRV power output control.

Subscribes to MQTT topics and controls HRV output based on received values.
Supports three output channels:
  - power: Base power (50:50 balanced) - for single-motor or balanced setups
  - intake: Fresh air motor power (adjusted by intakeExhaustRatio)
  - exhaust: Stale air motor power (adjusted by intakeExhaustRatio)

Input: 0-100 (percentage from OpenHAB hrvOutputPower/Intake/Exhaust)
Output: PWM signal (default) or DAC voltage

Modes:
  - PWM mode (default): GPIO PWM → PWM-to-0-10V module → HRV
    - Uses separate GPIO pins for intake/exhaust
  - DAC mode: Waveshare DAC8532 → 0-5V output
    - Channel A: Intake motor
    - Channel B: Exhaust motor

Usage:
    hrv-bridge [--mqtt-host HOST] [--mode pwm|dac] [--pwm-pin-intake PIN] [--pwm-pin-exhaust PIN]
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
DEFAULT_PWM_PIN_INTAKE = 18  # GPIO 18 (hardware PWM capable) - intake motor
DEFAULT_PWM_PIN_EXHAUST = 19  # GPIO 19 (hardware PWM capable) - exhaust motor
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
    """PWM output driver using lgpio for dual-channel HRV control."""

    def __init__(self, pin_intake: int = DEFAULT_PWM_PIN_INTAKE,
                 pin_exhaust: int = DEFAULT_PWM_PIN_EXHAUST,
                 freq: int = DEFAULT_PWM_FREQ):
        self.pin_intake = pin_intake
        self.pin_exhaust = pin_exhaust
        self.freq = freq
        self.handle = None
        self.duty_intake = 0
        self.duty_exhaust = 0

        if not LGPIO_AVAILABLE:
            raise RuntimeError("lgpio library not available")

        self.handle = lgpio.gpiochip_open(0)
        lgpio.gpio_claim_output(self.handle, self.pin_intake)
        lgpio.gpio_claim_output(self.handle, self.pin_exhaust)
        log.info(f"PWM initialized: intake=GPIO{self.pin_intake}, exhaust=GPIO{self.pin_exhaust} at {self.freq} Hz")

    def set_intake(self, percent: float):
        """Set intake motor PWM duty cycle."""
        percent = max(0, min(100, percent))
        self.duty_intake = percent
        calibrated_duty = percent_to_pwm(percent)
        lgpio.tx_pwm(self.handle, self.pin_intake, self.freq, calibrated_duty)

    def set_exhaust(self, percent: float):
        """Set exhaust motor PWM duty cycle."""
        percent = max(0, min(100, percent))
        self.duty_exhaust = percent
        calibrated_duty = percent_to_pwm(percent)
        lgpio.tx_pwm(self.handle, self.pin_exhaust, self.freq, calibrated_duty)

    def set_both(self, percent: float):
        """Set both motors to same PWM duty cycle (for base power)."""
        self.set_intake(percent)
        self.set_exhaust(percent)

    def stop(self):
        """Stop PWM and cleanup."""
        if self.handle is not None:
            lgpio.tx_pwm(self.handle, self.pin_intake, self.freq, 0)
            lgpio.tx_pwm(self.handle, self.pin_exhaust, self.freq, 0)
            lgpio.gpiochip_close(self.handle)
            self.handle = None


class DacOutput:
    """DAC output driver using Waveshare DAC8532 for dual-channel HRV control.

    Channel A: Intake motor
    Channel B: Exhaust motor
    """

    def __init__(self):
        if not DAC_AVAILABLE:
            raise RuntimeError("Waveshare DAC8532 library not available")

        waveshare_config.module_init()
        self.dac = DAC8532.DAC8532()
        self.voltage_intake = 0.0
        self.voltage_exhaust = 0.0
        self.v_max = 5.0
        log.info("DAC8532 initialized: Channel A=intake, Channel B=exhaust")

    def set_intake(self, percent: float):
        """Set intake motor voltage (Channel A)."""
        percent = max(0, min(100, percent))
        self.voltage_intake = (percent / 100.0) * self.v_max
        self.dac.DAC8532_Out_Voltage(DAC8532.channel_A, self.voltage_intake)

    def set_exhaust(self, percent: float):
        """Set exhaust motor voltage (Channel B)."""
        percent = max(0, min(100, percent))
        self.voltage_exhaust = (percent / 100.0) * self.v_max
        self.dac.DAC8532_Out_Voltage(DAC8532.channel_B, self.voltage_exhaust)

    def set_both(self, percent: float):
        """Set both motors to same voltage (for base power)."""
        self.set_intake(percent)
        self.set_exhaust(percent)

    def stop(self):
        """Set outputs to 0 and cleanup."""
        self.dac.DAC8532_Out_Voltage(DAC8532.channel_A, 0.0)
        self.dac.DAC8532_Out_Voltage(DAC8532.channel_B, 0.0)
        try:
            waveshare_config.module_exit()
        except Exception:
            pass


class HrvBridge:
    """MQTT to PWM/DAC bridge for HRV control.

    Subscribes to three MQTT topics:
      - {prefix}/power/set: Base power (sets both motors to same level)
      - {prefix}/intake/set: Intake motor power (fresh air)
      - {prefix}/exhaust/set: Exhaust motor power (stale air)

    Use power/set for single-motor or balanced setups.
    Use intake/set and exhaust/set for independent motor control.
    """

    def __init__(self, mqtt_host: str, mqtt_port: int, topic_prefix: str,
                 client_id: str, mode: str, pwm_pin_intake: int,
                 pwm_pin_exhaust: int, pwm_freq: int):
        self.mqtt_host = mqtt_host
        self.mqtt_port = mqtt_port
        self.topic_prefix = topic_prefix.rstrip('/')

        # MQTT topics for all three outputs
        self.topic_power_set = f"{self.topic_prefix}/power/set"
        self.topic_power_state = f"{self.topic_prefix}/power/state"
        self.topic_intake_set = f"{self.topic_prefix}/intake/set"
        self.topic_intake_state = f"{self.topic_prefix}/intake/state"
        self.topic_exhaust_set = f"{self.topic_prefix}/exhaust/set"
        self.topic_exhaust_state = f"{self.topic_prefix}/exhaust/state"

        self.mode = mode

        self.client = mqtt.Client(client_id=client_id)
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self.client.on_message = self._on_message

        self.output = None
        self.running = False
        self.current_power = 0
        self.current_intake = 0
        self.current_exhaust = 0

        # Initialize output driver
        try:
            if mode == "pwm":
                self.output = PwmOutput(pin_intake=pwm_pin_intake,
                                        pin_exhaust=pwm_pin_exhaust,
                                        freq=pwm_freq)
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
            # Subscribe to all three control topics
            client.subscribe(self.topic_power_set)
            client.subscribe(self.topic_intake_set)
            client.subscribe(self.topic_exhaust_set)
            log.info(f"Subscribed to: {self.topic_power_set}, {self.topic_intake_set}, {self.topic_exhaust_set}")
            self._publish_all_states()
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

            topic = msg.topic
            if topic == self.topic_power_set:
                self._set_power(percent)
            elif topic == self.topic_intake_set:
                self._set_intake(percent)
            elif topic == self.topic_exhaust_set:
                self._set_exhaust(percent)
            else:
                log.warning(f"Unknown topic: {topic}")
        except ValueError as e:
            log.error(f"Invalid payload '{msg.payload}': {e}")
        except Exception as e:
            log.exception(f"Error processing message: {e}")

    def _set_power(self, percent: float):
        """Set base power level (both motors)."""
        percent = max(PERCENT_MIN, min(PERCENT_MAX, percent))

        if self.output:
            self.output.set_both(percent)

        self.current_power = percent
        self.current_intake = percent
        self.current_exhaust = percent
        log.info(f"Power set: {percent:.1f}% (both motors, mode: {self.mode})")
        self._publish_all_states()

    def _set_intake(self, percent: float):
        """Set intake motor power level."""
        percent = max(PERCENT_MIN, min(PERCENT_MAX, percent))

        if self.output:
            self.output.set_intake(percent)

        self.current_intake = percent
        log.info(f"Intake power set: {percent:.1f}% (mode: {self.mode})")
        self.client.publish(self.topic_intake_state, f"{self.current_intake:.1f}", retain=True)

    def _set_exhaust(self, percent: float):
        """Set exhaust motor power level."""
        percent = max(PERCENT_MIN, min(PERCENT_MAX, percent))

        if self.output:
            self.output.set_exhaust(percent)

        self.current_exhaust = percent
        log.info(f"Exhaust power set: {percent:.1f}% (mode: {self.mode})")
        self.client.publish(self.topic_exhaust_state, f"{self.current_exhaust:.1f}", retain=True)

    def _publish_all_states(self):
        """Publish all current states to MQTT."""
        self.client.publish(self.topic_power_state, f"{self.current_power:.1f}", retain=True)
        self.client.publish(self.topic_intake_state, f"{self.current_intake:.1f}", retain=True)
        self.client.publish(self.topic_exhaust_state, f"{self.current_exhaust:.1f}", retain=True)

    def start(self):
        """Start the bridge."""
        self.running = True
        self._set_power(0)  # Initialize both motors to 0

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
            self.output.set_both(0)
            self.output.stop()

        self.client.disconnect()
        log.info("Shutdown complete")


def main():
    parser = argparse.ArgumentParser(
        description="HRV Bridge - MQTT to PWM/DAC bridge for dual-channel HRV power output"
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
