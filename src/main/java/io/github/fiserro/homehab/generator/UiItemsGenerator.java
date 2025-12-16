package io.github.fiserro.homehab.generator;

import io.github.fiserro.homehab.HabState;
import io.github.fiserro.homehab.InputItem;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates OpenHAB items file from @InputItem annotated fields in HabState.
 */
@Slf4j
public class UiItemsGenerator {

  private static final String HEADER = """
      // Auto-generated from HabState.java @InputItem annotations
      // DO NOT EDIT - changes will be overwritten

      Group gHrvInputs "HRV Inputs"

      """;

  public void generate(GeneratorOptions options) throws IOException {
    Path outputPath = Paths.get(options.outputDir(), "items", "ui-items.items");
    log.info("Generating UI items to: {}", outputPath);
    generate(outputPath);
    log.info("Generated UI items to: {}", outputPath);
  }

  public static void generate(Path outputPath) throws IOException {
    StringBuilder content = new StringBuilder(HEADER);

    for (Field field : HabState.class.getDeclaredFields()) {
      if (field.isAnnotationPresent(InputItem.class)) {
        content.append(generateItem(field));
      }
    }

    Files.writeString(outputPath, content.toString());
  }

  private static String generateItem(Field field) {
    String itemName = field.getName();
    String itemType = getItemType(field.getType());
    String label = formatLabel(itemName);
    String icon = getIcon(itemName);
    String defaultValue = getDefaultValue(field);

    return String.format("%s %s \"HRV - %s\" <%s> (gHrvInputs)  // default: %s%n",
        itemType, itemName, label, icon, defaultValue);
  }

  private static String getItemType(Class<?> type) {
    if (type == boolean.class) {
      return "Switch";
    } else if (type == int.class || type == float.class) {
      return "Number";
    }
    throw new IllegalArgumentException("Unsupported type: " + type);
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

  private static String getIcon(String fieldName) {
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
