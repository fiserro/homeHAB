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
     * Respects dualMotorMode setting for HRV outputs:
     * - dualMotorMode=false: sends only hrvOutputPower
     * - dualMotorMode=true: sends only hrvOutputIntake and hrvOutputExhaust
     *
     * @param events the OpenHAB script bus event for sending commands
     * @param state the Options instance containing output values
     * @param <T> the Options type
     */
    public static <T extends Options<T>> void writeState(ScriptBusEvent events, T state) {
        // Check if dualMotorMode is enabled (for HRV module compatibility)
        boolean dualMotorMode = getDualMotorMode(state);

        state.options().stream()
            .filter(opt -> hasAnnotation(opt, OutputItem.class))
            .filter(opt -> shouldSendOutput(opt.name(), dualMotorMode))
            .forEach(opt -> {
                Object value = state.getValue(opt);
                if (value != null) {
                    String command = formatValue(value);
                    log.debug("Sending command to {}: {}", opt.name(), command);
                    events.sendCommand(opt.name(), command);
                }
            });
    }

    /**
     * Checks if dualMotorMode is enabled in the state.
     * Returns false if the option is not present (for non-HRV modules).
     */
    private static <T extends Options<T>> boolean getDualMotorMode(T state) {
        return state.options().stream()
            .filter(opt -> "dualMotorMode".equals(opt.name()))
            .findFirst()
            .map(opt -> {
                Object value = state.getValue(opt);
                return value instanceof Boolean && (Boolean) value;
            })
            .orElse(false);
    }

    /**
     * Determines if an output should be sent based on motor mode.
     * - In single motor mode: send only hrvOutputPower
     * - In dual motor mode: send only hrvOutputIntake and hrvOutputExhaust
     * - For other outputs: always send
     */
    private static boolean shouldSendOutput(String outputName, boolean dualMotorMode) {
        if (dualMotorMode) {
            // Dual motor mode: skip hrvOutputPower, send intake and exhaust
            return !"hrvOutputPower".equals(outputName);
        } else {
            // Single motor mode: send hrvOutputPower, skip intake and exhaust
            return !"hrvOutputIntake".equals(outputName) && !"hrvOutputExhaust".equals(outputName);
        }
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
