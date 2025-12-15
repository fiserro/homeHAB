import helper.generated.Java223Script;
import helper.rules.annotations.ItemStateChangeTrigger;
import helper.rules.annotations.Rule;
import helper.rules.eventinfo.ItemStateChange;
import io.github.fiserro.homehab.HabState;
import io.github.fiserro.homehab.HabState.Fields;
import io.github.fiserro.homehab.HabStateFactory;
import io.github.fiserro.homehab.MqttItemMappings;
import io.github.fiserro.homehab.hrv.HrvCalculator;

/**
 * HRV (Heat Recovery Ventilator) control script.
 */
public class HrvControl extends Java223Script {

    @Rule(name = "hrv.item.changed", description = "Handle HRV item changes")
    @ItemStateChangeTrigger(itemName = "manualMode")
    public void onItemChanged(ItemStateChange eventInfo) {
        MqttItemMappings itemMappings = MqttItemMappings.builder()
            .of(Fields.smoke, _items.mqttZigbeeSmoke_0xa4c138aa8b540e22())
            .of(Fields.humidity, _items.mqttZigbeeHumidity_0x00158d008b8b7beb())
            .build();

        HabState inputState = HabStateFactory.of(items, itemMappings);
        HabState completeState = new HrvCalculator().calculate(inputState);

        HabStateFactory.writeState(_items, events, completeState);
    }

}
