package io.github.fiserro.homehab.hrv;

import io.github.fiserro.options.OptionsFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.types.UnDefType;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Loads HRV configuration from OpenHAB Items.
 * Configuration items have prefix 'hrvConfig' in camelCase format.
 * Initializes items with default values if they are NULL/UNDEF.
 */
@Slf4j
@RequiredArgsConstructor
public class HrvConfigLoader {

    private static final String CONFIG_PREFIX = "hrvConfig";

    private final ItemRegistry itemRegistry;
    private final ScriptBusEvent events;

    /**
     * Loads configuration from OpenHAB Items.
     * Items have prefix 'hrvConfig' with camelCase property names.
     * Initializes NULL/UNDEF items with default values.
     *
     * @return HRV configuration with values from OpenHAB Items or defaults
     */
    public HrvConfig loadConfiguration() {
        // Create default config instance to get default values
        HrvConfig defaultConfig = OptionsFactory.create(HrvConfig.class);

        // Create map of property names to default values
        Map<String, Integer> defaults = Map.ofEntries(
            Map.entry("humidityThreshold", defaultConfig.humidityThreshold()),
            Map.entry("co2Threshold", defaultConfig.co2Threshold()),
            Map.entry("smokePower", defaultConfig.smokePower()),
            Map.entry("windowOpenPower", defaultConfig.windowOpenPower()),
            Map.entry("manualDefaultPower", defaultConfig.manualDefaultPower()),
            Map.entry("boostPower", defaultConfig.boostPower()),
            Map.entry("exhaustHoodPower", defaultConfig.exhaustHoodPower()),
            Map.entry("humidityPower", defaultConfig.humidityPower()),
            Map.entry("co2Power", defaultConfig.co2Power()),
            Map.entry("basePower", defaultConfig.basePower()),
            Map.entry("temporaryModeTimeoutMinutes", defaultConfig.temporaryModeTimeoutMinutes())
        );

        // Load values from OpenHAB Items for each option
        Map<String, Object> configValues = defaultConfig.options().stream()
            .map(optionDef -> {
                String propertyName = optionDef.name();
                String itemName = CONFIG_PREFIX + capitalize(propertyName);
                int defaultValue = defaults.get(propertyName);
                return loadConfigValue(propertyName, itemName, defaultValue);
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return OptionsFactory.create(HrvConfig.class, configValues);
    }

    /**
     * Loads a single config value from OpenHAB Item.
     * If item is NULL/UNDEF, initializes it with default value.
     * Returns Optional with property name and value if item exists.
     */
    private Optional<Map.Entry<String, Object>> loadConfigValue(String propertyName, String itemName, int defaultValue) {
        try {
            Item item = itemRegistry.getItem(itemName);
            if (item != null) {
                // Check if item has a valid value
                if (item.getState() instanceof DecimalType) {
                    int value = ((DecimalType) item.getState()).intValue();
                    log.debug("Loaded config '{}' = {} from item '{}'", propertyName, value, itemName);
                    return Optional.of(Map.entry(propertyName, value));
                }
                // Item exists but is NULL/UNDEF - initialize with default
                else if (item.getState() instanceof UnDefType) {
                    log.info("Initializing config item '{}' with default value: {}", itemName, defaultValue);
                    initializeItem(itemName, defaultValue);
                    return Optional.of(Map.entry(propertyName, defaultValue));
                }
            }
        } catch (Exception e) {
            log.debug("Config parameter '{}' not found, will use default value", itemName);
        }
        return Optional.empty();
    }

    /**
     * Initializes an item with a default value by posting an update.
     */
    private void initializeItem(String itemName, int value) {
        try {
            events.postUpdate(itemName, String.valueOf(value));
            log.info("Successfully initialized item '{}' with default value: {}", itemName, value);
        } catch (Exception e) {
            log.warn("Failed to initialize item '{}' with value {}: {}", itemName, value, e.getMessage());
        }
    }

    /**
     * Capitalizes first letter of property name for OpenHAB item names.
     * Example: humidityThreshold -> HumidityThreshold
     * Example: co2Threshold -> Co2Threshold
     */
    private String capitalize(String propertyName) {
        if (propertyName == null || propertyName.isEmpty()) {
            return propertyName;
        }
        return propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
    }
}
