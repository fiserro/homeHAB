import helper.generated.Java223Script;
import helper.rules.annotations.CronTrigger;
import helper.rules.annotations.Rule;
import java.time.Instant;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;

/**
 * Scheduler script that checks temporary mode expiration.
 * Runs every minute to turn off expired temporary modes.
 */
public class ModeScheduler extends Java223Script {

  @Rule(name = "mode.scheduler", description = "Check and disable expired temporary modes")
  @CronTrigger(cronExpression = "0 * * * * ?")
  public void checkTemporaryModes() {
    long now = Instant.now().getEpochSecond();

    // Check temporary manual mode
    if (_items.temporaryManualMode().getStateAs(OnOffType.class) == OnOffType.ON) {
      DecimalType offTimeState = _items.temporaryManualModeOffTime().getStateAs(DecimalType.class);
      if (offTimeState != null) {
        long offTime = offTimeState.longValue();
        if (now >= offTime) {
          logger.info("Temporary manual mode expired, turning off");
          events.sendCommand(_items.temporaryManualMode(), OnOffType.OFF);
        }
      }
    }

    // Check temporary boost mode
    if (_items.temporaryBoostMode().getStateAs(OnOffType.class) == OnOffType.ON) {
      DecimalType offTimeState = _items.temporaryBoostModeOffTime().getStateAs(DecimalType.class);
      if (offTimeState != null) {
        long offTime = offTimeState.longValue();
        if (now >= offTime) {
          logger.info("Temporary boost mode expired, turning off");
          events.sendCommand(_items.temporaryBoostMode(), OnOffType.OFF);
        }
      }
    }
  }
}
