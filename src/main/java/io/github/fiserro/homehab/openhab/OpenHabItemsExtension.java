package io.github.fiserro.homehab.openhab;

import io.github.fiserro.homehab.InputItem;
import io.github.fiserro.homehab.MqttItem;
import io.github.fiserro.options.OptionDef;
import io.github.fiserro.options.Options;
import io.github.fiserro.options.OptionsBuilder;
import io.github.fiserro.options.extension.AbstractOptionsExtension;
import io.github.fiserro.options.extension.OptionExtensionType;
import java.lang.annotation.Annotation;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

/**
 * Options extension that reads values from OpenHAB items.
 *
 * <p>Reads values for:
 * <ul>
 *   <li>{@link InputItem} - individual items (user settings)</li>
 *   <li>{@link MqttItem} - sensor items with optional aggregation</li>
 * </ul>
 */
@Slf4j
public class OpenHabItemsExtension extends AbstractOptionsExtension {

    private final Map<String, State> itemStates;

    public OpenHabItemsExtension(Map<String, State> itemStates) {
        super(OptionExtensionType.CUSTOM);
        this.itemStates = itemStates;
    }

    @Override
    public void extend(OptionsBuilder<? extends Options<?>, ?> builder) {
        builder.options().forEach(optionDef -> {
            if (shouldLoadValue(optionDef)) {
                loadAndSetValue(builder, optionDef);
            }
        });
    }

    private boolean shouldLoadValue(OptionDef optionDef) {
        // Load value for any option that has a corresponding item state
        // The annotation check is too restrictive because Options library
        // might pick up annotations from parent interfaces rather than overrides
        return true;
    }

    private void loadAndSetValue(OptionsBuilder<?, ?> builder, OptionDef optionDef) {
        String itemName = optionDef.name();
        State state = itemStates.get(itemName);

        if (state == null) {
            log.debug("No state found for item: {}", itemName);
            return;
        }

        Class<?> returnType = optionDef.method().getReturnType();
        Object value = convertState(state, returnType);

        // Log output items at INFO level for debugging
        if (itemName.startsWith("hrvOutput")) {
            log.info("Loaded output item {}: state={}, stateClass={}, converted={}",
                itemName, state, state.getClass().getSimpleName(), value);
        }

        if (value != null) {
            builder.setValue(optionDef, value);
        } else if (itemName.startsWith("hrvOutput")) {
            log.warn("Failed to convert output item {}: state={}, type={}", itemName, state, returnType);
        }
    }

    @SuppressWarnings("unchecked")
    private Object convertState(State state, Class<?> targetType) {
        String stateStr = state.toString();
        if (stateStr == null || stateStr.equals("NULL") || stateStr.equals("UNDEF")) {
            return null;
        }

        try {
            if (targetType == int.class || targetType == Integer.class) {
                // Parse as double first to handle "55.0" format, then convert to int
                return (int) Double.parseDouble(stateStr);
            } else if (targetType == long.class || targetType == Long.class) {
                return (long) Double.parseDouble(stateStr);
            } else if (targetType == float.class || targetType == Float.class) {
                return Float.parseFloat(stateStr);
            } else if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(stateStr);
            } else if (targetType == boolean.class || targetType == Boolean.class) {
                return stateStr.equalsIgnoreCase("ON") || stateStr.equalsIgnoreCase("true");
        } else if (targetType == String.class) {
            String str = state.toString();
            return (str != null && !str.equals("NULL") && !str.equals("UNDEF")) ? str : null;
        } else if (targetType.isEnum()) {
            String str = state.toString();
            if (str == null || str.equals("NULL") || str.equals("UNDEF")) {
                return null;
            }
            try {
                return Enum.valueOf((Class<Enum>) targetType, str.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid enum value '{}' for type {}", str, targetType.getSimpleName());
                return null;
            }
        } else {
            log.warn("Unsupported type for state conversion: {}", targetType);
            return null;
        }
    }

    private static boolean hasAnnotation(OptionDef optionDef, Class<? extends Annotation> annotationType) {
        return optionDef.annotations().stream()
            .anyMatch(annotationType::isInstance);
    }
}
