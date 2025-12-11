package io.github.fiserro.homehab.hrv;

import io.github.fiserro.options.OptionsFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.types.DecimalType;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Loads HRV configuration from OpenHAB Items.
 * Configuration items have prefix 'hrv_config_'
 */
@Slf4j
@RequiredArgsConstructor
public class HrvConfigLoader {

    private static final String CONFIG_PREFIX = "hrv_config_";

    private final ItemRegistry itemRegistry;

    /**
     * Loads configuration from OpenHAB Items.
     * Items have prefix 'hrv_config_'
     *
     * @return HRV configuration with values from OpenHAB Items or defaults
     */
    public HrvConfig loadConfiguration() {
        // Create temporary instance to get all option definitions
        HrvConfig defaultConfig = OptionsFactory.create(HrvConfig.class);

        // Load values from OpenHAB Items for each option
        Map<String, Object> configValues = defaultConfig.options().stream()
            .map(optionDef -> {
                String propertyName = optionDef.name();
                String itemName = CONFIG_PREFIX + camelToSnakeCase(propertyName);
                return loadConfigValue(propertyName, itemName);
            })
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return OptionsFactory.create(HrvConfig.class, configValues);
    }

    /**
     * Loads a single config value from OpenHAB Item.
     * Returns Optional with property name and value if item exists.
     */
    private Optional<Map.Entry<String, Object>> loadConfigValue(String propertyName, String itemName) {
        try {
            Item item = itemRegistry.get(itemName);
            if (item != null && item.getState() instanceof DecimalType) {
                int value = ((DecimalType) item.getState()).intValue();
                log.debug("Loaded config '{}' = {} from item '{}'", propertyName, value, itemName);
                return Optional.of(Map.entry(propertyName, value));
            }
        } catch (Exception e) {
            log.debug("Config parameter '{}' not found, will use default value", itemName);
        }
        return Optional.empty();
    }

    /**
     * Converts camelCase to snake_case for OpenHAB item names.
     * Example: humidityThreshold -> humidity_threshold
     * Example: co2Threshold -> co2_threshold
     */
    private String camelToSnakeCase(String camelCase) {
        return camelCase
            .replaceAll("([a-z])([A-Z])", "$1_$2")
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .toLowerCase();
    }
}
