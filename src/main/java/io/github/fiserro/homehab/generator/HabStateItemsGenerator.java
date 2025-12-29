package io.github.fiserro.homehab.generator;

import io.github.fiserro.homehab.BooleanAggregation;
import io.github.fiserro.homehab.InputItem;
import io.github.fiserro.homehab.MqttItem;
import io.github.fiserro.homehab.NumericAggregation;
import io.github.fiserro.homehab.OutputItem;
import io.github.fiserro.homehab.ReadOnlyItem;
import io.github.fiserro.homehab.module.CommonModule;
import io.github.fiserro.homehab.module.FlowerModule;
import io.github.fiserro.homehab.module.HrvModule;
import io.github.fiserro.options.Option;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
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

    public void generate(GeneratorOptions options) throws IOException {
        Path outputPath = Paths.get(options.outputDir(), "items", "habstate-items.items");
        log.info("Generating items to: {}", outputPath);
        generate(outputPath);
        log.info("Generated items to: {}", outputPath);
    }

    public static void generate(Path outputPath) throws IOException {
        StringBuilder content = new StringBuilder(HEADER);
        Set<String> processedMethods = new HashSet<>();

        // Process all module interfaces for input/output/readonly items
        processModule(CommonModule.class, content, processedMethods);
        processModule(HrvModule.class, content, processedMethods);
        processModule(FlowerModule.class, content, processedMethods);

        // Process HabState for aggregation groups (MqttItem with aggregation)
        processHabStateAggregations(content, processedMethods);

        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, content.toString());
    }

    private static void processModule(Class<?> moduleClass, StringBuilder content,
            Set<String> processedMethods) {

        // Sort methods by name for deterministic output
        Method[] sortedMethods = Arrays.stream(moduleClass.getDeclaredMethods())
                .sorted(Comparator.comparing(Method::getName))
                .toArray(Method[]::new);

        // Generate input items
        content.append(String.format("%n// Input items from %s%n", moduleClass.getSimpleName()));
        for (Method method : sortedMethods) {
            if (processedMethods.contains(method.getName())) continue;
            if (!method.isAnnotationPresent(Option.class)) continue;
            if (method.isAnnotationPresent(InputItem.class)) {
                content.append(generateInputItem(method));
                processedMethods.add(method.getName());
            }
        }

        // Generate output items
        content.append(String.format("%n// Output items from %s%n", moduleClass.getSimpleName()));
        for (Method method : sortedMethods) {
            if (processedMethods.contains(method.getName())) continue;
            if (!method.isAnnotationPresent(Option.class)) continue;
            if (method.isAnnotationPresent(OutputItem.class)) {
                content.append(generateOutputItem(method));
                processedMethods.add(method.getName());
            }
        }

        // Generate read-only items
        content.append(String.format("%n// Read-only items from %s%n", moduleClass.getSimpleName()));
        for (Method method : sortedMethods) {
            if (processedMethods.contains(method.getName())) continue;
            if (!method.isAnnotationPresent(Option.class)) continue;
            if (method.isAnnotationPresent(ReadOnlyItem.class)) {
                content.append(generateReadOnlyItem(method));
                processedMethods.add(method.getName());
            }
        }
    }

    /**
     * Process HabState interface (from openhab-dev) for aggregation groups.
     * Aggregations are defined via @MqttItem(numAgg=...) or @MqttItem(boolAgg=...).
     */
    private static void processHabStateAggregations(StringBuilder content, Set<String> processedMethods) {
        content.append("\n// Aggregation groups from HabState @MqttItem annotations\n");

        try {
            // Try to load HabState from the default package
            Class<?> habStateClass = Class.forName("HabState");

            // Sort methods by name for deterministic output
            Method[] sortedMethods = Arrays.stream(habStateClass.getDeclaredMethods())
                    .sorted(Comparator.comparing(Method::getName))
                    .toArray(Method[]::new);

            for (Method method : sortedMethods) {
                if (processedMethods.contains(method.getName())) continue;

                MqttItem mqttItem = method.getAnnotation(MqttItem.class);
                if (mqttItem == null) continue;

                String groupDef = generateGroupItemFromMqtt(method, mqttItem);
                if (groupDef != null) {
                    content.append(groupDef);
                    processedMethods.add(method.getName());
                }
            }
        } catch (ClassNotFoundException e) {
            log.warn("HabState class not found - aggregation groups will not be generated. " +
                    "This is expected when running outside OpenHAB environment.");
        }
    }

    private static String generateGroupItemFromMqtt(Method method, MqttItem mqttItem) {
        String fieldName = method.getName();
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
        if (lower.contains("temperature")) return "temperature";
        if (lower.contains("humidity")) return "humidity";
        if (lower.contains("pressure")) return "pressure";
        if (lower.contains("co2")) return "carbondioxide";
        if (lower.contains("smoke")) return "smoke";
        if (lower.contains("gas")) return "gas";
        if (lower.contains("contact")) return "contact";
        if (lower.contains("window")) return "window";
        return "none";
    }

    private static String generateInputItem(Method method) {
        String itemName = method.getName();
        Class<?> returnType = method.getReturnType();
        String itemType = getInputItemType(returnType);
        String label = formatLabel(itemName);
        String icon = getInputIcon(itemName);
        String defaultValue = getDefaultValue(method);

        // Get channels from both @InputItem and @OutputItem (some items need both)
        InputItem inputAnnotation = method.getAnnotation(InputItem.class);
        OutputItem outputAnnotation = method.getAnnotation(OutputItem.class);
        String inputChannel = inputAnnotation != null ? inputAnnotation.channel() : "";
        String outputChannel = outputAnnotation != null ? outputAnnotation.channel() : "";

        // Combine channels if both exist, otherwise use whichever is available
        String channelBinding;
        if (!inputChannel.isEmpty() && !outputChannel.isEmpty()) {
            channelBinding = String.format(" { channel=\"%s\", channel=\"%s\" }", inputChannel, outputChannel);
        } else if (!inputChannel.isEmpty()) {
            channelBinding = String.format(" { channel=\"%s\" }", inputChannel);
        } else if (!outputChannel.isEmpty()) {
            channelBinding = String.format(" { channel=\"%s\" }", outputChannel);
        } else {
            channelBinding = "";
        }

        return String.format("%s %s \"HRV - %s\" <%s> %s%s  // default: %s%n",
            itemType, itemName, label, icon, INPUT_TAGS, channelBinding, defaultValue);
    }

    private static String generateOutputItem(Method method) {
        String itemName = method.getName();
        Class<?> returnType = method.getReturnType();
        String itemType = getOutputItemType(returnType);
        String label = formatLabel(itemName);
        String icon = getOutputIcon(itemName);

        OutputItem annotation = method.getAnnotation(OutputItem.class);
        String channel = annotation != null ? annotation.channel() : "";
        String channelBinding = channel.isEmpty() ? "" : String.format(" { channel=\"%s\" }", channel);

        return String.format("%s %s \"HRV - %s\" <%s> %s%s%n",
            itemType, itemName, label, icon, OUTPUT_TAGS, channelBinding);
    }

    private static String generateReadOnlyItem(Method method) {
        String itemName = method.getName();
        Class<?> returnType = method.getReturnType();
        String itemType = getReadOnlyItemType(returnType);
        String label = formatLabel(itemName);
        String icon = getReadOnlyIcon(itemName);

        // Get channel from module annotation first
        ReadOnlyItem annotation = method.getAnnotation(ReadOnlyItem.class);
        String channel = annotation != null ? annotation.channel() : "";

        // Check if HabState has an override with channel (HabState may define channels)
        if (channel.isEmpty()) {
            channel = getChannelFromHabState(itemName);
        }

        String channelBinding = channel.isEmpty() ? "" : String.format(" { channel=\"%s\" }", channel);

        return String.format("%s %s \"HRV - %s\" <%s> %s%s%n",
            itemType, itemName, label, icon, READONLY_TAGS, channelBinding);
    }

    /**
     * Get channel binding from HabState override if available.
     */
    private static String getChannelFromHabState(String methodName) {
        try {
            Class<?> habStateClass = Class.forName("HabState");
            Method method = habStateClass.getDeclaredMethod(methodName);

            // Check for @ReadOnlyItem with channel
            ReadOnlyItem readOnlyItem = method.getAnnotation(ReadOnlyItem.class);
            if (readOnlyItem != null && !readOnlyItem.channel().isEmpty()) {
                return readOnlyItem.channel();
            }

            // Check for @OutputItem with channel
            OutputItem outputItem = method.getAnnotation(OutputItem.class);
            if (outputItem != null && !outputItem.channel().isEmpty()) {
                return outputItem.channel();
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // HabState not available or method not found - this is ok
        }
        return "";
    }

    private static String getReadOnlyItemType(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return "Switch";
        } else if (type == int.class || type == Integer.class ||
                   type == long.class || type == Long.class ||
                   type == float.class || type == Float.class ||
                   type == double.class || type == Double.class) {
            return "Number";
        } else if (type == String.class) {
            return "String";
        }
        throw new IllegalArgumentException("Unsupported read-only type: " + type);
    }

    private static String getReadOnlyIcon(String fieldName) {
        String lower = fieldName.toLowerCase();
        if (lower.contains("time") || lower.contains("off")) {
            return "time";
        } else if (lower.contains("temperature")) {
            return "temperature";
        }
        return "none";
    }

    private static String getInputItemType(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return "Switch";
        } else if (type == int.class || type == Integer.class ||
                   type == float.class || type == Float.class ||
                   type == double.class || type == Double.class) {
            return "Number";
        } else if (type == String.class) {
            return "String";
        } else if (type.isEnum()) {
            return "String";
        }
        throw new IllegalArgumentException("Unsupported input type: " + type);
    }

    private static String getOutputItemType(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return "Switch";
        } else if (type == int.class || type == Integer.class) {
            return "Dimmer";
        } else if (type == float.class || type == Float.class ||
                   type == double.class || type == Double.class) {
            return "Number";
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
        if (lower.contains("mode")) {
            return "switch";
        } else if (lower.contains("power") || lower.contains("threshold")) {
            return "energy";
        } else if (lower.contains("duration") || lower.contains("time")) {
            return "time";
        } else if (lower.contains("co2") || lower.contains("humidity")) {
            return "line";
        }
        return "settings";
    }

    private static String getOutputIcon(String fieldName) {
        String lower = fieldName.toLowerCase();
        if (lower.contains("power")) {
            return "energy";
        } else if (lower.contains("fan")) {
            return "fan";
        }
        return "settings";
    }

    private static String getDefaultValue(Method method) {
        try {
            if (method.isDefault()) {
                // For default methods, we need to invoke them on a proxy
                // For simplicity, just return the literal default from the method body
                // This is a limitation - we can't easily invoke default methods without an instance
                return "?";
            }
            return "?";
        } catch (Exception e) {
            return "?";
        }
    }
}
