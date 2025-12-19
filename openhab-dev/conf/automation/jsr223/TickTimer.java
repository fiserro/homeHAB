import helper.generated.Java223Script;
import helper.rules.annotations.CronTrigger;
import helper.rules.annotations.Rule;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.types.State;

/**
 * Timer that updates tickSecond every second to force UI refresh.
 */
public class TickTimer extends Java223Script {

  @Rule(name = "tick.timer", description = "Update tick every second for UI refresh")
  @CronTrigger(cronExpression = "* * * * * ?")
  public void tick() {
    int current = 0;
    try {
      State state = items.get("tickSecond");
      if (state instanceof DecimalType) {
        current = ((DecimalType) state).intValue();
      }
    } catch (Exception e) {
      // ignore
    }
    events.postUpdate("tickSecond", String.valueOf((current + 1) % 60));
  }
}
