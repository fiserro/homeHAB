import helper.generated.Java223Script;
import helper.rules.annotations.ItemStateChangeTrigger;
import helper.rules.annotations.Rule;
import io.github.fiserro.homehab.HabStateFactory;
import io.github.fiserro.homehab.hrv.HrvCalculator;
import java.time.Instant;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

/**
 * HRV (Heat Recovery Ventilator) control script. Split into multiple rules due to annotation
 * processing limitations.
 *
 * <p>This script uses the modular HabState interface which extends module interfaces
 * (CommonModule, HrvModule, FlowerModule) and adds home-specific MQTT bindings.
 */
public class HrvControl extends Java223Script {

    // Field name constants
    private static final String MANUAL_POWER = "manualPower";
    private static final String MANUAL_MODE = "manualMode";
    private static final String TEMPORARY_MANUAL_MODE = "temporaryManualMode";
    private static final String TEMPORARY_BOOST_MODE = "temporaryBoostMode";

    @Rule(name = "item.changed", description = "Handle item changes")
    @ItemStateChangeTrigger(itemName = "*")
    public void onZigbeeItemChanged() {
        HabState state = HabStateFactory.of(HabState.class, items);
        HabState calculated = new HrvCalculator<HabState>().calculate(state);
        events.sendCommand(_items.hrvOutputPower(), calculated.hrvOutputPower());
    }

    @Rule(name = "manual.power.changed", description = "Handle manual power changes")
    @ItemStateChangeTrigger(itemName = MANUAL_POWER)
    public void onManualPowerChanged() {
        events.sendCommand(_items.temporaryManualMode(), OnOffType.ON);
    }

    @Rule(name = "manual.mode.changed", description = "Handle manual mode changes")
    @ItemStateChangeTrigger(itemName = MANUAL_MODE)
    public void onManualModeChanged() {
        if (_items.manualMode().getStateAs(OnOffType.class) == OnOffType.ON) {
            events.sendCommand(_items.temporaryManualMode(), OnOffType.OFF);
            events.sendCommand(_items.temporaryBoostMode(), OnOffType.OFF);
        }
    }

    @Rule(name = "manual.temp.mode.changed", description = "Handle temporary manual mode changes")
    @ItemStateChangeTrigger(itemName = TEMPORARY_MANUAL_MODE)
    public void onTempManualModeChanged() {
        if (_items.temporaryManualMode().getStateAs(OnOffType.class) == OnOffType.ON) {
            events.sendCommand(_items.manualMode(), OnOffType.OFF);
            events.sendCommand(_items.temporaryBoostMode(), OnOffType.OFF);
            // Set off time = now + duration, reset other mode's off time
            int durationSec = _items.temporaryManualModeDurationSec().getStateAs(DecimalType.class).intValue();
            long offTime = Instant.now().getEpochSecond() + durationSec;
            events.postUpdate(_items.temporaryManualModeOffTime(), (State) new DecimalType(offTime));
            events.postUpdate(_items.temporaryBoostModeOffTime(), (State) new DecimalType(0));
        } else {
            // Mode turned OFF - reset off time
            events.postUpdate(_items.temporaryManualModeOffTime(), (State) new DecimalType(0));
        }
    }

    @Rule(name = "boost.temp.mode.changed", description = "Handle temporary boost mode changes")
    @ItemStateChangeTrigger(itemName = TEMPORARY_BOOST_MODE)
    public void onTempBoostModeChanged() {
        if (_items.temporaryBoostMode().getStateAs(OnOffType.class) == OnOffType.ON) {
            events.sendCommand(_items.manualMode(), OnOffType.OFF);
            events.sendCommand(_items.temporaryManualMode(), OnOffType.OFF);
            // Set off time = now + duration, reset other mode's off time
            int durationSec = _items.temporaryBoostModeDurationSec().getStateAs(DecimalType.class).intValue();
            long offTime = Instant.now().getEpochSecond() + durationSec;
            events.postUpdate(_items.temporaryBoostModeOffTime(), (State) new DecimalType(offTime));
            events.postUpdate(_items.temporaryManualModeOffTime(), (State) new DecimalType(0));
        } else {
            // Mode turned OFF - reset off time
            events.postUpdate(_items.temporaryBoostModeOffTime(), (State) new DecimalType(0));
        }
    }
}
