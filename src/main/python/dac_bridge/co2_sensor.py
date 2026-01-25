# -*- coding: utf-8 -*-
"""
MH-Z19C CO2 Sensor Module

Reads CO2 concentration from MH-Z19C sensor via UART.

MH-Z19C specifications:
- Measurement range: 400-5000 ppm (or 400-10000 ppm variant)
- Accuracy: ±(50 ppm + 5% reading)
- Response time: < 120s
- Warm-up time: 3 minutes
- Interface: UART 9600 baud, 8N1
"""

import logging
import time

log = logging.getLogger("co2-sensor")

# UART settings
BAUDRATE = 9600
TIMEOUT = 2

# MH-Z19C commands
CMD_READ_CO2 = bytes([0xFF, 0x01, 0x86, 0x00, 0x00, 0x00, 0x00, 0x00, 0x79])
CMD_CALIBRATE_ZERO = bytes([0xFF, 0x01, 0x87, 0x00, 0x00, 0x00, 0x00, 0x00, 0x78])
CMD_AUTO_CALIB_ON = bytes([0xFF, 0x01, 0x79, 0xA0, 0x00, 0x00, 0x00, 0x00, 0xE6])
CMD_AUTO_CALIB_OFF = bytes([0xFF, 0x01, 0x79, 0x00, 0x00, 0x00, 0x00, 0x00, 0x86])


class CO2Sensor:
    """
    MH-Z19C CO2 sensor reader via UART.
    """

    def __init__(self, port: str = '/dev/serial0'):
        """
        Initialize CO2 sensor.

        Args:
            port: Serial port path (default: /dev/serial0)
        """
        self.port = port
        self._serial = None
        self._initialized = False

    def init(self) -> bool:
        """
        Initialize serial connection to sensor.

        Returns:
            True if initialization successful, False otherwise
        """
        try:
            import serial
            self._serial = serial.Serial(
                port=self.port,
                baudrate=BAUDRATE,
                timeout=TIMEOUT
            )
            self._initialized = True
            log.info(f"CO2 sensor initialized on {self.port}")
            return True
        except Exception as e:
            log.warning(f"Failed to initialize CO2 sensor: {e}")
            self._initialized = False
            return False

    def read(self) -> tuple[int | None, int | None]:
        """
        Read CO2 concentration and temperature from sensor.

        Returns:
            Tuple of (co2_ppm, temperature_celsius) or (None, None) on error
        """
        if not self._initialized or not self._serial:
            return None, None

        try:
            self._serial.reset_input_buffer()
            self._serial.write(CMD_READ_CO2)
            self._serial.flush()
            time.sleep(0.2)

            response = self._serial.read(9)

            if len(response) != 9:
                log.debug(f"Invalid response length: {len(response)}")
                return None, None

            if response[0] != 0xFF or response[1] != 0x86:
                log.debug(f"Invalid response header: {response[:2].hex()}")
                return None, None

            # Verify checksum
            checksum = self._calculate_checksum(response)
            if checksum != response[8]:
                log.debug(f"Checksum mismatch: {checksum} != {response[8]}")
                return None, None

            co2 = response[2] * 256 + response[3]
            temp = response[4] - 40  # Temperature offset

            return co2, temp

        except Exception as e:
            log.error(f"Failed to read CO2 sensor: {e}")
            return None, None

    def read_co2(self) -> int | None:
        """
        Read only CO2 concentration.

        Returns:
            CO2 in ppm or None on error
        """
        co2, _ = self.read()
        return co2

    def _calculate_checksum(self, data: bytes) -> int:
        """Calculate MH-Z19 checksum."""
        return (0xFF - (sum(data[1:8]) & 0xFF) + 1) & 0xFF

    def set_auto_calibration(self, enabled: bool) -> bool:
        """
        Enable or disable automatic baseline calibration (ABC).

        ABC calibrates the sensor assuming it sees 400ppm at least once
        every 24 hours. Disable if sensor is in a space that never
        reaches outdoor air quality.

        Args:
            enabled: True to enable ABC, False to disable

        Returns:
            True if command sent successfully
        """
        if not self._initialized or not self._serial:
            return False

        try:
            cmd = CMD_AUTO_CALIB_ON if enabled else CMD_AUTO_CALIB_OFF
            self._serial.write(cmd)
            self._serial.flush()
            log.info(f"Auto calibration {'enabled' if enabled else 'disabled'}")
            return True
        except Exception as e:
            log.error(f"Failed to set auto calibration: {e}")
            return False

    def calibrate_zero(self) -> bool:
        """
        Perform zero point calibration.

        WARNING: Only call this when sensor is in 400ppm environment
        (outdoor fresh air) for at least 20 minutes!

        Returns:
            True if command sent successfully
        """
        if not self._initialized or not self._serial:
            return False

        try:
            self._serial.write(CMD_CALIBRATE_ZERO)
            self._serial.flush()
            log.info("Zero point calibration triggered")
            return True
        except Exception as e:
            log.error(f"Failed to calibrate zero: {e}")
            return False

    def cleanup(self):
        """Close serial connection."""
        if self._serial:
            try:
                self._serial.close()
            except Exception:
                pass
            self._serial = None
        self._initialized = False
