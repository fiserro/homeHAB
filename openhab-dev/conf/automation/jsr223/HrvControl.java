import helper.generated.Items;
import helper.generated.Java223Script;
import helper.rules.annotations.ItemStateChangeTrigger;
import helper.rules.annotations.Rule;
import io.github.fiserro.homehab.Calculator;
import io.github.fiserro.homehab.HabStateFactory;
import io.github.fiserro.homehab.hrv.HrvCalculator;
import java.time.Instant;
import org.openhab.automation.java223.common.InjectBinding;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

/**
 * HRV (Heat Recovery Ventilator) control script. Split into multiple rules due to annotation
 * processing limitations.
 *
 * <p>This script uses the modular HabState interface which extends module interfaces (CommonModule,
 * HrvModule, FlowerModule) and adds home-specific MQTT bindings.
 */
public class HrvControl extends Java223Script {


  @InjectBinding(enable = false)
  private final Calculator<HabState> calculator = new HrvCalculator<>();

  @Rule(name = "item.changed", description = "Handle item changes")
  @ItemStateChangeTrigger(itemName = "*")
  public void onZigbeeItemChanged() {
    HabState state = HabStateFactory.of(HabState.class, items);
    HabState calculated = calculator.apply(state);

    if (calculated.hasOutputChanged(state)) {
      events.sendCommand(_items.hrvOutputPower(), calculated.hrvOutputPower());
      events.sendCommand(_items.hrvOutputIntake(), calculated.hrvOutputIntake());
      events.sendCommand(_items.hrvOutputExhaust(), calculated.hrvOutputExhaust());
      events.sendCommand(_items.hrvOutputGpio18(), calculated.hrvOutputGpio18());
      events.sendCommand(_items.hrvOutputGpio19(), calculated.hrvOutputGpio19());
      events.sendCommand(_items.bypass(), calculated.bypass() ? OnOffType.ON : OnOffType.OFF);
    }
  }

  @Rule(name = "manual.power.changed", description = "Handle manual power changes")
  @ItemStateChangeTrigger(itemName = Items.manualPower)
  public void onManualPowerChanged() {
    if (_items.manualMode().getStateAs(OnOffType.class) == OnOffType.OFF) {
      events.sendCommand(_items.temporaryManualMode(), OnOffType.ON);
    }
  }

  @Rule(name = "manual.mode.changed", description = "Handle manual mode changes")
  @ItemStateChangeTrigger(itemName = Items.manualMode)
  public void onManualModeChanged() {
    if (_items.manualMode().getStateAs(OnOffType.class) == OnOffType.ON) {
      events.sendCommand(_items.temporaryManualMode(), OnOffType.OFF);
      events.sendCommand(_items.temporaryBoostMode(), OnOffType.OFF);
    }
  }

  @Rule(name = "manual.temp.mode.changed", description = "Handle temporary manual mode changes")
  @ItemStateChangeTrigger(itemName = Items.temporaryManualMode)
  public void onTempManualModeChanged() {
    if (_items.temporaryManualMode().getStateAs(OnOffType.class) == OnOffType.ON) {
      events.sendCommand(_items.manualMode(), OnOffType.OFF);
      events.sendCommand(_items.temporaryBoostMode(), OnOffType.OFF);
      // Set off time = now + duration, reset other mode's off time
      int durationSec =
          _items.temporaryManualModeDurationSec().getStateAs(DecimalType.class).intValue();
      long offTime = Instant.now().getEpochSecond() + durationSec;
      events.postUpdate(_items.temporaryManualModeOffTime(), (State) new DecimalType(offTime));
      events.postUpdate(_items.temporaryBoostModeOffTime(), (State) new DecimalType(0));
    } else {
      // Mode turned OFF - reset off time
      events.postUpdate(_items.temporaryManualModeOffTime(), (State) new DecimalType(0));
    }
  }

  @Rule(name = "boost.temp.mode.changed", description = "Handle temporary boost mode changes")
  @ItemStateChangeTrigger(itemName = Items.temporaryBoostMode)
  public void onTempBoostModeChanged() {
    if (_items.temporaryBoostMode().getStateAs(OnOffType.class) == OnOffType.ON) {
      events.sendCommand(_items.manualMode(), OnOffType.OFF);
      events.sendCommand(_items.temporaryManualMode(), OnOffType.OFF);
      // Set off time = now + duration, reset other mode's off time
      int durationSec =
          _items.temporaryBoostModeDurationSec().getStateAs(DecimalType.class).intValue();
      long offTime = Instant.now().getEpochSecond() + durationSec;
      events.postUpdate(_items.temporaryBoostModeOffTime(), (State) new DecimalType(offTime));
      events.postUpdate(_items.temporaryManualModeOffTime(), (State) new DecimalType(0));
    } else {
      // Mode turned OFF - reset off time
      events.postUpdate(_items.temporaryBoostModeOffTime(), (State) new DecimalType(0));
    }
  }

  @Rule(name = "bypass.changed", description = "Forward bypass state to HRV bridge")
  @ItemStateChangeTrigger(itemName = Items.bypass)
  public void onBypassChanged() {
    // When bypass state changes (e.g., from panel command), send command to trigger
    // the hrv_bridge channel's commandTopic (stateTopic updates don't trigger commandTopic)
    OnOffType state = _items.bypass().getStateAs(OnOffType.class);
    if (state != null) {
      events.sendCommand(_items.bypass(), state);
    }
  }
}
