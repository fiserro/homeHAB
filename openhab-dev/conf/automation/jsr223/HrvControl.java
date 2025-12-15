import helper.generated.Java223Script;
import helper.rules.annotations.Rule;
import helper.rules.annotations.ItemStateChangeTrigger;
import helper.rules.eventinfo.ItemStateChange;
import io.github.fiserro.homehab.HabState;
import io.github.fiserro.homehab.HabState.Fields;
import io.github.fiserro.homehab.HabStateFactory;
import io.github.fiserro.homehab.MqttItemMappings;
import io.github.fiserro.homehab.hrv.HrvCalculator;
import lombok.val;
import org.openhab.automation.java223.common.InjectBinding;

/**
 * HRV (Heat Recovery Ventilator) control script.
 * Split into multiple rules due to annotation processing limitations.
 */
public class HrvControl extends Java223Script {

    @InjectBinding(enable = false)
    private final MqttItemMappings itemMappings = MqttItemMappings.builder()
        .of(Fields.smoke, _items.mqttZigbeeSmoke_0xa4c138aa8b540e22())
        .of(Fields.humidity, _items.mqttZigbeeHumidity_0x00158d008b8b7beb())
        .build();

    @Rule(name = "item.changed", description = "Handle item changes")
    @ItemStateChangeTrigger(itemName = "*")
    public void onZigbeeItemChanged(ItemStateChange eventInfo) {
        val inputState = HabStateFactory.of(items, itemMappings);
        val completeState = new HrvCalculator().calculate(inputState);

        HabStateFactory.writeState(_items, events, completeState);
    }

}
