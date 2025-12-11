package io.github.fiserro.homehab.hrv;

import io.github.fiserro.options.Option;
import io.github.fiserro.options.Options;
import lombok.Builder;
import lombok.Value;

/**
 * Configuration for the HRV system. All threshold values and power levels are configurable via
 * OpenHAB Items.
 */
public interface HrvConfig extends Options<HrvConfig> {

    @Option
    default int humidityThreshold() {
        return 70;
    }

    @Option
    default int co2Threshold() {
        return 1000;
    }

    @Option
    default int smokePower() {
        return 100;
    }

    @Option
    default int windowOpenPower() {
        return 0;
    }

    @Option
    default int manualDefaultPower() {
        return 50;
    }

    @Option
    default int boostPower() {
        return 80;
    }

    @Option
    default int exhaustHoodPower() {
        return 60;
    }

    @Option
    default int humidityPower() {
        return 60;
    }

    @Option
    default int co2Power() {
        return 50;
    }

    @Option
    default int basePower() {
        return 30;
    }

    @Option
    default int temporaryModeTimeoutMinutes() {
        return 30;
    }

}
