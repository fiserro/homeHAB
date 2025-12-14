package io.github.fiserro.homehab.hrv;

import io.github.fiserro.homehab.InputItem;
import io.github.fiserro.homehab.MqttItem;
import lombok.Builder;

import static io.github.fiserro.homehab.hrv.InputType.*;

/** State of the HRV system at a given moment. */
@Builder(toBuilder = true)
public record HrvState(
    @InputItem(MANUAL_MODE) boolean manualMode,
    @InputItem(TEMPORARY_MANUAL_MODE) boolean temporaryManualMode,
    @InputItem(BOOST_MODE) boolean boostMode,
    @InputItem(TEMPORARY_BOOST_MODE) boolean temporaryBoostMode,
    @InputItem(HUMIDITY_THRESHOLD) int humidityThreshold,
    @InputItem(CO2_THRESHOLD_LOW) int co2ThresholdLow,
    @InputItem(CO2_THRESHOLD_MID) int co2ThresholdMid,
    @InputItem(CO2_THRESHOLD_HIGH) int co2ThresholdHigh,
    @InputItem(HRV_MANUAL_POWER) int manualPower,
    @InputItem(HRV_BOOST_POWER) int boostPower,
    @InputItem(HRV_SMOKE_POWER) int smokePower,
    @InputItem(HRV_GAS_POWER) int gasPower,
    @InputItem(HRV_HUMIDITY_POWER) int humidityPower,
    @InputItem(HRV_CO2_POWER_LOW) int co2PowerLow,
    @InputItem(HRV_CO2_POWER_MID) int co2PowerMid,
    @InputItem(HRV_CO2_POWER_HIGH) int co2PowerHigh,
    @InputItem(HRV_BASE_POWER) int basePower,

    @MqttItem(WINDOW_OPEN) float openWindows, // ratio: openWindows / totalWindows
    @MqttItem(HUMIDITY) int humidity,
    @MqttItem(CO2)int co2,
    @MqttItem(SMOKE) boolean smoke,
    @MqttItem(GAS) boolean gas

    ) {
}
