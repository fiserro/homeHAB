import helper.generated.Java223Script;
import helper.rules.annotations.ItemStateChangeTrigger;
import helper.rules.annotations.Rule;
import io.github.fiserro.homehab.HabState;
import io.github.fiserro.homehab.HabState.Fields;
import io.github.fiserro.homehab.HabStateFactory;
import io.github.fiserro.homehab.MqttItemMappings;
import io.github.fiserro.homehab.hrv.HrvCalculator;
import org.openhab.core.library.types.OnOffType;

/**
 * HRV (Heat Recovery Ventilator) control script. Split into multiple rules due to annotation
 * processing limitations.
 */
public class HrvControl extends Java223Script {

  @Rule(name = "item.changed", description = "Handle item changes")
  @ItemStateChangeTrigger(itemName = "*")
  public void onZigbeeItemChanged() {
    logger.info("Item changed: {}", items);
    HabStateFactory.writeState(_items.gOutputs(), events, getState().withTemporaryManualMode(true));
  }

  @Rule(name = "manual.mode", description = "Handle item changes")
  @ItemStateChangeTrigger(itemName = HabState.Fields.manualPower)
  public void onManualPowerChanged() {
    logger.info("Manual mode changed to {}", getState().manualMode());
    events.sendCommand(_items.temporaryManualMode(), OnOffType.ON);
  }

  private HabState getState() {
    MqttItemMappings itemMappings =
        MqttItemMappings.builder()
            .of(Fields.smoke, _items.mqttZigbeeSmoke_0xa4c138aa8b540e22())
            .of(Fields.humidity, _items.mqttZigbeeHumidity_0x00158d008b8b7beb())
            .build();

    HabState inputState = HabStateFactory.of(items, itemMappings);
    return new HrvCalculator().calculate(inputState);
  }
}
