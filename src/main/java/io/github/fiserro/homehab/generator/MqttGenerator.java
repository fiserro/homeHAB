package io.github.fiserro.homehab.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates OpenHAB MQTT Things and Items configuration from Zigbee2MQTT devices.
 *
 * <p>This generator connects to a Zigbee2MQTT broker (via SSH or direct MQTT connection),
 * fetches the device list, and generates:
 * <ul>
 *   <li>MQTT broker Thing configuration</li>
 *   <li>Thing definitions for each Zigbee device</li>
 *   <li>Item definitions for device metrics (temperature, humidity, smoke, etc.)</li>
 * </ul>
 *
 * <p>Items are generated without group assignments - users should manually assign
 * items to aggregation groups defined in {@code habstate-items.items}.
 *
 * @see HabStateItemsGenerator
 */
@Slf4j
public class MqttGenerator {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Path outputDir;

  public MqttGenerator(String outputDir) {
    this.outputDir = Paths.get(outputDir);
  }

  public void generate(GeneratorOptions options) throws IOException, InterruptedException {
    log.info("Generating MQTT/Zigbee configuration...");

    // Fetch devices
    List<JsonNode> devices = fetchDevices(options);

    if (devices.isEmpty()) {
      log.info("No devices found, skipping MQTT file generation");
      return;
    }

    log.info("Found {} devices", devices.size());

    // Generate MQTT Broker configuration
    Path mqttFile = outputDir.resolve("things/mqtt.things");
    generateMqttBrokerFile(options, mqttFile);
    log.info("Generated MQTT Broker file: {}", mqttFile);

    // Generate Things file
    Path thingsFile = outputDir.resolve("things/mqtt-devices.things");
    generateThingsFile(devices, thingsFile);
    log.info("Generated Things file: {}", thingsFile);

    // Generate Items file
    Path itemsFile = outputDir.resolve("items/mqtt-devices.items");
    generateItemsFile(devices, itemsFile);
    log.info("Generated Items file: {}", itemsFile);

    log.info("MQTT/Zigbee configuration generated successfully");
  }

  private List<JsonNode> fetchDevices(GeneratorOptions options)
      throws IOException, InterruptedException {
    if (options.sshHost() != null && !options.sshHost().isEmpty()) {
      return fetchDevicesViaSsh(options.sshHost());
    } else if (options.mqttHost() != null && !options.mqttHost().isEmpty()) {
      return fetchDevicesViaMqtt(options.mqttHost());
    } else {
      log.warn("Skipping MQTT generation: neither sshHost nor mqttHost is provided");
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
    content.append("// Auto-generated MQTT Things configuration\n");
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
          generateChannel(expose, friendlyName, content);
        }
      }

      content.append("}\n\n");
    }

    Files.createDirectories(outputFile.getParent());
    Files.writeString(outputFile, content.toString());
  }

  private void generateChannel(JsonNode expose, String friendlyName, StringBuilder content) {
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

    String stateTopic = String.format("zigbee2mqtt/%s", friendlyName);
    content.append(String.format("        Type %s : %s \"%s\" [\n",
        channelType, expProperty, getLabel(expProperty)));
    content.append(String.format("            stateTopic=\"%s\",\n", stateTopic));
    content.append(String.format("            transformationPattern=\"JSONPATH:$.%s\"", expProperty));
    // For switch types, add on/off value mappings (Zigbee2MQTT uses true/false)
    if ("switch".equals(channelType)) {
      content.append(",\n            on=\"true\",\n            off=\"false\"");
    }
    content.append("\n        ]\n");
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
    content.append("// Auto-generated MQTT Items configuration\n");
    content.append("// DO NOT EDIT - changes will be overwritten\n");
    content.append("// Manually assign items to groups in habstate-items.items as needed\n\n");

    // Generate items for all devices and their metrics (no automatic group assignment)
    for (JsonNode device : devices) {
      String type = device.has("type") ? device.get("type").asText() : "";
      if ("Coordinator".equals(type)) {
        continue;
      }

      String ieee = device.get("ieee_address").asText();
      String friendlyName = device.has("friendly_name") ? device.get("friendly_name").asText() : ieee;
      String thingId = "zigbee_" + ieee.replace(":", "");

      content.append("// ").append(friendlyName).append("\n");

      if (device.has("definition") && device.get("definition").has("exposes")) {
        JsonNode exposes = device.get("definition").get("exposes");
        for (JsonNode expose : exposes) {
          String property = expose.has("property") ? expose.get("property").asText() : "";
          if (!property.isEmpty()) {
            String category = getMetricCategory(property);
            if (category != null) {
              String itemDef = generateItemDefinition(expose, thingId, ieee, friendlyName, property, category);
              if (itemDef != null) {
                content.append(itemDef).append("\n");
              }
            }
          }
        }
      }
      content.append("\n");
    }

    Files.createDirectories(outputFile.getParent());
    Files.writeString(outputFile, content.toString());
  }

  private String generateItemDefinition(JsonNode expose, String thingId, String ieee,
      String friendlyName, String property, String category) {
    String itemType = getItemType(expose);
    if (itemType == null) {
      return null;
    }

    // Item name: friendlyName in camelCase + category (e.g., "soil3Battery")
    String itemName = toCamelCase(friendlyName) + category;
    // Label: friendlyName readable + category readable (e.g., "Soil3 Battery")
    String label = toReadableLabel(friendlyName) + " " + category;
    String icon = getIconForCategory(category);
    String channel = String.format("mqtt:topic:zigbee2mqtt:%s:%s", thingId, property);
    // Tags: IEEE address, mqtt, zigbee
    String tags = String.format("[\"%s\", \"mqtt\", \"zigbee\"]", ieee);

    // No automatic group assignment - user assigns items to groups manually
    return String.format("%s %s \"%s\" <%s> %s { channel=\"%s\" }",
        itemType, itemName, label, icon, tags, channel);
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

  /**
   * Maps property name to metric category. Only exact matches are supported to avoid generating
   * items for auxiliary properties like humidity_calibration, temperature_unit, etc.
   *
   * @return category name or null if property should be skipped
   */
  private String getMetricCategory(String property) {
    String lower = property.toLowerCase();
    // Only exact matches - auxiliary properties like humidity_calibration are skipped
    Map<String, String> categoryMap = Map.ofEntries(
        Map.entry("temperature", "Temperature"),
        Map.entry("humidity", "Humidity"),
        Map.entry("pressure", "Pressure"),
        Map.entry("co2", "Co2"),
        Map.entry("smoke", "Smoke"),
        Map.entry("gas", "Gas"),
        Map.entry("contact", "Contact"),
        Map.entry("occupancy", "Occupancy"),
        Map.entry("illuminance", "Illuminance"),
        Map.entry("battery", "Battery"),
        Map.entry("voltage", "Voltage"),
        Map.entry("linkquality", "Linkquality"),
        Map.entry("link_quality", "Linkquality")
    );

    return categoryMap.get(lower); // Returns null if not found (property will be skipped)
  }

  private String getIconForCategory(String category) {
    return switch (category) {
      case "Temperature" -> "temperature";
      case "Humidity" -> "humidity";
      case "Pressure" -> "pressure";
      case "Co2" -> "carbondioxide";
      case "Smoke" -> "smoke";
      case "Gas" -> "gas";
      case "Contact" -> "contact";
      case "Occupancy" -> "motion";
      case "Illuminance" -> "light";
      case "Battery" -> "battery";
      case "Voltage" -> "energy";
      case "Linkquality" -> "network";
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
   * Converts a friendly name to camelCase for use as item name.
   * Examples: "soil3" -> "soil3", "living_room" -> "livingRoom", "Bedroom 1" -> "bedroom1"
   */
  private String toCamelCase(String friendlyName) {
    if (friendlyName == null || friendlyName.isEmpty()) {
      return friendlyName;
    }

    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = false;

    for (int i = 0; i < friendlyName.length(); i++) {
      char c = friendlyName.charAt(i);
      if (c == '_' || c == ' ' || c == '-') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else if (i == 0) {
        result.append(Character.toLowerCase(c));
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  /**
   * Converts a friendly name to readable label format.
   * Examples: "soil3" -> "Soil3", "living_room" -> "Living Room", "bedroom1" -> "Bedroom1"
   */
  private String toReadableLabel(String friendlyName) {
    if (friendlyName == null || friendlyName.isEmpty()) {
      return friendlyName;
    }

    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = true;

    for (int i = 0; i < friendlyName.length(); i++) {
      char c = friendlyName.charAt(i);
      if (c == '_' || c == '-') {
        result.append(' ');
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }
}
