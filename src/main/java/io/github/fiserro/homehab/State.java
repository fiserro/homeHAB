package io.github.fiserro.homehab;

import lombok.Builder;

import static io.github.fiserro.homehab.InputType.*;

/** State of the HRV system at a given moment. */
@Builder(toBuilder = true, builderClassName = "StateBuilder")
public record State(
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
    @Aggregate(AggregationType.AVG) @MqttItem(WINDOW_OPEN) float openWindows,
    @Aggregate(AggregationType.MAX) @MqttItem(HUMIDITY) int humidity,
    @Aggregate(AggregationType.MAX) @MqttItem(CO2) int co2,
    @Aggregate(AggregationType.AVG) @MqttItem(TEMPERATURE) int temperature,
    @Aggregate(AggregationType.AVG) @MqttItem(PRESSURE) int pressure,
    @Aggregate(AggregationType.MAX) @MqttItem(SMOKE) boolean smoke,
    @Aggregate(AggregationType.MAX) @MqttItem(GAS) boolean gas,
    @OutputItem int hrvOutputPower) {

  public static final int ZERO_POWER = 0;
  public static final int LOW_POWER = 15;
  public static final int MID_POWER = 50;
  public static final int HIGH_POWER = 95;
  public static final int CLOSED_WINDOWS = 0;
  public static final int DEFAULT_HUMIDITY = 40;
  public static final int DEFAULT_CO_2 = 500;
  public static final int DEFAULT_TEMPERATURE = 20;
  public static final int DEFAULT_PRESSURE = 1000;
  public static final boolean NO_SMOKE = false;
  public static final boolean NO_GAS = false;

  public static class StateBuilder {
    public StateBuilder() {
      openWindows = CLOSED_WINDOWS;
      humidity = DEFAULT_HUMIDITY;
      co2 = DEFAULT_CO_2;
      temperature = DEFAULT_TEMPERATURE;
      pressure = DEFAULT_PRESSURE;
      smoke = NO_SMOKE;
      gas = NO_GAS;
    }
  }
}
