import io.github.fiserro.homehab.BooleanAggregation;
import io.github.fiserro.homehab.MqttItem;
import io.github.fiserro.homehab.NumericAggregation;
import io.github.fiserro.homehab.OutputItem;
import io.github.fiserro.homehab.ReadOnlyItem;
import io.github.fiserro.homehab.module.FlowerModule;
import io.github.fiserro.homehab.module.HrvModule;

/**
 * Complete home automation state interface.
 * Extends all modules and adds MQTT specifications for this specific home.
 *
 * <p>All modules use self-referential type parameters, so HabState passes itself
 * as the type argument to each module.
 *
 * <p>MQTT bindings and aggregations are specified here via @MqttItem annotation.
 *
 * <p>Note: No @OptionsExtensions annotation here because extensions are
 * passed explicitly via HabStateFactory.
 *
 * <p>Note: HrvModule extends CommonModule, so we don't need to extend it directly.
 */
public interface HabState extends HrvModule<HabState>, FlowerModule<HabState> {

    // Override methods to add MQTT specifications and aggregations for this home

    @Override @ReadOnlyItem(channel = "mqtt:topic:mosquitto:hrv_bridge:gpio27_28-0316840d44ff")
    default float outsideTemperature() { return HrvModule.super.outsideTemperature(); }

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

    @Override @OutputItem
    default int hrvOutputPower() { return HrvModule.super.hrvOutputPower(); }

    @Override @OutputItem
    default int hrvOutputIntake() { return HrvModule.super.hrvOutputIntake(); }

    @Override @OutputItem
    default int hrvOutputExhaust() { return HrvModule.super.hrvOutputExhaust(); }

}
