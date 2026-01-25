package io.github.fiserro.homehab.module;

import io.github.fiserro.homehab.BooleanAggregation;
import io.github.fiserro.homehab.InputItem;
import io.github.fiserro.homehab.MqttItem;
import io.github.fiserro.homehab.NumericAggregation;
import io.github.fiserro.homehab.OutputItem;
import io.github.fiserro.homehab.ReadOnlyItem;
import io.github.fiserro.options.Option;

/**
 * Complete home automation state interface. Extends all modules and adds MQTT specifications
 * for this specific home.
 *
 * <p>MQTT bindings and aggregations are specified here via @MqttItem, @InputItem, @OutputItem,
 * and @ReadOnlyItem annotations with channel parameters.
 * Individual modules remain clean of any device-specific information.
 */
public interface HabState extends CommonModule<HabState>, HrvModule<HabState>, FlowerModule<HabState> {

    // CommonModule overrides with MQTT bindings (via HrvModule which extends CommonModule)

    @Override @Option
    @MqttItem(value = {"aqara*Temperature", "soil*Temperature"}, numAgg = NumericAggregation.AVG)
    default float insideTemperature() {
        return HrvModule.super.insideTemperature();
    }

    @Override @Option
    @ReadOnlyItem(channel = "mqtt:topic:mosquitto:hrv_bridge:gpio27_28-0316840d44ff")
    default float outsideTemperature() {
        return HrvModule.super.outsideTemperature();
    }

    @Override @Option
    @MqttItem(value = "aqara*Pressure", numAgg = NumericAggregation.AVG)
    default int pressure() {
        return HrvModule.super.pressure();
    }

    // HrvModule overrides with MQTT aggregation bindings

    @Override @Option
    @MqttItem(value = {"aqara*Humidity"}, numAgg = NumericAggregation.MAX)
    default int airHumidity() {
        return HrvModule.super.airHumidity();
    }

    @Override @Option
    @ReadOnlyItem(channel = "mqtt:topic:mosquitto:hrv_bridge:co2")
    default int co2() {
        return HrvModule.super.co2();
    }

    @Override @Option
    @MqttItem(numAgg = NumericAggregation.SUM)
    default int openWindows() {
        return HrvModule.super.openWindows();
    }

    @Override @Option
    @MqttItem(value = "fire*Smoke", boolAgg = BooleanAggregation.OR)
    default boolean smoke() {
        return HrvModule.super.smoke();
    }

    @Override @Option
    @MqttItem(boolAgg = BooleanAggregation.OR)
    default boolean gas() {
        return HrvModule.super.gas();
    }

    // HrvModule overrides with MQTT channel bindings (panel commands)

    @Override @Option
    @InputItem(channel = "mqtt:topic:mosquitto:panel_commands:manualMode")
    default boolean manualMode() {
        return HrvModule.super.manualMode();
    }

    @Override @Option
    @InputItem(channel = "mqtt:topic:mosquitto:panel_commands:tempManualMode")
    default boolean temporaryManualMode() {
        return HrvModule.super.temporaryManualMode();
    }

    @Override @Option
    @InputItem(channel = "mqtt:topic:mosquitto:panel_commands:boostMode")
    default boolean temporaryBoostMode() {
        return HrvModule.super.temporaryBoostMode();
    }

    @Override @Option
    @InputItem(channel = "mqtt:topic:mosquitto:panel_commands:manualPower")
    default int manualPower() {
        return HrvModule.super.manualPower();
    }

    @Override @Option
    @InputItem(channel = "mqtt:topic:mosquitto:panel_commands:bypass")
    @OutputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:gpio17")
    default boolean bypass() {
        return HrvModule.super.bypass();
    }

    // HrvModule overrides with MQTT channel bindings (HRV bridge)

    @Override @Option
    @ReadOnlyItem(channel = "mqtt:topic:mosquitto:hrv_bridge:current_ad0")
    default int powerAd0() {
        return HrvModule.super.powerAd0();
    }

    @Override @Option
    @ReadOnlyItem(channel = "mqtt:topic:mosquitto:hrv_bridge:current_ad1")
    default int powerAd1() {
        return HrvModule.super.powerAd1();
    }

    @Override @Option
    @OutputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:pwmGpio18")
    default int hrvOutputGpio18() {
        return HrvModule.super.hrvOutputGpio18();
    }

    @Override @Option
    @OutputItem(channel = "mqtt:topic:mosquitto:hrv_bridge:pwmGpio19")
    default int hrvOutputGpio19() {
        return HrvModule.super.hrvOutputGpio19();
    }

    // FlowerModule overrides with MQTT bindings

    @Override @Option
    @MqttItem(value = "soil*Humidity", numAgg = NumericAggregation.MIN)
    default int soilHumidity() {
        return FlowerModule.super.soilHumidity();
    }
}
