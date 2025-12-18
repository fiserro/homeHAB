package io.github.fiserro.homehab;

import lombok.Builder;
import lombok.With;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants
@Builder(toBuilder = true, builderClassName = "HabStateBuilder")
public record HabState(
    @InputItem boolean manualMode,
    @With @InputItem boolean temporaryManualMode,
    @InputItem int temporaryManualModeDurationSec,
    @InputItem boolean temporaryBoostMode,
    @InputItem int temporaryBoostModeDurationSec,
    @ReadOnlyItem long temporaryManualModeOffTime,
    @ReadOnlyItem long temporaryBoostModeOffTime,
    @InputItem int humidityThreshold,
    @InputItem int co2ThresholdLow,
    @InputItem int co2ThresholdMid,
    @InputItem int co2ThresholdHigh,
    @InputItem int manualPower,
    @InputItem int powerLow,
    @InputItem int powerMid,
    @InputItem int powerHigh,
    @NumAgg(NumericAggregation.SUM) @MqttItem int openWindows,
    @NumAgg(NumericAggregation.MAX) @MqttItem({"aqara*Humidity"}) int airHumidity,
    @NumAgg(NumericAggregation.MIN) @MqttItem("soil*Humidity") int soilHumidity,
    @NumAgg(NumericAggregation.MAX) @MqttItem int co2,
    @NumAgg(NumericAggregation.AVG) @MqttItem({"aqara*Temperature", "soil*Temperature"}) int temperature,
    @NumAgg(NumericAggregation.AVG) @MqttItem("aqara*Pressure") int pressure,
    @BoolAgg(BooleanAggregation.OR) @MqttItem("fire*Smoke") boolean smoke,
    @BoolAgg(BooleanAggregation.OR) @MqttItem boolean gas,
    @With @OutputItem int hrvOutputPower) {

  public static final int POWER_OFF = 0;

  @FieldNameConstants
  public static class HabStateBuilder {

    /**
     * Default values for the HAB state.
     */
    public HabStateBuilder() {
      manualMode = false;
      temporaryManualMode = false;
      temporaryManualModeDurationSec = 8 * 60 * 60;
      temporaryBoostMode = false;
      temporaryBoostModeDurationSec = 10 * 60;
      humidityThreshold = 60;
      co2ThresholdLow = 500;
      co2ThresholdMid = 700;
      co2ThresholdHigh = 900;
      manualPower = 50;
      powerLow = 15;
      powerMid = 50;
      powerHigh = 95;
      openWindows = 0;
      co2 = 500;
      temperature = 20;
      pressure = 1000;
      smoke = false;
      gas = false;
      hrvOutputPower = 50;
    }
  }
}
