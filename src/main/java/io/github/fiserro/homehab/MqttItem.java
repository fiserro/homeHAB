package io.github.fiserro.homehab;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an MQTT-bound item with optional aggregation.
 *
 * <p>The value patterns specify which MQTT topics to bind to.
 * Aggregation is used when multiple sensors contribute to a single value.
 *
 * <p>Examples:
 * <pre>
 * &#64;MqttItem(value = {"aqara*Humidity"}, numAgg = NumericAggregation.MAX)
 * int airHumidity();
 *
 * &#64;MqttItem(value = "fire*Smoke", boolAgg = BooleanAggregation.OR)
 * boolean smoke();
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface MqttItem {

  /**
   * MQTT topic patterns. Wildcards: * (any chars), ? (single char).
   * Empty means default pattern based on method name.
   */
  String[] value() default {};

  /**
   * Numeric aggregation for combining multiple sensor values.
   */
  NumericAggregation numAgg() default NumericAggregation.NONE;

  /**
   * Boolean aggregation for combining multiple sensor values.
   */
  BooleanAggregation boolAgg() default BooleanAggregation.NONE;
}
