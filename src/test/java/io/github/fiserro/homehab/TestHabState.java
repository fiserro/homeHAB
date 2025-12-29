package io.github.fiserro.homehab;

import io.github.fiserro.homehab.module.FlowerModule;
import io.github.fiserro.homehab.module.HrvModule;

/**
 * Test-specific HabState interface for unit tests.
 * Mirrors the structure of the real HabState in openhab-dev.
 *
 * <p>All modules use self-referential type parameters, so TestHabState passes itself
 * as the type argument to each module.
 *
 * <p>Note: No @OptionsExtensions annotation here because extensions are
 * passed explicitly via HabStateFactory.
 *
 * <p>Note: HrvModule extends CommonModule, so we don't need to extend it directly.
 */
public interface TestHabState extends HrvModule<TestHabState>, FlowerModule<TestHabState> {

    @Override @MqttItem(value = {"aqara*Temperature", "soil*Temperature"}, numAgg = NumericAggregation.AVG)
    default float insideTemperature() { return HrvModule.super.insideTemperature(); }

    @Override @MqttItem(value = "aqara*Pressure", numAgg = NumericAggregation.AVG)
    default int pressure() { return HrvModule.super.pressure(); }

    @Override @MqttItem(value = {"aqara*Humidity"}, numAgg = NumericAggregation.MAX)
    default int airHumidity() { return HrvModule.super.airHumidity(); }

    @Override @MqttItem(value = "soil*Humidity", numAgg = NumericAggregation.MIN)
    default int soilHumidity() { return FlowerModule.super.soilHumidity(); }

    @Override @MqttItem(numAgg = NumericAggregation.MAX)
    default int co2() { return HrvModule.super.co2(); }

    @Override @MqttItem(numAgg = NumericAggregation.SUM)
    default int openWindows() { return HrvModule.super.openWindows(); }

    @Override @MqttItem(value = "fire*Smoke", boolAgg = BooleanAggregation.OR)
    default boolean smoke() { return HrvModule.super.smoke(); }

    @Override @MqttItem(boolAgg = BooleanAggregation.OR)
    default boolean gas() { return HrvModule.super.gas(); }

    @Override @OutputItem(channel = "mqtt:topic:hrv:power")
    default int hrvOutputPower() { return HrvModule.super.hrvOutputPower(); }
}
