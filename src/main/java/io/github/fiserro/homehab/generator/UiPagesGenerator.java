package io.github.fiserro.homehab.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.fiserro.homehab.InputItem;
import io.github.fiserro.homehab.module.CommonModule;
import io.github.fiserro.homehab.module.FlowerModule;
import io.github.fiserro.homehab.module.HrvModule;
import io.github.fiserro.options.Option;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Updates UI pages with min/max boundaries from Jakarta validation annotations.
 *
 * <p>This generator reads the ui-pages.json file and updates stepper/slider components
 * with min/max values from @Min/@Max annotations in module interfaces.
 */
@Slf4j
public class UiPagesGenerator {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Extracts field constraints from module interface annotations.
     *
     * @return map of field name to FieldConstraints
     */
    public static Map<String, FieldConstraints> getFieldConstraints() {
        Map<String, FieldConstraints> constraints = new HashMap<>();

        // Process all module interfaces
        processModuleConstraints(CommonModule.class, constraints);
        processModuleConstraints(HrvModule.class, constraints);
        processModuleConstraints(FlowerModule.class, constraints);

        return constraints;
    }

    private static void processModuleConstraints(
            Class<?> moduleClass, Map<String, FieldConstraints> constraints) {
        for (Method method : moduleClass.getDeclaredMethods()) {
            // Skip non-option methods
            if (!method.isAnnotationPresent(Option.class)) {
                continue;
            }

            // Only process InputItem annotated methods
            if (!method.isAnnotationPresent(InputItem.class)) {
                continue;
            }

            Min minAnn = method.getAnnotation(Min.class);
            Max maxAnn = method.getAnnotation(Max.class);

            if (minAnn != null || maxAnn != null) {
                Long min = minAnn != null ? minAnn.value() : null;
                Long max = maxAnn != null ? maxAnn.value() : null;
                constraints.put(method.getName(), new FieldConstraints(min, max));
            }
        }
    }

    public record FieldConstraints(Long min, Long max) {

        public long minOrDefault(long defaultValue) {
            return min != null ? min : defaultValue;
        }

        public long maxOrDefault(long defaultValue) {
            return max != null ? max : defaultValue;
        }
    }

    public void generate(GeneratorOptions options) throws IOException {
        Path inputPath = Paths.get(options.outputDir(), "ui-pages.json");
        Path outputPath = Paths.get(options.outputDir(), "..", "userdata", "jsondb", "uicomponents_ui_page.json");

        if (!Files.exists(inputPath)) {
            log.warn("UI pages file not found: {}", inputPath);
            return;
        }

        log.info("Reading UI pages from: {}", inputPath);
        JsonNode root = mapper.readTree(Files.readString(inputPath));

        Map<String, FieldConstraints> constraints = getFieldConstraints();
        log.info("Found {} fields with constraints: {}", constraints.size(), constraints.keySet());

        // Update all pages
        updateConstraintsInNode(root, constraints);

        // Write updated JSON
        String output = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        Files.writeString(inputPath, output);
        Files.writeString(outputPath, output);

        log.info("Updated UI pages written to: {} and {}", inputPath, outputPath);
    }

    private void updateConstraintsInNode(JsonNode node, Map<String, FieldConstraints> constraints) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;

            // Check if this is a stepper or slider component
            JsonNode componentNode = obj.get("component");
            JsonNode configNode = obj.get("config");

            if (componentNode != null && configNode != null && configNode.isObject()) {
                String component = componentNode.asText();
                ObjectNode config = (ObjectNode) configNode;

                if (component.contains("stepper") || component.contains("slider")) {
                    JsonNode itemNode = config.get("item");
                    if (itemNode != null) {
                        String itemName = itemNode.asText();
                        FieldConstraints fc = constraints.get(itemName);
                        if (fc != null) {
                            if (fc.min() != null) {
                                config.put("min", fc.min());
                            }
                            if (fc.max() != null) {
                                config.put("max", fc.max());
                            }
                            log.debug("Updated {} with min={}, max={}", itemName, fc.min(), fc.max());
                        }
                    }
                }
            }

            // Recursively process all children
            obj.fields().forEachRemaining(entry -> updateConstraintsInNode(entry.getValue(), constraints));

        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (JsonNode child : arr) {
                updateConstraintsInNode(child, constraints);
            }
        }
    }
}
