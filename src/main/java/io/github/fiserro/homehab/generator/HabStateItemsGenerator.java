package io.github.fiserro.homehab.generator;

import io.github.fiserro.homehab.BoolAgg;
import io.github.fiserro.homehab.BooleanAggregation;
import io.github.fiserro.homehab.HabState;
import io.github.fiserro.homehab.InputItem;
import io.github.fiserro.homehab.NumAgg;
import io.github.fiserro.homehab.NumericAggregation;
import io.github.fiserro.homehab.OutputItem;
import io.github.fiserro.homehab.ReadOnlyItem;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates OpenHAB items from {@link HabState} field annotations.
 *
 * <p>This generator creates OpenHAB items based on annotations in the {@link HabState} class:
 *
 * <ul>
 *   <li>{@link InputItem} - generates input items (switches, numbers) for HRV control parameters
 *   <li>{@link OutputItem} - generates output items (dimmer, switch) for HRV outputs
 *   <li>{@link ReadOnlyItem} - generates read-only items (managed by system, not UI)
 *   <li>{@link NumAgg} - generates aggregation groups for numeric values (e.g., humidity, temperature)
 *   <li>{@link BoolAgg} - generates aggregation groups for boolean values (e.g., smoke detectors)
 * </ul>
 *
 * <p>The generated groups are empty - Zigbee device items should be manually assigned to these
 * groups based on the desired aggregation behavior.
 *
 * <p>Output file: {@code items/habstate-items.items}
 *
 * @see HabState
 * @see InputItem
 * @see OutputItem
 * @see ReadOnlyItem
 * @see NumAgg
 * @see BoolAgg
 */
@Slf4j
public class HabStateItemsGenerator {

  private static final String HEADER = """
      // Auto-generated from HabState.java annotations
      // DO NOT EDIT - changes will be overwritten
      //
      // This file contains:
      // - Input items from @InputItem annotations (HRV control parameters)
      // - Output items from @OutputItem annotations (HRV outputs)
      // - Read-only items from @ReadOnlyItem annotations (system-managed values)
      // - Aggregation groups from @NumAgg and @BoolAgg annotations
      //
      // Assign MQTT device items to aggregation groups manually.

      """;

  public void generate(GeneratorOptions options) throws IOException {
    Path outputPath = Paths.get(options.outputDir(), "items", "habstate-items.items");
    log.info("Generating HabState items to: {}", outputPath);
    generate(outputPath);
    log.info("Generated HabState items to: {}", outputPath);
  }

  public static void generate(Path outputPath) throws IOException {
    StringBuilder content = new StringBuilder(HEADER);

    // Generate input items
    content.append("// Input items from @InputItem annotations\n");
    for (Field field : HabState.class.getDeclaredFields()) {
      if (field.isAnnotationPresent(InputItem.class)) {
        content.append(generateInputItem(field));
      }
    }

    // Generate output items
    content.append("\n// Output items from @OutputItem annotations\n");
    for (Field field : HabState.class.getDeclaredFields()) {
      if (field.isAnnotationPresent(OutputItem.class)) {
        content.append(generateOutputItem(field));
      }
    }

    // Generate read-only items
    content.append("\n// Read-only items from @ReadOnlyItem annotations (system-managed)\n");
    for (Field field : HabState.class.getDeclaredFields()) {
      if (field.isAnnotationPresent(ReadOnlyItem.class)) {
        content.append(generateReadOnlyItem(field));
      }
    }

    // Generate group items from @NumAgg and @BoolAgg annotations
    content.append("\n// Aggregation groups from @NumAgg and @BoolAgg annotations\n");
    content.append("// Assign Zigbee items to these groups manually\n");
    for (Field field : HabState.class.getDeclaredFields()) {
      String groupDef = generateGroupItem(field);
      if (groupDef != null) {
        content.append(groupDef);
      }
    }

    Files.writeString(outputPath, content.toString());
  }

  private static final String AGG_TAGS = "[\"mqtt\", \"zigbee\", \"computed\"]";
  private static final String INPUT_TAGS = "[\"user\"]";
  private static final String OUTPUT_TAGS = "[\"computed\"]";
  private static final String READONLY_TAGS = "[\"readonly\", \"computed\"]";

  private static String generateGroupItem(Field field) {
    NumAgg numAgg = field.getAnnotation(NumAgg.class);
    BoolAgg boolAgg = field.getAnnotation(BoolAgg.class);

    if (numAgg != null) {
      String fieldName = field.getName();
      String aggFunc = switch (numAgg.value()) {
        case MAX -> "MAX";
        case MIN -> "MIN";
        case AVG -> "AVG";
        case SUM -> "SUM";
        case COUNT -> "COUNT";
      };
      String icon = getIconForGroup(fieldName);
      String label = formatLabel(fieldName);
      return String.format("Group:Number:%s %s \"%s\" <%s> %s%n", aggFunc, fieldName, label, icon, AGG_TAGS);
    } else if (boolAgg != null) {
      String fieldName = field.getName();
      String aggFunc = switch (boolAgg.value()) {
        case OR -> "OR(ON,OFF)";
        case AND -> "AND(ON,OFF)";
      };
      String icon = getIconForGroup(fieldName);
      String label = formatLabel(fieldName);
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

  private static String generateInputItem(Field field) {
    String itemName = field.getName();
    String itemType = getInputItemType(field.getType());
    String label = formatLabel(itemName);
    String icon = getInputIcon(itemName);
    String defaultValue = getDefaultValue(field);

    return String.format("%s %s \"HRV - %s\" <%s> %s  // default: %s%n",
        itemType, itemName, label, icon, INPUT_TAGS, defaultValue);
  }

  private static String generateOutputItem(Field field) {
    String itemName = field.getName();
    String itemType = getOutputItemType(field.getType());
    String label = formatLabel(itemName);
    String icon = getOutputIcon(itemName);

    return String.format("%s %s \"HRV - %s\" <%s> %s%n",
        itemType, itemName, label, icon, OUTPUT_TAGS);
  }

  private static String generateReadOnlyItem(Field field) {
    String itemName = field.getName();
    String itemType = getReadOnlyItemType(field.getType());
    String label = formatLabel(itemName);
    String icon = getReadOnlyIcon(itemName);

    return String.format("%s %s \"HRV - %s\" <%s> %s%n",
        itemType, itemName, label, icon, READONLY_TAGS);
  }

  private static String getReadOnlyItemType(Class<?> type) {
    if (type == boolean.class) {
      return "Switch";
    } else if (type == int.class || type == long.class || type == float.class) {
      return "Number";
    }
    throw new IllegalArgumentException("Unsupported read-only type: " + type);
  }

  private static String getReadOnlyIcon(String fieldName) {
    String lower = fieldName.toLowerCase();
    if (lower.contains("time") || lower.contains("off")) {
      return "time";
    }
    return "none";
  }

  private static String getInputItemType(Class<?> type) {
    if (type == boolean.class) {
      return "Switch";
    } else if (type == int.class || type == float.class) {
      return "Number";
    }
    throw new IllegalArgumentException("Unsupported input type: " + type);
  }

  private static String getOutputItemType(Class<?> type) {
    if (type == boolean.class) {
      return "Switch";
    } else if (type == int.class) {
      return "Dimmer";
    } else if (type == float.class) {
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

  private static String getDefaultValue(Field field) {
    try {
      HabState defaultState = HabState.builder().build();
      field.setAccessible(true);
      Object value = field.get(defaultState);
      if (value instanceof Boolean) {
        return (Boolean) value ? "ON" : "OFF";
      }
      return String.valueOf(value);
    } catch (Exception e) {
      return "?";
    }
  }
}