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
    MqttItemMappings itemMappings =
        MqttItemMappings.builder()
            .of(Fields.smoke, _items.mqttZigbeeSmoke_0xa4c138aa8b540e22())
            .of(Fields.humidity, _items.mqttZigbeeHumidity_0x00158d008b8b7beb())
            .build();

    var inputState = HabStateFactory.of(items, itemMappings);
    inputState = new HrvCalculator().calculate(inputState);

    HabStateFactory.writeState(_items.gOutputs(), events, inputState);
  }

  @Rule(name = "manual.power.changed", description = "Handle manual power changes")
  @ItemStateChangeTrigger(itemName = HabState.Fields.manualPower)
  public void onManualPowerChanged() {
    events.sendCommand(_items.temporaryManualMode(), OnOffType.ON);
  }

  @Rule(name = "manual.mode.changed", description = "Handle manual mode changes")
  @ItemStateChangeTrigger(itemName = HabState.Fields.manualMode)
  public void onManualModeChanged() {
    if (_items.manualMode().getStateAs(OnOffType.class) == OnOffType.ON) {
      events.sendCommand(_items.temporaryManualMode(), OnOffType.OFF);
      events.sendCommand(_items.temporaryBoostMode(), OnOffType.OFF);
    }
  }

  @Rule(name = "manual.temp.mode.changed", description = "Handle temporary manual mode changes")
  @ItemStateChangeTrigger(itemName = Fields.temporaryManualMode)
  public void onTempManualModeChanged() {
    if (_items.temporaryManualMode().getStateAs(OnOffType.class) == OnOffType.ON) {
      events.sendCommand(_items.manualMode(), OnOffType.OFF);
      events.sendCommand(_items.temporaryBoostMode(), OnOffType.OFF);
    }
  }

  @Rule(name = "boost.temp.mode.changed", description = "Handle temporary boost mode changes")
  @ItemStateChangeTrigger(itemName = Fields.temporaryBoostMode)
  public void onTempBoostModeChanged() {
    if (_items.temporaryBoostMode().getStateAs(OnOffType.class) == OnOffType.ON) {
      events.sendCommand(_items.manualMode(), OnOffType.OFF);
      events.sendCommand(_items.temporaryManualMode(), OnOffType.OFF);
    }
  }


}
