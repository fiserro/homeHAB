package io.github.fiserro.homehab.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.fiserro.homehab.Aggregate;
import io.github.fiserro.homehab.AggregationType;
import io.github.fiserro.homehab.HabState;
import io.github.fiserro.homehab.MqttItem;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates OpenHAB Things and Items configuration from Zigbee2MQTT devices.
 */
@Slf4j
public class ZigbeeGenerator {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Path outputDir;

  public ZigbeeGenerator(String outputDir) {
    this.outputDir = Paths.get(outputDir);
  }

  public void generate(GeneratorOptions options) throws IOException, InterruptedException {
    log.info("Generating Zigbee configuration...");

    // Fetch devices
    List<JsonNode> devices = fetchDevices(options);

    if (devices.isEmpty()) {
      log.info("No devices found, skipping Zigbee file generation");
      return;
    }

    log.info("Found {} devices", devices.size());

    // Generate MQTT Broker configuration
    Path mqttFile = outputDir.resolve("things/mqtt.things");
    generateMqttBrokerFile(options, mqttFile);
    log.info("Generated MQTT Broker file: {}", mqttFile);

    // Generate Things file
    Path thingsFile = outputDir.resolve("things/zigbee-devices.things");
    generateThingsFile(devices, thingsFile);
    log.info("Generated Things file: {}", thingsFile);

    // Generate Items file
    Path itemsFile = outputDir.resolve("items/zigbee-devices.items");
    generateItemsFile(devices, itemsFile);
    log.info("Generated Items file: {}", itemsFile);

    log.info("Zigbee configuration generated successfully");
  }

  private List<JsonNode> fetchDevices(GeneratorOptions options)
      throws IOException, InterruptedException {
    if (options.sshHost() != null && !options.sshHost().isEmpty()) {
      return fetchDevicesViaSsh(options.sshHost());
    } else if (options.mqttHost() != null && !options.mqttHost().isEmpty()) {
      return fetchDevicesViaMqtt(options.mqttHost());
    } else {
      log.warn("Skipping Zigbee generation: neither sshHost nor mqttHost is provided");
      return List.of();
    }
  }

  private List<JsonNode> fetchDevicesViaSsh(String sshHost)
      throws IOException, InterruptedException {
    log.info("Fetching devices via SSH from {}...", sshHost);

    ProcessBuilder pb = new ProcessBuilder(
        "ssh", "-o", "StrictHostKeyChecking=no", sshHost,
        "mosquitto_sub -h localhost -t 'zigbee2mqtt/bridge/devices' -C 1"
    );
    pb.redirectErrorStream(true);
    Process process = pb.start();

    String json = new String(process.getInputStream().readAllBytes());
    int exitCode = process.waitFor();

    if (exitCode != 0) {
      throw new IOException("SSH command failed with exit code " + exitCode);
    }

    JsonNode devicesArray = objectMapper.readTree(json);
    List<JsonNode> devices = new ArrayList<>();
    for (JsonNode elem : devicesArray) {
      devices.add(elem);
    }

    return devices;
  }

  private List<JsonNode> fetchDevicesViaMqtt(String mqttHost)
      throws IOException, InterruptedException {
    log.info("Fetching devices via MQTT from {}...", mqttHost);

    ProcessBuilder pb = new ProcessBuilder(
        "mosquitto_sub", "-h", mqttHost,
        "-t", "zigbee2mqtt/bridge/devices", "-C", "1"
    );
    pb.redirectErrorStream(true);
    Process process = pb.start();

    String json = new String(process.getInputStream().readAllBytes());
    int exitCode = process.waitFor();

    if (exitCode != 0) {
      throw new IOException("mosquitto_sub command failed with exit code " + exitCode);
    }

    JsonNode devicesArray = objectMapper.readTree(json);
    List<JsonNode> devices = new ArrayList<>();
    for (JsonNode elem : devicesArray) {
      devices.add(elem);
    }

    return devices;
  }

  private void generateMqttBrokerFile(GeneratorOptions options, Path outputFile) throws IOException {
    StringBuilder content = new StringBuilder();
    content.append("// Auto-generated MQTT Broker configuration\n");
    content.append("// DO NOT EDIT - changes will be overwritten\n\n");

    String mqttHost = options.mqttBrokerHost();
    int mqttPort = options.mqttBrokerPort();
    String clientId = options.mqttClientId();

    content.append("Thing mqtt:broker:zigbee2mqtt \"Zigbee2MQTT Broker\" [\n");
    content.append(String.format("    host=\"%s\",\n", mqttHost));
    content.append(String.format("    port=%d,\n", mqttPort));
    content.append(String.format("    clientid=\"%s\",\n", clientId));
    content.append("    secure=false,\n");
    content.append("    protocol=\"TCP\",\n");
    content.append("    mqttVersion=\"V3\",\n");
    content.append("    qos=0,\n");
    content.append("    keepAlive=60,\n");
    content.append("    reconnectTime=60000,\n");
    content.append("    enableDiscovery=true,\n");
    content.append("    lwtQos=0,\n");
    content.append("    lwtRetain=true,\n");
    content.append("    birthRetain=true,\n");
    content.append("    shutdownRetain=true,\n");
    content.append("    publickeypin=true,\n");
    content.append("    hostnameValidated=true,\n");
    content.append("    certificatepin=true\n");
    content.append("]\n");

    Files.createDirectories(outputFile.getParent());
    Files.writeString(outputFile, content.toString());
  }

  private void generateThingsFile(List<JsonNode> devices, Path outputFile) throws IOException {
    StringBuilder content = new StringBuilder();
    content.append("// Auto-generated Zigbee Things configuration\n");
    content.append("// DO NOT EDIT - changes will be overwritten\n");
    content.append("// These Things use the Bridge defined in mqtt.things\n\n");

    for (JsonNode device : devices) {
      String type = device.has("type") ? device.get("type").asText() : "";
      if ("Coordinator".equals(type)) {
        continue; // Skip coordinator
      }

      String ieee = device.get("ieee_address").asText();
      String friendlyName = device.has("friendly_name") ? device.get("friendly_name").asText() : ieee;

      content.append(String.format("Thing mqtt:topic:zigbee2mqtt:zigbee_%s \"%s\" (mqtt:broker:zigbee2mqtt) [\n",
          ieee.replace(":", ""), friendlyName));
      content.append(String.format("    stateTopic=\"zigbee2mqtt/%s\",\n", friendlyName));
      content.append(String.format("    commandTopic=\"zigbee2mqtt/%s/set\"\n", friendlyName));
      content.append("] {\n");
      content.append("    Channels:\n");

      // Generate channels from exposes
      if (device.has("definition") && device.get("definition").has("exposes")) {
        JsonNode exposes = device.get("definition").get("exposes");
        for (JsonNode expose : exposes) {
          generateChannel(expose, content);
        }
      }

      content.append("}\n\n");
    }

    Files.createDirectories(outputFile.getParent());
    Files.writeString(outputFile, content.toString());
  }

  private void generateChannel(JsonNode expose, StringBuilder content) {
    String expType = expose.has("type") ? expose.get("type").asText() : "";
    String expName = expose.has("name") ? expose.get("name").asText() : "";
    String expProperty = expose.has("property") ? expose.get("property").asText() : expName;

    if (expProperty.isEmpty()) {
      return;
    }

    String channelType = getChannelType(expose);
    if (channelType == null) {
      return;
    }

    content.append(String.format("        Type %s : %s \"%s\" [\n",
        channelType, expProperty, getLabel(expProperty)));
    content.append(String.format("            stateTopic=\"~\",\n"));
    content.append(String.format("            transformationPattern=\"JSONPATH:$.%s\"\n", expProperty));
    content.append("        ]\n");
  }

  private String getChannelType(JsonNode expose) {
    String expType = expose.has("type") ? expose.get("type").asText() : "";
    String expProperty = expose.has("property") ? expose.get("property").asText() : "";
    String expName = expose.has("name") ? expose.get("name").asText() : expProperty;

    // Map expose types to OpenHAB channel types
    return switch (expType.toLowerCase()) {
      case "binary" -> "switch";
      case "numeric" -> "number";
      case "enum" -> "string";
      default -> {
        // Try to infer from property name
        String lower = expName.toLowerCase();
        if (lower.contains("temperature")) yield "number";
        if (lower.contains("humidity")) yield "number";
        if (lower.contains("pressure")) yield "number";
        if (lower.contains("battery")) yield "number";
        if (lower.contains("contact")) yield "contact";
        if (lower.contains("occupancy")) yield "switch";
        yield null;
      }
    };
  }

  private void generateItemsFile(List<JsonNode> devices, Path outputFile) throws IOException {
    StringBuilder content = new StringBuilder();
    content.append("// Auto-generated Zigbee Items configuration\n");
    content.append("// DO NOT EDIT - changes will be overwritten\n\n");

    // Group devices by metric category
    Map<String, List<String>> itemsByCategory = new HashMap<>();

    for (JsonNode device : devices) {
      String type = device.has("type") ? device.get("type").asText() : "";
      if ("Coordinator".equals(type)) {
        continue;
      }

      String ieee = device.get("ieee_address").asText();
      String thingId = "zigbee_" + ieee.replace(":", "");

      if (device.has("definition") && device.get("definition").has("exposes")) {
        JsonNode exposes = device.get("definition").get("exposes");
        for (JsonNode expose : exposes) {
          String property = expose.has("property") ? expose.get("property").asText() : "";
          if (!property.isEmpty()) {
            String category = getMetricCategory(property);
            String itemDef = generateItemDefinition(expose, thingId, ieee, property, category);
            if (itemDef != null) {
              itemsByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(itemDef);
            }
          }
        }
      }
    }

    // Write groups and items organized by category
    for (Map.Entry<String, List<String>> entry : itemsByCategory.entrySet()) {
      String category = entry.getKey();
      // Determine item type from first item definition
      String firstItem = entry.getValue().isEmpty() ? "" : entry.getValue().get(0);
      String itemType = firstItem.startsWith("Switch") ? "Switch" : "Number";

      content.append(generateGroupDefinition(category, itemType)).append("\n\n");

      for (String itemDef : entry.getValue()) {
        content.append(itemDef).append("\n");
      }
      content.append("\n");
    }

    Files.createDirectories(outputFile.getParent());
    Files.writeString(outputFile, content.toString());
  }

  private String generateItemDefinition(JsonNode expose, String thingId, String ieee,
      String property, String category) {
    String itemType = getItemType(expose);
    if (itemType == null) {
      return null;
    }

    String itemName = String.format("mqttZigbee%s_%s", capitalize(category), ieee.replace(":", ""));
    String label = getLabel(property);
    String icon = getIconForCategory(category);
    String channel = String.format("mqtt:topic:zigbee2mqtt:%s:%s", thingId, property);

    return String.format("%s %s \"%s\" <%s> (gZigbee%s) { channel=\"%s\" }",
        itemType, itemName, label, icon, capitalize(category), channel);
  }

  private String getItemType(JsonNode expose) {
    String expType = expose.has("type") ? expose.get("type").asText() : "";
    return switch (expType.toLowerCase()) {
      case "binary" -> "Switch";
      case "numeric" -> "Number";
      case "enum" -> "String";
      default -> "String";
    };
  }

  private String getMetricCategory(String property) {
    String lower = property.toLowerCase();
    Map<String, String> categoryMap = Map.ofEntries(
        Map.entry("temperature", "temperature"),
        Map.entry("humidity", "humidity"),
        Map.entry("pressure", "pressure"),
        Map.entry("co2", "co2"),
        Map.entry("smoke", "smoke"),
        Map.entry("contact", "contact"),
        Map.entry("occupancy", "occupancy"),
        Map.entry("illuminance", "illuminance"),
        Map.entry("battery", "battery"),
        Map.entry("voltage", "voltage"),
        Map.entry("linkquality", "linkquality"),
        Map.entry("link_quality", "linkquality")
    );

    for (Map.Entry<String, String> entry : categoryMap.entrySet()) {
      if (lower.contains(entry.getKey())) {
        return entry.getValue();
      }
    }
    return lower;
  }

  private String getIconForCategory(String category) {
    return switch (category) {
      case "temperature" -> "temperature";
      case "humidity" -> "humidity";
      case "pressure" -> "pressure";
      case "co2" -> "carbondioxide";
      case "smoke" -> "smoke";
      case "contact" -> "contact";
      case "occupancy" -> "motion";
      case "illuminance" -> "light";
      case "battery" -> "battery";
      case "voltage" -> "energy";
      case "linkquality" -> "network";
      default -> "none";
    };
  }

  private String getLabel(String property) {
    return capitalize(property.replace("_", " "));
  }

  private String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return str.substring(0, 1).toUpperCase() + str.substring(1);
  }

  /**
   * Builds a map of category name -> aggregation type from HabState @MqttItem fields.
   */
  private Map<String, AggregationType> getAggregationMap() {
    Map<String, AggregationType> aggregationMap = new HashMap<>();

    for (Field field : HabState.class.getDeclaredFields()) {
      if (field.isAnnotationPresent(MqttItem.class)) {
        Aggregate aggregate = field.getAnnotation(Aggregate.class);
        if (aggregate != null) {
          // Map field name to category (e.g., "humidity" -> "humidity", "co2" -> "co2")
          String category = field.getName().toLowerCase();
          aggregationMap.put(category, aggregate.value());
        }
      }
    }

    return aggregationMap;
  }

  /**
   * Generates OpenHAB Group definition with aggregation function.
   */
  private String generateGroupDefinition(String category, String itemType) {
    Map<String, AggregationType> aggregationMap = getAggregationMap();
    AggregationType aggregation = aggregationMap.get(category.toLowerCase());

    String groupName = "gZigbee" + capitalize(category);
    String label = capitalize(category);

    if (aggregation != null) {
      String aggFunc = switch (aggregation) {
        case MAX -> itemType.equals("Switch") ? "OR(ON,OFF)" : "MAX";
        case MIN -> "MIN";
        case AVG -> "AVG";
        case SUM -> "SUM";
        case COUNT -> "COUNT";
      };
      return String.format("Group:%s:%s %s \"%s\"", itemType, aggFunc, groupName, label);
    } else {
      // No aggregation defined, create simple group
      return String.format("Group %s \"%s\"", groupName, label);
    }
  }
}
