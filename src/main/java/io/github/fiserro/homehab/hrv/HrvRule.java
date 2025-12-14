package io.github.fiserro.homehab.hrv;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import helper.rules.eventinfo.ItemStateChange;
import io.github.fiserro.homehab.AggregationType;
import java.util.*;
import java.util.concurrent.*;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.items.GenericItem;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.SwitchItem;

/**
 * OpenHAB Rule for automatic HRV (Heat Recovery Ventilator) control.
 * Aggregates inputs from sensors and controls ventilator power.
 */
@Slf4j
public class HrvRule {

    private final ScriptBusEvent events;

  // Configuration
  private final Multimap<InputType, GenericItem> inputs;
  private final GenericItem output;
    private HrvCalculator calculator;

    // State management
    private final Map<String, Object> currentValues = new ConcurrentHashMap<>();
    private ScheduledFuture<?> temporaryModeTimer = null;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

  @Builder(builderClassName = "HrvRuleBuilder")
  public HrvRule(
      Multimap<InputType, GenericItem> inputs, GenericItem output, ScriptBusEvent events) {
    this.inputs = inputs;
    this.output = output;
        this.events = events;

    this.calculator = new HrvCalculator();
    }

    /**
     * Custom builder for fluent API
     */
    @SuppressWarnings("unused")
    public static class HrvRuleBuilder {
    public HrvRuleBuilder input(InputType type, GenericItem item) {
      if (inputs == null) {
        inputs = HashMultimap.create();
            }
      inputs.put(type, item);
            return this;
        }
    }


    /**
     * Reloads configuration from Items and updates calculator.
     * Called when a configuration parameter changes.
     */
    public void reloadConfiguration() {
    this.calculator = new HrvCalculator();
        log.info("Configuration reloaded: {}", config);

        // Recalculate output with new configuration
        recalculateAndUpdate();
    }

  /**
   * Main trigger - called when any input changes. This method will be dynamically bound to all
   * input channels.
   */
  public void onInputChanged(ItemStateChange eventInfo) {
    // TODO recalculate
  }

    private void recalculateAndUpdate() {
    // Aggregate inputs by type
    Map<InputType, Object> aggregatedInputs = aggregateInputs();

        // Calculate output power
        int power = calculator.calculate(aggregatedInputs);

        // Send to output channel
        log.info("Setting ventilation power to: {}%", power);
        events.sendCommand(outputChannel, String.valueOf(power));
    }

  private HrvState calculateState() {
    Map<InputType, Object> result = new HashMap<>();

    inputs
        .asMap()
        .forEach(
            (type, items) -> {
              val agg = type.aggregationType();
              val values = items.stream().map(this::getItemValue).toList();
              result.put(type, agg.aggregate(values));
            });

  }

  private Number getItemValue(GenericItem item) {
    return switch (item) {
      case NumberItem numberItem -> numberItem.getState().as(Number.class);
      case SwitchItem switchItem -> switchItem.getState().as(Number.class);
      default ->
          throw new IllegalArgumentException("Unsupported item type: " + item.getClass().getName());
    };
  }

    private void handleTemporaryMode(String itemName, Object value) {
        if (Boolean.TRUE.equals(value)) {
            // Cancel previous timer
            if (temporaryModeTimer != null && !temporaryModeTimer.isDone()) {
                temporaryModeTimer.cancel(false);
                log.debug("Cancelled previous temporary mode timer");
            }

            // Get timeout from configuration
            int timeoutMinutes = config.temporaryModeTimeoutMinutes();

            // Schedule auto-off
            log.info("Temporary mode activated - will auto-off in {} minutes", timeoutMinutes);
            temporaryModeTimer = executorService.schedule(
                () -> {
                    log.info("Temporary mode timeout - turning off");
                    events.sendCommand(itemName, "OFF");
                },
                timeoutMinutes,
                TimeUnit.MINUTES
            );
        }
    }

    /**
     * Cleanup method - should be called when rule is unloaded.
     * Shuts down the executor service gracefully.
     */
    public void shutdown() {
        log.info("Shutting down HRV rule executor service");
        if (temporaryModeTimer != null && !temporaryModeTimer.isDone()) {
            temporaryModeTimer.cancel(false);
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

  private InputType findInputType(String itemName) {
    for (Map.Entry<InputType, String> entry : inputChannels.entries()) {
            if (entry.getValue().equals(itemName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private Object parseValue(String value, Class<?> type) {
        if (type == Boolean.class) {
            return "ON".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
        } else if (type == Number.class) {
            return Double.parseDouble(value);
        }
        return value;
    }
}
