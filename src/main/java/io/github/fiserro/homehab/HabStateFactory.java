package io.github.fiserro.homehab;

import io.github.fiserro.homehab.HabState.HabStateBuilder;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.val;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.items.GenericItem;
import org.openhab.core.items.GroupItem;
import org.openhab.core.items.Item;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

public class HabStateFactory {

  private static List<String> getFields(Class<?> clazz) {
    return Stream.of(clazz.getDeclaredFields())
        .map(Field::getName)
        .toList();
  }

  public static void writeState(
      GroupItem outputGroup, ScriptBusEvent events, HabState state) {

    Map<String, Item> outputItems =
        outputGroup.getAllMembers().stream().collect(Collectors.toMap(Item::getName, i -> i));

    Map<String, Number> outputValues =
        getFields(HabStateBuilder.class).stream()
            .map(stateField -> getField(HabState.class, stateField))
            .filter(field -> field.isAnnotationPresent(OutputItem.class))
            .collect(Collectors.toMap(Field::getName, f -> getFieldNumberValue(state, f)));

    if (!outputItems.keySet().equals(outputValues.keySet())) {
      throw new IllegalArgumentException(
          "Output items in openHAB: " + outputItems.keySet() + " do not match with output fields: " + outputValues.keySet() + ". "
              + "Please check your configuration or regenerate the openHAB items.");
    }

    outputValues.forEach((name, value) -> {
      val item = outputItems.get(name);
      events.sendCommand(item, value);
    });
  }

  public static HabState of(Map<String, State> itemStates, MqttItemMappings itemMappings) {

    val builder = HabState.builder();

    getFields(HabStateBuilder.class)
        .forEach(
            fieldName -> {
              val field = getField(HabState.class, fieldName);
              val value =
                  loadInputItem(itemStates, field)
                      .or(() -> loadMqttItem(itemStates, itemMappings, fieldName, field));
              value.ifPresent(o -> setValue(builder, fieldName, o));
            });

    return builder.build();
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

  private static Optional<Object> loadInputItem(Map<String, State> itemStates, Field field) {
    Object value = getItemValue(itemStates, field);
    return Optional.ofNullable(value);
  }

  private static Optional<Object> loadMqttItem(
      Map<String, State> itemStates,
      MqttItemMappings itemMappings,
      String fieldName,
      Field field) {
    if (!field.isAnnotationPresent(MqttItem.class)) {
      return Optional.empty();
    }
    val items = itemMappings.get(fieldName);
    if (items.isEmpty()) {
      return Optional.empty();
    }
    val aggregate = field.getAnnotation(NumAgg.class);
    if (items.size() > 1 && aggregate == null) {
      throw new IllegalArgumentException(
          "More than one MQTT item found for field " + field.getName());
    }

    val values =
        items.stream().map(item -> getMqttItemValue(itemStates, item, field.getType())).toList();
    val value = aggregate.value().aggregate(values);

    return Optional.of(value);
  }

  private static @Nullable Object getItemValue(Map<String, State> itemStates, Field field) {
    if (!field.isAnnotationPresent(InputItem.class)) {
      return null;
    }
    val stateValue = itemStates.get(field.getName());
    if (stateValue == null) {
      return null;
    }
    return convertStateValue(stateValue, field.getType());
  }

  private static @Nullable Object getMqttItemValue(
      Map<String, State> itemStates, GenericItem item, Class<?> fieldType) {
    val stateValue = itemStates.get(item.getName());
    if (stateValue == null) {
      return null;
    }
    return convertStateValue(stateValue, fieldType);
  }

  private static Object convertStateValue(State stateValue, Class<?> fieldType) {
    if (fieldType == int.class) {
      return stateValue.as(DecimalType.class).intValue();
    } else if (fieldType == float.class) {
      return stateValue.as(DecimalType.class).floatValue();
    } else if (fieldType == boolean.class) {
      return stateValue.as(OnOffType.class) == OnOffType.ON;
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
