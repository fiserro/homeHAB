package io.github.fiserro.homehab.generator;

import io.github.fiserro.options.Option;
import io.github.fiserro.options.Options;
import io.github.fiserro.options.extension.ArgumentsEquals;
import io.github.fiserro.options.extension.EnvironmentVariables;
import io.github.fiserro.options.extension.OptionsExtensions;

@OptionsExtensions({EnvironmentVariables.class, ArgumentsEquals.class})
public interface GeneratorOptions extends Options<GeneratorOptions> {

  @Option(description = "Base directory for the generated files")
  default String baseDir() {
    return "openhab-dev/conf/items";
  }

  @Option(description = "Output directory for generated files")
  default String outputDir() {
    return "openhab-dev/conf";
  }

  @Option(description = "Enables the task")
  default boolean uiEnabled() {
    return false;
  }

  @Option(description = "Enables the task")
  default boolean outputEnabled() {
    return false;
  }

  @Option(description = "OpenHAB URL")
  default String openhabUrl() {
    return "http://localhost:8888";
  }

  @Option(description = "Enables the task")
  default boolean initEnabled() {
    return false;
  }

  @Option(description = "SSH host for fetching devices (e.g., user@host)")
  String sshHost();

  @Option(description = "MQTT host for fetching devices")
  String mqttHost();

  @Option(description = "Enables the task")
  default boolean zigbeeEnabled() {
    return false;
  }
}
