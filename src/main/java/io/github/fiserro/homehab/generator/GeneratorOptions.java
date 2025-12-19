package io.github.fiserro.homehab.generator;

import io.github.fiserro.options.Option;
import io.github.fiserro.options.Options;
import io.github.fiserro.options.extension.ArgumentsEquals;
import io.github.fiserro.options.extension.EnvironmentFile;
import io.github.fiserro.options.extension.EnvironmentVariables;
import io.github.fiserro.options.extension.OptionsExtensions;

@OptionsExtensions({EnvironmentVariables.class, ArgumentsEquals.class, EnvironmentFile.class})
public interface GeneratorOptions extends Options<GeneratorOptions> {

  @Option(description = "Base directory for the generated files")
  default String baseDir() {
    return "openhab-dev/conf/items";
  }

  @Option(description = "Output directory for generated files")
  default String outputDir() {
    return "openhab-dev/conf";
  }

  @Option(description = "Enables HabState items generation (input items + aggregation groups)")
  default boolean habStateEnabled() {
    return true;
  }

  @Option(description = "OpenHAB URL")
  default String openhabUrl() {
    return "http://localhost:8888";
  }

  @Option(description = "Enables items initialization with default values (requires OpenHAB restart after generating items)")
  default boolean initEnabled() {
    return false;
  }

  @Option(description = "SSH host for fetching devices (e.g., user@host)")
  String sshHost();

  @Option(description = "SSH private key path (default: ~/.ssh/id_rsa)")
  default String sshKey() {
    return System.getProperty("user.home") + "/.ssh/id_rsa";
  }

  @Option(description = "MQTT host for fetching devices")
  String mqttHost();

  @Option(description = "Enables MQTT/Zigbee configuration generation")
  default boolean mqttEnabled() {
    return true;
  }

  @Option(description = "Enables UI pages generation/update with constraints from annotations")
  default boolean uiPagesEnabled() {
    return true;
  }

  @Option(description = "MQTT broker host for Things configuration")
  default String mqttBrokerHost() {
    return "zigbee.home";
  }

  @Option(description = "MQTT broker port")
  default int mqttBrokerPort() {
    return 1883;
  }

  @Option(description = "MQTT client ID")
  default String mqttClientId() {
    return "homehab-dev";
  }
}
