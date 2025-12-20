package io.github.fiserro.homehab;

import static io.github.fiserro.homehab.NumericAggregation.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.With;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants
@Builder(toBuilder = true, builderClassName = "HabStateBuilder")
public record HabState(
    @InputItem boolean manualMode,
    @With @InputItem boolean temporaryManualMode,
    @Max(value = 43200) @Min(value = 3600) @InputItem int temporaryManualModeDurationSec,
    @InputItem boolean temporaryBoostMode,
    @Max(value = 3600) @Min(value = 300) @InputItem int temporaryBoostModeDurationSec,
    @ReadOnlyItem long temporaryManualModeOffTime,
    @ReadOnlyItem long temporaryBoostModeOffTime,
    @ReadOnlyItem int tickSecond,
    @Min(40) @Max(80) @InputItem int humidityThreshold,
    @Min(400) @Max(800) @InputItem int co2ThresholdLow,
    @Min(600) @Max(1000) @InputItem int co2ThresholdMid,
    @Min(800) @Max(1500) @InputItem int co2ThresholdHigh,
    @Min(0) @Max(100) @InputItem int manualPower,
    @Min(0) @Max(100) @InputItem int powerLow,
    @Min(0) @Max(100) @InputItem int powerMid,
    @Min(0) @Max(100) @InputItem int powerHigh,
    @NumAgg(SUM) @MqttItem int openWindows,
    @NumAgg(MAX) @MqttItem({"aqara*Humidity"}) int airHumidity,
    @NumAgg(MIN) @MqttItem("soil*Humidity") int soilHumidity,
    @NumAgg(MAX) @MqttItem int co2,
    @NumAgg(AVG) @MqttItem({"aqara*Temperature", "soil*Temperature"}) int temperature,
    @NumAgg(AVG) @MqttItem("aqara*Pressure") int pressure,
    @BoolAgg(BooleanAggregation.OR) @MqttItem("fire*Smoke") boolean smoke,
    @BoolAgg(BooleanAggregation.OR) @MqttItem boolean gas,
    @With @OutputItem(channel = "mqtt:topic:hrv:power") int hrvOutputPower) {

  public static final int POWER_OFF = 0;

  @FieldNameConstants
  public static class HabStateBuilder {

    /** Default values for the HAB state. */
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
