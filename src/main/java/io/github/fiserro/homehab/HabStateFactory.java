package io.github.fiserro.homehab;

import io.github.fiserro.homehab.HabState.HabStateBuilder;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

/**
 * Factory for creating and writing HabState from/to OpenHAB items.
 *
 * <p>Reads values from:
 * <ul>
 *   <li>{@link InputItem} - individual items (user settings)</li>
 *   <li>{@link MqttItem} - group items with aggregated values (OpenHAB aggregates automatically)</li>
 * </ul>
 *
 * <p>Writes values to:
 * <ul>
 *   <li>{@link OutputItem} - individual items (computed outputs)</li>
 * </ul>
 */
public class HabStateFactory {

  private static List<String> getFields(Class<?> clazz) {
    return Stream.of(clazz.getDeclaredFields())
        .map(Field::getName)
        .toList();
  }

  /**
   * Writes output values from HabState to OpenHAB items.
   */
  public static void writeState(ScriptBusEvent events, HabState state) {
    getFields(HabStateBuilder.class).stream()
        .map(fieldName -> getField(HabState.class, fieldName))
        .filter(field -> field.isAnnotationPresent(OutputItem.class))
        .forEach(field -> {
          Number value = getFieldNumberValue(state, field);
          events.sendCommand(field.getName(), String.valueOf(value));
        });
  }

  /**
   * Creates HabState from OpenHAB item states.
   *
   * <p>For {@link InputItem} fields, reads from individual items.
   * <p>For {@link MqttItem} fields, reads from group items (field name = group name).
   * OpenHAB automatically aggregates member values based on group function.
   */
  public static HabState of(Map<String, State> itemStates) {
    val builder = HabState.builder();

    getFields(HabStateBuilder.class).forEach(fieldName -> {
      val field = getField(HabState.class, fieldName);
      val value = loadItemValue(itemStates, field);
      value.ifPresent(v -> setValue(builder, fieldName, v));
    });

    return builder.build();
  }

  /**
   * Loads item value for a field. Supports @InputItem and @MqttItem annotations.
   * For @MqttItem, the field name is the group name containing aggregated value.
   */
  private static Optional<Object> loadItemValue(Map<String, State> itemStates, Field field) {
    // Both @InputItem and @MqttItem use field name as item name
    // For @MqttItem, the item is a Group with aggregated value from OpenHAB
    if (!field.isAnnotationPresent(InputItem.class) && !field.isAnnotationPresent(MqttItem.class)) {
      return Optional.empty();
    }

    val stateValue = itemStates.get(field.getName());
    if (stateValue == null) {
      return Optional.empty();
    }

    Object value = convertStateValue(stateValue, field.getType());
    return Optional.ofNullable(value);
  }

  @SneakyThrows
  private static Number getFieldNumberValue(HabState state, Field field) {
    Object value = field.get(state);
    return switch (value) {
      case null -> throw new IllegalArgumentException("Field " + field.getName() + " is null");
      case Number n -> n;
      case Boolean b -> b ? 1 : 0;
      default -> throw new IllegalArgumentException("Unsupported type: " + field.getType());
    };
  }

  private static Object convertStateValue(State stateValue, Class<?> fieldType) {
    if (fieldType == int.class) {
      DecimalType decimal = stateValue.as(DecimalType.class);
      return decimal != null ? decimal.intValue() : null;
    } else if (fieldType == float.class) {
      DecimalType decimal = stateValue.as(DecimalType.class);
      return decimal != null ? decimal.floatValue() : null;
    } else if (fieldType == boolean.class) {
      OnOffType onOff = stateValue.as(OnOffType.class);
      return onOff != null ? onOff == OnOffType.ON : null;
    } else {
      throw new IllegalArgumentException("Unsupported type: " + fieldType);
    }
  }

  @SneakyThrows
  private static void setValue(HabStateBuilder builder, String fieldName, Object value) {
    val field = getField(HabStateBuilder.class, fieldName);
    Object convertedValue = value;
    if (value instanceof Number number) {
      if (field.getType() == int.class) {
        convertedValue = number.intValue();
      } else if (field.getType() == float.class) {
        convertedValue = number.floatValue();
      } else if (field.getType() == boolean.class) {
        convertedValue = number.intValue() != 0;
      }
    }
    field.set(builder, convertedValue);
  }

  @SneakyThrows
  private static @NonNull Field getField(Class<?> clazz, String fieldName) {
    Field field = clazz.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field;
  }
}
