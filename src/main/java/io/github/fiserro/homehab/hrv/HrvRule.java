package io.github.fiserro.homehab.hrv;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.github.fiserro.homehab.AggregationType;
import java.util.*;
import java.util.concurrent.*;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.items.ItemRegistry;

/**
 * OpenHAB Rule for automatic HRV (Heat Recovery Ventilator) control.
 * Aggregates inputs from sensors and controls ventilator power.
 */
@Slf4j
public class HrvRule {

    private final ScriptBusEvent events;

    // Configuration
    private final Multimap<HrvInputType, String> inputChannels;
    private final String outputChannel;
    private final HrvConfigLoader configLoader;
    private HrvCalculator calculator;
    private HrvConfig config;

    // State management
    private final Map<String, Object> currentValues = new ConcurrentHashMap<>();
    private ScheduledFuture<?> temporaryModeTimer = null;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    // Configuration item name prefix
    private static final String CONFIG_PREFIX = "hrv_config_";

    @Builder(builderClassName = "HrvRuleBuilder")
    HrvRule(
            Multimap<HrvInputType, String> inputChannels,
            String outputChannel,
            ScriptBusEvent events,
            ItemRegistry itemRegistry) {
        this.inputChannels = inputChannels;
        this.outputChannel = outputChannel;
        this.events = events;
        this.configLoader = new HrvConfigLoader(itemRegistry);

        // Load configuration from OpenHAB Items
        this.config = loadConfiguration();
        this.calculator = new HrvCalculator(config);

        log.info("HrvRule initialized with config: {}", config);
    }

    /**
     * Custom builder for fluent API
     */
    @SuppressWarnings("unused")
    public static class HrvRuleBuilder {

        /**
         * Registers an input item for given HRV input type.
         * Can be called multiple times for the same type to register multiple sensors.
         *
         * @param type HRV input type
         * @param itemName OpenHAB item name
         * @return builder instance for chaining
         */
        public HrvRuleBuilder input(HrvInputType type, String itemName) {
            if (inputChannels == null) {
                inputChannels = HashMultimap.create();
            }
            inputChannels.put(type, itemName);
            return this;
        }

        /**
         * Sets the output channel for HRV power control.
         *
         * @param itemName OpenHAB item name for output
         * @return builder instance for chaining
         */
        public HrvRuleBuilder output(String itemName) {
            this.outputChannel = itemName;
            return this;
        }
    }

    /**
     * Loads configuration from OpenHAB Items.
     * Items have prefix 'hrv_config_'
     */
    private HrvConfig loadConfiguration() {
        return configLoader.loadConfiguration();
    }

    /**
     * Reloads configuration from Items and updates calculator.
     * Called when a configuration parameter changes.
     */
    public void reloadConfiguration() {
        this.config = loadConfiguration();
        this.calculator = new HrvCalculator(config);
        log.info("Configuration reloaded: {}", config);

        // Recalculate output with new configuration
        recalculateAndUpdate();
    }

    /**
     * Main trigger - called when any input changes.
     * This method will be dynamically bound to all input channels.
     */
    public void onInputChanged(String itemName, String newValue) {
        log.debug("Input changed: {} = {}", itemName, newValue);

        // Detect config change
        if (itemName.startsWith(CONFIG_PREFIX)) {
            log.info("Config parameter changed: {}", itemName);
            reloadConfiguration();
            return;
        }

        // Update internal state
        HrvInputType inputType = findInputType(itemName);
        if (inputType == null) {
            log.warn("Unknown channel: {}", itemName);
            return;
        }

        Object parsedValue = parseValue(newValue, inputType.getDataType());
        currentValues.put(itemName, parsedValue);

        // Handle temporary mode timers
        if (inputType == HrvInputType.TEMPORARY_MANUAL_MODE ||
            inputType == HrvInputType.TEMPORARY_BOOST_MODE) {
            handleTemporaryMode(itemName, parsedValue);
        }

        // Recalculate and update output
        recalculateAndUpdate();
    }

    private void recalculateAndUpdate() {
        // Aggregate inputs by type
        Map<HrvInputType, Object> aggregatedInputs = aggregateInputs();

        // Calculate output power
        int power = calculator.calculate(aggregatedInputs);

        // Send to output channel
        log.info("Setting ventilation power to: {}%", power);
        events.sendCommand(outputChannel, String.valueOf(power));
    }

    private Map<HrvInputType, Object> aggregateInputs() {
        Map<HrvInputType, Object> result = new HashMap<>();

        for (HrvInputType type : inputChannels.keySet()) {
            Collection<String> channels = inputChannels.get(type);
            List<Object> values = channels.stream()
                .map(currentValues::get)
                .filter(Objects::nonNull)
                .toList();

            if (!values.isEmpty()) {
                Object aggregated = aggregateValues(type, values);
                result.put(type, aggregated);
            }
        }

        return result;
    }

    private Object aggregateValues(HrvInputType type, List<Object> values) {
        if (values.size() == 1) {
            return values.getFirst();
        }

        AggregationType aggType = type.getAggregationType();

        if (type.getDataType() == Boolean.class) {
            return values.stream()
                .map(v -> (Boolean) v)
                .reduce(aggType::aggregate)
                .orElse(false);
        } else {
            return values.stream()
                .map(v -> (Number) v)
                .reduce(aggType::aggregate)
                .orElse(0);
        }
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

    private HrvInputType findInputType(String itemName) {
        for (Map.Entry<HrvInputType, String> entry : inputChannels.entries()) {
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
