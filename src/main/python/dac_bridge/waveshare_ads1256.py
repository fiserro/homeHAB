# -*- coding: utf-8 -*-
# ADS1256 ADC driver for Waveshare High-Precision AD/DA Board
#
# Based on Waveshare example code, adapted for homeHAB project.
# License: MIT

from . import waveshare_config as config
import time

# Gain settings
ADS1256_GAIN = {
    1: 0,   # GAIN_1
    2: 1,   # GAIN_2
    4: 2,   # GAIN_4
    8: 3,   # GAIN_8
    16: 4,  # GAIN_16
    32: 5,  # GAIN_32
    64: 6,  # GAIN_64
}

# Data rate settings (samples per second)
ADS1256_DRATE = {
    30000: 0xF0,
    15000: 0xE0,
    7500: 0xD0,
    3750: 0xC0,
    2000: 0xB0,
    1000: 0xA1,
    500: 0x92,
    100: 0x82,
    60: 0x72,
    50: 0x63,
    30: 0x53,
    25: 0x43,
    15: 0x33,
    10: 0x23,
    5: 0x13,
    2.5: 0x03,
}

# Register addresses
REG_STATUS = 0x00
REG_MUX = 0x01
REG_ADCON = 0x02
REG_DRATE = 0x03
REG_IO = 0x04
REG_OFC0 = 0x05
REG_OFC1 = 0x06
REG_OFC2 = 0x07
REG_FSC0 = 0x08
REG_FSC1 = 0x09
REG_FSC2 = 0x0A

# Commands
CMD_WAKEUP = 0x00
CMD_RDATA = 0x01
CMD_RDATAC = 0x03
CMD_SDATAC = 0x0F
CMD_RREG = 0x10
CMD_WREG = 0x50
CMD_SELFCAL = 0xF0
CMD_SELFOCAL = 0xF1
CMD_SELFGCAL = 0xF2
CMD_SYSOCAL = 0xF3
CMD_SYSGCAL = 0xF4
CMD_SYNC = 0xFC
CMD_STANDBY = 0xFD
CMD_RESET = 0xFE

# Reference voltage (5V with VREF connected to 5V)
ADC_VREF = 5.0


class ADS1256:
    """ADS1256 24-bit ADC driver."""

    def __init__(self):
        self.gain = 1
        self.drate = 1000  # Default 1000 SPS
        self.cs_pin = config.CS_PIN
        self.rst_pin = config.RST_PIN
        self.drdy_pin = config.DRDY_PIN

    def reset(self):
        """Hardware reset the ADC."""
        config.digital_write(self.rst_pin, 0)
        config.delay_ms(20)
        config.digital_write(self.rst_pin, 1)
        config.delay_ms(20)

    def write_cmd(self, cmd: int):
        """Write a command to the ADC."""
        config.digital_write(self.cs_pin, 0)
        config.spi_writebyte([cmd])
        config.digital_write(self.cs_pin, 1)

    def write_reg(self, reg: int, data: int):
        """Write to a register."""
        config.digital_write(self.cs_pin, 0)
        config.spi_writebyte([CMD_WREG | reg, 0x00, data])
        config.digital_write(self.cs_pin, 1)

    def read_reg(self, reg: int) -> int:
        """Read from a register."""
        config.digital_write(self.cs_pin, 0)
        config.spi_writebyte([CMD_RREG | reg, 0x00])
        data = config.spi_readbytes(1)
        config.digital_write(self.cs_pin, 1)
        return data[0]

    def wait_drdy(self, timeout_ms: int = 1000) -> bool:
        """Wait for DRDY pin to go low (data ready)."""
        start = time.time()
        while time.time() - start < timeout_ms / 1000.0:
            if config.digital_read(self.drdy_pin) == 0:
                return True
            time.sleep(0.0001)  # 100us
        return False

    def init(self, gain: int = 1, drate: int = 1000):
        """Initialize the ADC with specified gain and data rate."""
        self.gain = gain
        self.drate = drate

        self.reset()

        # Read chip ID (should be 0x03 for ADS1256)
        chip_id = self.read_reg(REG_STATUS) >> 4
        if chip_id != 3:
            raise RuntimeError(f"ADS1256 not found (ID={chip_id}, expected 3)")

        # Configure ADC
        # STATUS: Auto-calibration enabled, buffer enabled (high impedance input)
        self.write_reg(REG_STATUS, 0x06)

        # MUX: Default AIN0-AINCOM (single-ended)
        self.write_reg(REG_MUX, 0x08)

        # ADCON: Clock out off, sensor detect off, gain
        gain_bits = ADS1256_GAIN.get(gain, 0)
        self.write_reg(REG_ADCON, gain_bits)

        # DRATE: Data rate
        drate_bits = ADS1256_DRATE.get(drate, 0xA1)
        self.write_reg(REG_DRATE, drate_bits)

        # Self-calibration
        self.write_cmd(CMD_SELFCAL)
        self.wait_drdy()

    def read_adc_raw(self) -> int:
        """Read raw 24-bit ADC value."""
        config.digital_write(self.cs_pin, 0)
        config.spi_writebyte([CMD_RDATA])
        time.sleep(0.00001)  # 10us delay
        data = config.spi_readbytes(3)
        config.digital_write(self.cs_pin, 1)

        # Combine 3 bytes into 24-bit value
        value = (data[0] << 16) | (data[1] << 8) | data[2]

        # Convert to signed (two's complement)
        if value & 0x800000:
            value -= 0x1000000

        return value

    def set_channel(self, channel: int):
        """Set single-ended channel (0-7, referenced to AINCOM)."""
        if not 0 <= channel <= 7:
            raise ValueError(f"Channel must be 0-7, got {channel}")

        # MUX register: positive input = channel, negative = AINCOM (8)
        mux = (channel << 4) | 0x08
        self.write_reg(REG_MUX, mux)

        # Sync and wakeup for new channel
        self.write_cmd(CMD_SYNC)
        self.write_cmd(CMD_WAKEUP)
        self.wait_drdy()

    def set_diff_channel(self, pos: int, neg: int):
        """Set differential channel pair."""
        if not (0 <= pos <= 7 and 0 <= neg <= 7):
            raise ValueError("Channels must be 0-7")

        mux = (pos << 4) | neg
        self.write_reg(REG_MUX, mux)

        self.write_cmd(CMD_SYNC)
        self.write_cmd(CMD_WAKEUP)
        self.wait_drdy()

    def read_channel(self, channel: int) -> int:
        """Read raw value from single-ended channel."""
        self.set_channel(channel)
        return self.read_adc_raw()

    def read_channel_voltage(self, channel: int) -> float:
        """Read voltage from single-ended channel."""
        raw = self.read_channel(channel)
        # 24-bit ADC: max value is 2^23 - 1 = 8388607
        # Full scale range is +/- VREF/gain
        voltage = (raw / 8388607.0) * (ADC_VREF / self.gain)
        return voltage

    def read_all_channels(self) -> list[int]:
        """Read raw values from all 8 channels."""
        values = []
        for ch in range(8):
            values.append(self.read_channel(ch))
        return values

    def read_all_voltages(self) -> list[float]:
        """Read voltages from all 8 channels."""
        voltages = []
        for ch in range(8):
            voltages.append(self.read_channel_voltage(ch))
        return voltages


### END OF FILE ###
