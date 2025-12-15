package io.github.fiserro.homehab;

import lombok.Builder;
import lombok.With;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(asEnum = true)
@Builder(toBuilder = true, builderClassName = "HabStateBuilder")
public record HabState(
    @InputItem boolean manualMode,
    @InputItem boolean temporaryManualMode,
    @InputItem int temporaryManualModeDurationSec,
    @InputItem boolean boostMode,
    @InputItem boolean temporaryBoostMode,
    @InputItem int temporaryBoostModeDurationSec,
    @InputItem int humidityThreshold,
    @InputItem int co2ThresholdLow,
    @InputItem int co2ThresholdMid,
    @InputItem int co2ThresholdHigh,
    @InputItem int manualPower,
    @InputItem int boostPower,
    @InputItem int smokePower,
    @InputItem int gasPower,
    @InputItem int humidityPower,
    @InputItem int co2PowerLow,
    @InputItem int co2PowerMid,
    @InputItem int co2PowerHigh,
    @InputItem int basePower,
    @Aggregate(AggregationType.AVG) @MqttItem float openWindows,
    @Aggregate(AggregationType.MAX) @MqttItem int humidity,
    @Aggregate(AggregationType.MAX) @MqttItem int co2,
    @Aggregate(AggregationType.AVG) @MqttItem int temperature,
    @Aggregate(AggregationType.AVG) @MqttItem int pressure,
    @Aggregate(AggregationType.MAX) @MqttItem boolean smoke,
    @Aggregate(AggregationType.MAX) @MqttItem boolean gas,
    @With @OutputItem int hrvOutputPower) {

  @FieldNameConstants
  public static class HabStateBuilder {

    /**
     * Default values for the HAB state.
     */
    public HabStateBuilder() {
      manualMode = false;
      temporaryManualMode = false;
      temporaryManualModeDurationSec = 8 * 60 * 60;
      boostMode = false;
      temporaryBoostMode = false;
      temporaryBoostModeDurationSec = 10 * 60;
      humidityThreshold = 60;
      co2ThresholdLow = 500;
      co2ThresholdMid = 700;
      co2ThresholdHigh = 900;
      manualPower = 50;
      boostPower = 95;
      smokePower = 0;
      gasPower = 95;
      humidityPower = 95;
      co2PowerLow = 15;
      co2PowerMid = 50;
      co2PowerHigh = 95;
      basePower = 50;
      openWindows = 0;
      humidity = 40;
      co2 = 500;
      temperature = 20;
      pressure = 1000;
      smoke = false;
      gas = false;
      hrvOutputPower = 50;
    }
  }
}
