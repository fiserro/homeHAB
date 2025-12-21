package io.github.fiserro.homehab;

import io.github.fiserro.homehab.openhab.OpenHabItemsExtension;
import io.github.fiserro.options.OptionDef;
import io.github.fiserro.options.Options;
import io.github.fiserro.options.OptionsFactory;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.types.State;

/**
 * Factory for creating and writing Options-based state from/to OpenHAB items.
 *
 * <p>Reads values from:
 * <ul>
 *   <li>{@link InputItem} - individual items (user settings)</li>
 *   <li>{@link MqttItem} - group items with aggregated values (OpenHAB aggregates automatically)</li>
 * </ul>
 *
 * <p>Writes values to:
 * <ul>
 *   <li>{@link OutputItem} - individual items (computed outputs)</li>
 * </ul>
 */
@Slf4j
public class HabStateFactory {

    /**
     * Creates an Options instance from OpenHAB item states.
     *
     * @param optionsClass the Options interface class
     * @param itemStates map of item names to their current states
     * @param <T> the Options type
     * @return new Options instance populated with values from OpenHAB
     */
    public static <T extends Options<T>> T of(Class<T> optionsClass, Map<String, State> itemStates) {
        return OptionsFactory.create(
            optionsClass,
            List.of(new OpenHabItemsExtension(itemStates))
        );
    }

    /**
     * Writes output values from Options instance to OpenHAB items.
     *
     * @param events the OpenHAB script bus event for sending commands
     * @param state the Options instance containing output values
     * @param <T> the Options type
     */
    public static <T extends Options<T>> void writeState(ScriptBusEvent events, T state) {
        state.options().stream()
            .filter(opt -> hasAnnotation(opt, OutputItem.class))
            .forEach(opt -> {
                Object value = state.getValue(opt);
                if (value != null) {
                    String command = formatValue(value);
                    log.debug("Sending command to {}: {}", opt.name(), command);
                    events.sendCommand(opt.name(), command);
                }
            });
    }

    private static boolean hasAnnotation(OptionDef optionDef, Class<? extends Annotation> annotationType) {
        return optionDef.annotations().stream()
            .anyMatch(annotationType::isInstance);
    }

    private static String formatValue(Object value) {
        return switch (value) {
            case Boolean b -> b ? "ON" : "OFF";
            case Number n -> String.valueOf(n);
            default -> String.valueOf(value);
        };
    }
}
