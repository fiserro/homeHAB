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
        // Skip if value is already set in the builder (e.g., from withValue call)
        if (builder.getValue(optionDef) != null) {
            log.debug("Value already set for item: {}", optionDef.name());
            return;
        }

        String itemName = optionDef.name();
        State state = itemStates.get(itemName);

        if (state == null) {
            log.debug("No state found for item: {}", itemName);
            return;
        }

        Class<?> returnType = optionDef.method().getReturnType();
        Object value = convertState(state, returnType);

        if (value != null) {
            builder.setValue(optionDef, value);
        }
    }

    private Object convertState(State state, Class<?> targetType) {
        if (targetType == int.class || targetType == Integer.class) {
            DecimalType decimal = state.as(DecimalType.class);
            return decimal != null ? decimal.intValue() : null;
        } else if (targetType == long.class || targetType == Long.class) {
            DecimalType decimal = state.as(DecimalType.class);
            return decimal != null ? decimal.longValue() : null;
        } else if (targetType == float.class || targetType == Float.class) {
            DecimalType decimal = state.as(DecimalType.class);
            return decimal != null ? decimal.floatValue() : null;
        } else if (targetType == double.class || targetType == Double.class) {
            DecimalType decimal = state.as(DecimalType.class);
            return decimal != null ? decimal.doubleValue() : null;
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            OnOffType onOff = state.as(OnOffType.class);
            return onOff != null ? onOff == OnOffType.ON : null;
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
