package io.github.fiserro.homehab.generator;

import io.github.fiserro.homehab.BooleanAggregation;
import io.github.fiserro.homehab.InputItem;
import io.github.fiserro.homehab.MqttItem;
import io.github.fiserro.homehab.NumericAggregation;
import io.github.fiserro.homehab.OutputItem;
import io.github.fiserro.homehab.ReadOnlyItem;
import io.github.fiserro.homehab.module.HabState;
import io.github.fiserro.options.OptionDef;
import io.github.fiserro.options.OptionsFactory;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates OpenHAB items from module interface annotations.
 *
 * <p>This generator creates OpenHAB items based on annotations in the module interfaces:
 *
 * <ul>
 *   <li>{@link InputItem} - generates input items (switches, numbers) for control parameters
 *   <li>{@link OutputItem} - generates output items (dimmer, switch) for computed outputs
 *   <li>{@link ReadOnlyItem} - generates read-only items (managed by system, not UI)
 *   <li>{@link MqttItem} with numAgg/boolAgg - generates aggregation groups for sensor values
 * </ul>
 *
 * <p>Output file: {@code items/habstate-items.items}
 */
@Slf4j
public class HabStateItemsGenerator {

    private static final String HEADER = """
        // Auto-generated from module interface annotations
        // DO NOT EDIT - changes will be overwritten
        //
        // This file contains:
        // - Input items from @InputItem annotations (control parameters)
        // - Output items from @OutputItem annotations (computed outputs)
        // - Read-only items from @ReadOnlyItem annotations (system-managed values)
        // - Aggregation groups from @MqttItem annotations with numAgg/boolAgg
        //
        // Assign MQTT device items to aggregation groups manually.

        """;

    private static final String AGG_TAGS = "[\"mqtt\", \"zigbee\", \"computed\"]";
    private static final String INPUT_TAGS = "[\"user\"]";
    private static final String OUTPUT_TAGS = "[\"computed\"]";
    private static final String READONLY_TAGS = "[\"readonly\", \"computed\"]";
    private static final String CHANNEL_FMT = " { channel=\"%s\" }";
    private static final String CHANNEL_FMT_2 = " { channel=\"%s\", channel=\"%s\" }";

    // OpenHAB item types
    private static final String TYPE_STRING = "String";
    private static final String TYPE_NUMBER = "Number";
    private static final String TYPE_SWITCH = "Switch";
    private static final String TYPE_DIMMER = "Dimmer";

    // Field/icon names
    private static final String POWER = "power";
    private static final String ENERGY = "energy";
    private static final String TEMPERATURE = "temperature";
    private static final String HUMIDITY = "humidity";
    private static final String PRESSURE = "pressure";
    private static final String CO2 = "co2";
    private static final String CARBONDIOXIDE = "carbondioxide";
    private static final String SMOKE = "smoke";
    private static final String GAS = "gas";
    private static final String CONTACT = "contact";
    private static final String WINDOW = "window";
    private static final String TIME = "time";
    private static final String MODE = "mode";
    private static final String THRESHOLD = "threshold";
    private static final String DURATION = "duration";
    private static final String FAN = "fan";
    private static final String ICON_SWITCH = "switch";
    private static final String ICON_LINE = "line";
    private static final String ICON_SETTINGS = "settings";
    private static final String ICON_NONE = "none";

    public void generate(GeneratorOptions options) throws IOException {
        Path outputPath = Paths.get(options.outputDir(), "items", "habstate-items.items");
        log.info("Generating items to: {}", outputPath);
        generate(outputPath);
        log.info("Generated items to: {}", outputPath);
    }

    public static void generate(Path outputPath) throws IOException {
        StringBuilder content = new StringBuilder(HEADER);
        Set<String> processedMethods = new HashSet<>();

        // Create single HabModules instance with default values
        HabState habState = OptionsFactory.create(HabState.class);

        // Get all options sorted by name for deterministic output
        List<OptionDef> allOptions = habState.options().stream()
                .sorted(Comparator.comparing(OptionDef::name))
                .toList();

        // Generate input items
        content.append("\n// Input items from HabModules\n");
        for (OptionDef opt : allOptions) {
            if (processedMethods.contains(opt.name())) continue;
            if (hasAnnotation(opt, InputItem.class)) {
                content.append(generateInputItem(opt, habState));
                processedMethods.add(opt.name());
            }
        }

        // Generate output items
        content.append("\n// Output items from HabModules\n");
        for (OptionDef opt : allOptions) {
            if (processedMethods.contains(opt.name())) continue;
            if (hasAnnotation(opt, OutputItem.class)) {
                content.append(generateOutputItem(opt));
                processedMethods.add(opt.name());
            }
        }

        // Generate read-only items
        content.append("\n// Read-only items from HabModules\n");
        for (OptionDef opt : allOptions) {
            if (processedMethods.contains(opt.name())) continue;
            if (hasAnnotation(opt, ReadOnlyItem.class)) {
                content.append(generateReadOnlyItem(opt));
                processedMethods.add(opt.name());
            }
        }

        // Process aggregation groups (MqttItem with aggregation)
        processAggregations(content, processedMethods);

        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, content.toString());
    }

    private static boolean hasAnnotation(OptionDef opt, Class<? extends Annotation> annotationType) {
        return opt.annotations().stream().anyMatch(annotationType::isInstance);
    }

    private static <A extends Annotation> A getAnnotation(OptionDef opt, Class<A> annotationType) {
        return opt.annotations().stream()
                .filter(annotationType::isInstance)
                .map(annotationType::cast)
                .findFirst()
                .orElse(null);
    }

    /**
     * Process aggregation groups from @MqttItem annotations.
     * Uses reflection on HabState.class.getDeclaredMethods() because OptionDef.annotations()
     * only sees annotations from the declaring method, not from overriding methods.
     */
    private static void processAggregations(StringBuilder content, Set<String> processedMethods) {
        content.append("\n// Aggregation groups from @MqttItem annotations\n");

        for (Method method : HabState.class.getDeclaredMethods()) {
            MqttItem mqttItem = method.getAnnotation(MqttItem.class);
            String methodName = method.getName();

            if (mqttItem == null || processedMethods.contains(methodName)) {
                continue;
            }

            String groupDef = generateGroupItemFromMqtt(methodName, mqttItem);
            if (groupDef != null) {
                content.append(groupDef);
                processedMethods.add(methodName);
            }
        }
    }

    private static String generateGroupItemFromMqtt(String fieldName, MqttItem mqttItem) {
        String icon = getIconForGroup(fieldName);
        String label = formatLabel(fieldName);

        NumericAggregation numAgg = mqttItem.numAgg();
        BooleanAggregation boolAgg = mqttItem.boolAgg();

        if (numAgg != NumericAggregation.NONE) {
            String aggFunc = switch (numAgg) {
                case MAX -> "MAX";
                case MIN -> "MIN";
                case AVG -> "AVG";
                case SUM -> "SUM";
                case COUNT -> "COUNT";
                case NONE -> throw new IllegalStateException("NONE should not reach here");
            };
            return String.format("Group:Number:%s %s \"%s\" <%s> %s%n", aggFunc, fieldName, label, icon, AGG_TAGS);
        } else if (boolAgg != BooleanAggregation.NONE) {
            String aggFunc = switch (boolAgg) {
                case OR -> "OR(ON,OFF)";
                case AND -> "AND(ON,OFF)";
                case NONE -> throw new IllegalStateException("NONE should not reach here");
            };
            return String.format("Group:Switch:%s %s \"%s\" <%s> %s%n", aggFunc, fieldName, label, icon, AGG_TAGS);
        }

        return null;
    }

    private static String getIconForGroup(String fieldName) {
        String lower = fieldName.toLowerCase();
        if (lower.contains(TEMPERATURE)) return TEMPERATURE;
        if (lower.contains(HUMIDITY)) return HUMIDITY;
        if (lower.contains(PRESSURE)) return PRESSURE;
        if (lower.contains(CO2)) return CARBONDIOXIDE;
        if (lower.contains(SMOKE)) return SMOKE;
        if (lower.contains(GAS)) return GAS;
        if (lower.contains(CONTACT)) return CONTACT;
        if (lower.contains(WINDOW)) return WINDOW;
        return ICON_NONE;
    }

    private static String generateInputItem(OptionDef opt, HabState habState) {
        String itemName = opt.name();
        Class<?> returnType = opt.method().getReturnType();
        String itemType = getInputItemType(returnType);
        String label = formatLabel(itemName);
        String icon = getInputIcon(itemName);

        // Get actual default value from instance
        Object defaultValue = habState.getValue(opt);
        String defaultValueStr = defaultValue != null ? defaultValue.toString() : "?";

        // Get channels from both @InputItem and @OutputItem (some items need both)
        InputItem inputAnnotation = getAnnotation(opt, InputItem.class);
        OutputItem outputAnnotation = getAnnotation(opt, OutputItem.class);
        String inputChannel = inputAnnotation != null ? inputAnnotation.channel() : "";
        String outputChannel = outputAnnotation != null ? outputAnnotation.channel() : "";

        // Combine channels if both exist, otherwise use whichever is available
        String channelBinding = buildChannelBinding(inputChannel, outputChannel);

        return String.format("%s %s \"HRV - %s\" <%s> %s%s  // default: %s%n",
            itemType, itemName, label, icon, INPUT_TAGS, channelBinding, defaultValueStr);
    }

    private static String generateOutputItem(OptionDef opt) {
        String itemName = opt.name();
        Class<?> returnType = opt.method().getReturnType();
        String itemType = getOutputItemType(returnType);
        String label = formatLabel(itemName);
        String icon = getOutputIcon(itemName);

        OutputItem annotation = getAnnotation(opt, OutputItem.class);
        String channel = annotation != null ? annotation.channel() : "";
        String channelBinding = channel.isEmpty() ? "" : String.format(CHANNEL_FMT, channel);

        return String.format("%s %s \"HRV - %s\" <%s> %s%s%n",
            itemType, itemName, label, icon, OUTPUT_TAGS, channelBinding);
    }

    private static String generateReadOnlyItem(OptionDef opt) {
        String itemName = opt.name();
        Class<?> returnType = opt.method().getReturnType();
        String itemType = getReadOnlyItemType(returnType);
        String label = formatLabel(itemName);
        String icon = getReadOnlyIcon(itemName);

        ReadOnlyItem annotation = getAnnotation(opt, ReadOnlyItem.class);
        String channel = annotation != null ? annotation.channel() : "";
        String channelBinding = channel.isEmpty() ? "" : String.format(CHANNEL_FMT, channel);

        return String.format("%s %s \"HRV - %s\" <%s> %s%s%n",
            itemType, itemName, label, icon, READONLY_TAGS, channelBinding);
    }

    private static String buildChannelBinding(String inputChannel, String outputChannel) {
        if (!inputChannel.isEmpty() && !outputChannel.isEmpty()) {
            return String.format(CHANNEL_FMT_2, inputChannel, outputChannel);
        } else if (!inputChannel.isEmpty()) {
            return String.format(CHANNEL_FMT, inputChannel);
        } else if (!outputChannel.isEmpty()) {
            return String.format(CHANNEL_FMT, outputChannel);
        }
        return "";
    }


    private static String getReadOnlyItemType(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return TYPE_SWITCH;
        } else if (type == int.class || type == Integer.class ||
                   type == long.class || type == Long.class ||
                   type == float.class || type == Float.class ||
                   type == double.class || type == Double.class) {
            return TYPE_NUMBER;
        } else if (type == String.class) {
            return TYPE_STRING;
        }
        throw new IllegalArgumentException("Unsupported read-only type: " + type);
    }

    private static String getReadOnlyIcon(String fieldName) {
        String lower = fieldName.toLowerCase();
        if (lower.contains(TIME) || lower.contains("off")) {
            return TIME;
        } else if (lower.contains(TEMPERATURE)) {
            return TEMPERATURE;
        } else if (lower.contains(POWER)) {
            return ENERGY;
        }
        return ICON_NONE;
    }

    private static String getInputItemType(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return TYPE_SWITCH;
        } else if (type == int.class || type == Integer.class ||
                   type == float.class || type == Float.class ||
                   type == double.class || type == Double.class) {
            return TYPE_NUMBER;
        } else if (type == String.class) {
            return TYPE_STRING;
        } else if (type.isEnum()) {
            return TYPE_STRING;
        }
        throw new IllegalArgumentException("Unsupported input type: " + type);
    }

    private static String getOutputItemType(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return TYPE_SWITCH;
        } else if (type == int.class || type == Integer.class) {
            return TYPE_DIMMER;
        } else if (type == float.class || type == Float.class ||
                   type == double.class || type == Double.class) {
            return TYPE_NUMBER;
        }
        throw new IllegalArgumentException("Unsupported output type: " + type);
    }

    private static String formatLabel(String fieldName) {
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                label.append(' ');
            }
            if (i == 0) {
                label.append(Character.toUpperCase(c));
            } else {
                label.append(c);
            }
        }
        return label.toString();
    }

    private static String getInputIcon(String fieldName) {
        String lower = fieldName.toLowerCase();
        if (lower.contains(MODE)) {
            return ICON_SWITCH;
        } else if (lower.contains(POWER) || lower.contains(THRESHOLD)) {
            return ENERGY;
        } else if (lower.contains(DURATION) || lower.contains(TIME)) {
            return TIME;
        } else if (lower.contains(CO2) || lower.contains(HUMIDITY)) {
            return ICON_LINE;
        }
        return ICON_SETTINGS;
    }

    private static String getOutputIcon(String fieldName) {
        String lower = fieldName.toLowerCase();
        if (lower.contains(POWER)) {
            return ENERGY;
        } else if (lower.contains(FAN)) {
            return FAN;
        }
        return ICON_SETTINGS;
    }
}
