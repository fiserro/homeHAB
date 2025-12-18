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

      // Generate HabState items (input items + aggregation groups)
      if (options.habStateEnabled()) {
        log.info("Generating HabState items...");
        new HabStateItemsGenerator().generate(options);
        log.info("Generated HabState items");
      }

      // Generate output items
      if (options.outputEnabled()) {
        log.info("Generating output items...");
        new OutputItemsGenerator().generate(options);
        log.info("Generated output items");
      }

      // Initialize
      if (options.initEnabled()) {
        log.info("Initializing all items with default values...");
        new Initializer().initialize(options);
        log.info("Initialized all items");
      }

      // Generate Zigbee configuration
      if (options.zigbeeEnabled()) {
        log.info("Generating Zigbee configuration...");
        new ZigbeeGenerator(options.outputDir()).generate(options);
        log.info("Generated Zigbee configuration");
      }

      log.info("All configuration files generated successfully!");

    } catch (Exception e) {
      log.error("Failed to generate configuration: {}", e.getMessage(), e);
      System.exit(1);
    }
  }
}
