package io.github.fiserro.homehab.generator;

import io.github.fiserro.options.OptionsFactory;
import lombok.extern.slf4j.Slf4j;

/** Generates all OpenHAB configuration files. */
@Slf4j
public class Generator {

  public static void main(String[] args) {

    GeneratorOptions options = OptionsFactory.create(GeneratorOptions.class, args);

    try {
      log.info("Generating all OpenHAB configuration...");

      // Generate MQTT/Zigbee configuration
      if (options.mqttEnabled()) {
        log.info("Generating MQTT/Zigbee configuration...");
        new MqttGenerator(options.outputDir()).generate(options);
        log.info("Generated MQTT/Zigbee configuration");
      }

      // Generate HabState items (input items, output items, aggregation groups)
      if (options.habStateEnabled()) {
        log.info("Generating HabState items...");
        new HabStateItemsGenerator().generate(options);
        log.info("Generated HabState items");
      }

      // Initialize
      if (options.initEnabled()) {
        log.info("Initializing all items with default values...");
        new Initializer().initialize(options);
        log.info("Initialized all items");
      }

      log.info("All configuration files generated successfully!");

    } catch (Exception e) {
      log.error("Failed to generate configuration: {}", e.getMessage(), e);
      System.exit(1);
    }
  }
}
