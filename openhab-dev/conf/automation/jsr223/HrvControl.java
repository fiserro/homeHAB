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

  // Filter cleaning management

  @Rule(name = "filter.cleaned.trigger", description = "Handle filter cleaned button press")
  @ItemStateChangeTrigger(itemName = Items.filterCleanedTrigger)
  public void onFilterCleanedTrigger() {
    OnOffType state = _items.filterCleanedTrigger().getStateAs(OnOffType.class);
    if (state == OnOffType.ON) {
      // Update last cleaned timestamp to now
      long now = Instant.now().getEpochSecond();
      events.postUpdate(_items.filterLastCleanedTimestamp(), (State) new DecimalType(now));
      // Reset trigger back to OFF
      events.sendCommand(_items.filterCleanedTrigger(), OnOffType.OFF);
      // Filter is now clean
      events.postUpdate(_items.filterCleaningRequired(), OnOffType.OFF);
      System.out.println("[HrvControl] Filter marked as cleaned at " + now);
    }
  }

  @Rule(name = "filter.status.check", description = "Check filter cleaning status on tick")
  @ItemStateChangeTrigger(itemName = Items.tickSecond)
  public void onTickCheckFilterStatus() {
    // Only check every minute (when tickSecond == 0)
    DecimalType tick = _items.tickSecond().getStateAs(DecimalType.class);
    if (tick == null || tick.intValue() != 0) {
      return;
    }

    DecimalType lastCleaned = _items.filterLastCleanedTimestamp().getStateAs(DecimalType.class);
    DecimalType intervalDays = _items.filterCleaningIntervalDays().getStateAs(DecimalType.class);

    if (lastCleaned == null || intervalDays == null) {
      return;
    }

    long lastCleanedSec = lastCleaned.longValue();
    long intervalSec = intervalDays.longValue() * 24 * 60 * 60;
    long now = Instant.now().getEpochSecond();

    boolean cleaningRequired = (lastCleanedSec + intervalSec) < now;
    OnOffType currentState = _items.filterCleaningRequired().getStateAs(OnOffType.class);
    OnOffType newState = cleaningRequired ? OnOffType.ON : OnOffType.OFF;

    // Only update if changed
    if (currentState != newState) {
      events.postUpdate(_items.filterCleaningRequired(), newState);
      if (cleaningRequired) {
        System.out.println("[HrvControl] Filter cleaning required!");
      }
    }
  }

  @Rule(name = "filter.interval.changed", description = "Recalculate filter status when interval changes")
  @ItemStateChangeTrigger(itemName = Items.filterCleaningIntervalDays)
  public void onFilterIntervalChanged() {
    // Force recalculation by triggering tick logic
    checkFilterCleaningStatus();
  }

  private void checkFilterCleaningStatus() {
    DecimalType lastCleaned = _items.filterLastCleanedTimestamp().getStateAs(DecimalType.class);
    DecimalType intervalDays = _items.filterCleaningIntervalDays().getStateAs(DecimalType.class);

    if (lastCleaned == null || intervalDays == null) {
      return;
    }

    long lastCleanedSec = lastCleaned.longValue();
    long intervalSec = intervalDays.longValue() * 24 * 60 * 60;
    long now = Instant.now().getEpochSecond();

    boolean cleaningRequired = lastCleanedSec == 0 || (lastCleanedSec + intervalSec) < now;
    OnOffType newState = cleaningRequired ? OnOffType.ON : OnOffType.OFF;
    events.postUpdate(_items.filterCleaningRequired(), newState);
  }
}
