package io.github.fiserro.homehab.generator;

import io.github.fiserro.homehab.HabState;
import io.github.fiserro.homehab.OutputItem;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates OpenHAB output items file from @OutputItem annotated fields in HabState.
 */
@Slf4j
public class OutputItemsGenerator {

  private static final String HEADER = """
      // Auto-generated from HabState.java @OutputItem annotations
      // DO NOT EDIT - changes will be overwritten

      Group gOutputs "Outputs"

      """;

  public void generate(GeneratorOptions options) throws IOException {
    Path outputPath = Paths.get(options.outputDir(), "items", "output-items.items");
    log.info("Generating output items to: {}", outputPath);
    generate(outputPath);
    log.info("Generated output items to: {}", outputPath);
  }

  public static void generate(Path outputPath) throws IOException {
    StringBuilder content = new StringBuilder(HEADER);

    for (Field field : HabState.class.getDeclaredFields()) {
      if (field.isAnnotationPresent(OutputItem.class)) {
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

    return String.format("%s %s \"HRV - %s\" <%s> (gOutputs)%n",
        itemType, itemName, label, icon);
  }

  private static String getItemType(Class<?> type) {
    if (type == boolean.class) {
      return "Switch";
    } else if (type == int.class) {
      return "Dimmer";
    } else if (type == float.class) {
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
    if (lower.contains("power")) {
      return "energy";
    } else if (lower.contains("fan")) {
      return "fan";
    }
    return "settings";
  }
}
