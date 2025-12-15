package io.github.fiserro.homehab;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import helper.generated.Items;
import io.github.fiserro.homehab.HabState.Fields;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.items.GenericItem;
import org.openhab.core.items.GroupItem;
import org.openhab.core.items.Item;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

class HabStateFactoryTest {

  @Test
  void shouldCreateHabStateWithInputItems() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("manualMode", mockState(OnOffType.ON));
    itemStates.put("boostMode", mockState(OnOffType.OFF));
    itemStates.put("humidityThreshold", mockState(new DecimalType(70)));
    itemStates.put("co2ThresholdLow", mockState(new DecimalType(600)));

    MqttItemMappings itemMappings = MqttItemMappings.builder().build();

    HabState habState = HabStateFactory.of(itemStates, itemMappings);

    assertTrue(habState.manualMode());
    assertFalse(habState.boostMode());
    assertEquals(70, habState.humidityThreshold());
    assertEquals(600, habState.co2ThresholdLow());
  }

  @Test
  void shouldUseDefaultValuesForMissingInputItems() {
    Map<String, State> itemStates = new HashMap<>();
    MqttItemMappings itemMappings = MqttItemMappings.builder().build();

    HabState habState = HabStateFactory.of(itemStates, itemMappings);

    assertFalse(habState.manualMode());
    assertFalse(habState.boostMode());
    assertEquals(60, habState.humidityThreshold());
    assertEquals(500, habState.co2ThresholdLow());
    assertEquals(700, habState.co2ThresholdMid());
    assertEquals(900, habState.co2ThresholdHigh());
  }

  @Test
  void shouldLoadMqttItemWithSingleItem() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("humidity", mockState(new DecimalType(65)));

    GenericItem humidityItem = mockGenericItem("humidity");
    MqttItemMappings itemMappings =
        MqttItemMappings.builder().of(Fields.humidity, humidityItem).build();

    HabState habState = HabStateFactory.of(itemStates, itemMappings);

    assertEquals(65, habState.humidity());
  }

  @Test
  void shouldLoadMqttItemWithMultipleItemsAndMaxAggregation() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("co2_sensor1", mockState(new DecimalType(600)));
    itemStates.put("co2_sensor2", mockState(new DecimalType(800)));
    itemStates.put("co2_sensor3", mockState(new DecimalType(700)));

    GenericItem co2Item1 = mockGenericItem("co2_sensor1");
    GenericItem co2Item2 = mockGenericItem("co2_sensor2");
    GenericItem co2Item3 = mockGenericItem("co2_sensor3");

    MqttItemMappings itemMappings =
        MqttItemMappings.builder()
            .of(Fields.co2, co2Item1)
            .of(Fields.co2, co2Item2)
            .of(Fields.co2, co2Item3)
            .build();

    HabState habState = HabStateFactory.of(itemStates, itemMappings);

    assertEquals(800, habState.co2());
  }

  @Test
  void shouldLoadMqttItemWithMultipleItemsAndAvgAggregation() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("temp_sensor1", mockState(new DecimalType(20)));
    itemStates.put("temp_sensor2", mockState(new DecimalType(22)));
    itemStates.put("temp_sensor3", mockState(new DecimalType(21)));

    GenericItem tempItem1 = mockGenericItem("temp_sensor1");
    GenericItem tempItem2 = mockGenericItem("temp_sensor2");
    GenericItem tempItem3 = mockGenericItem("temp_sensor3");

    MqttItemMappings itemMappings =
        MqttItemMappings.builder()
            .of(Fields.temperature, tempItem1)
            .of(Fields.temperature, tempItem2)
            .of(Fields.temperature, tempItem3)
            .build();

    HabState habState = HabStateFactory.of(itemStates, itemMappings);

    assertEquals(21, habState.temperature());
  }

  @Test
  void shouldLoadMqttBooleanItemWithMaxAggregation() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("smoke_sensor1", mockState(OnOffType.OFF));
    itemStates.put("smoke_sensor2", mockState(OnOffType.ON));
    itemStates.put("smoke_sensor3", mockState(OnOffType.OFF));

    GenericItem smokeItem1 = mockGenericItem("smoke_sensor1");
    GenericItem smokeItem2 = mockGenericItem("smoke_sensor2");
    GenericItem smokeItem3 = mockGenericItem("smoke_sensor3");

    MqttItemMappings itemMappings =
        MqttItemMappings.builder()
            .of(Fields.smoke, smokeItem1)
            .of(Fields.smoke, smokeItem2)
            .of(Fields.smoke, smokeItem3)
            .build();

    HabState habState = HabStateFactory.of(itemStates, itemMappings);

    assertTrue(habState.smoke());
  }

  @Test
  void shouldLoadMqttFloatItemWithAvgAggregation() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("window1", mockState(new DecimalType(0)));
    itemStates.put("window2", mockState(new DecimalType(100)));

    GenericItem window1 = mockGenericItem("window1");
    GenericItem window2 = mockGenericItem("window2");

    MqttItemMappings itemMappings =
        MqttItemMappings.builder()
            .of(Fields.openWindows, window1)
            .of(Fields.openWindows, window2)
            .build();

    HabState habState = HabStateFactory.of(itemStates, itemMappings);

    assertEquals(50.0f, habState.openWindows(), 0.01);
  }

  @Test
  void shouldUseMqttItemOverInputItem() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("humidity", mockState(new DecimalType(65)));
    itemStates.put("mqtt_humidity", mockState(new DecimalType(75)));

    GenericItem humidityItem = mockGenericItem("mqtt_humidity");
    MqttItemMappings itemMappings =
        MqttItemMappings.builder().of(Fields.humidity, humidityItem).build();

    HabState habState = HabStateFactory.of(itemStates, itemMappings);

    assertEquals(75, habState.humidity());
  }

  @Test
  void shouldHandleMixedInputAndMqttItems() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("manualMode", mockState(OnOffType.ON));
    itemStates.put("humidityThreshold", mockState(new DecimalType(70)));
    itemStates.put("humidity", mockState(new DecimalType(65)));
    itemStates.put("co2", mockState(new DecimalType(700)));

    GenericItem humidityItem = mockGenericItem("humidity");
    GenericItem co2Item = mockGenericItem("co2");

    MqttItemMappings itemMappings =
        MqttItemMappings.builder()
            .of(Fields.humidity, humidityItem)
            .of(Fields.co2, co2Item)
            .build();

    HabState habState = HabStateFactory.of(itemStates, itemMappings);

    assertTrue(habState.manualMode());
    assertEquals(70, habState.humidityThreshold());
    assertEquals(65, habState.humidity());
    assertEquals(700, habState.co2());
  }

  private State mockState(OnOffType type) {
    State state = mock(State.class);
    when(state.as(OnOffType.class)).thenReturn(type);
    return state;
  }

  private State mockState(DecimalType type) {
    State state = mock(State.class);
    when(state.as(DecimalType.class)).thenReturn(type);
    return state;
  }

  private GenericItem mockGenericItem(String name) {
    GenericItem item = mock(GenericItem.class);
    when(item.getName()).thenReturn(name);
    return item;
  }

  @Test
  void shouldWriteStateToOutputItems() {
    HabState state = HabState.builder().hrvOutputPower(75).build();

    Item outputItem = mockItem("hrvOutputPower");
    GroupItem gOutputs = mockGroupItem(List.of(outputItem));
    Items items = mockItems(gOutputs);
    ScriptBusEvent events = mock(ScriptBusEvent.class);

    HabStateFactory.writeState(items, events, state);

    verify(events).sendCommand(outputItem, 75);
  }

  @Test
  void shouldThrowExceptionWhenOutputItemsDoNotMatch() {
    HabState state = HabState.builder().hrvOutputPower(75).build();

    Item wrongItem = mockItem("wrongOutputItem");
    GroupItem gOutputs = mockGroupItem(List.of(wrongItem));
    Items items = mockItems(gOutputs);
    ScriptBusEvent events = mock(ScriptBusEvent.class);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> HabStateFactory.writeState(items, events, state));

    assertTrue(exception.getMessage().contains("Output items in openHAB"));
    assertTrue(exception.getMessage().contains("do not match with output fields"));
    verifyNoInteractions(events);
  }

  @Test
  void shouldThrowExceptionWhenOutputItemsAreMissing() {
    HabState state = HabState.builder().hrvOutputPower(75).build();

    GroupItem gOutputs = mockGroupItem(List.of());
    Items items = mockItems(gOutputs);
    ScriptBusEvent events = mock(ScriptBusEvent.class);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> HabStateFactory.writeState(items, events, state));

    assertTrue(exception.getMessage().contains("Output items in openHAB"));
    assertTrue(exception.getMessage().contains("do not match with output fields"));
    verifyNoInteractions(events);
  }

  @Test
  void shouldThrowExceptionWhenTooManyOutputItems() {
    HabState state = HabState.builder().hrvOutputPower(75).build();

    Item outputItem1 = mockItem("hrvOutputPower");
    Item outputItem2 = mockItem("extraOutputItem");
    GroupItem gOutputs = mockGroupItem(List.of(outputItem1, outputItem2));
    Items items = mockItems(gOutputs);
    ScriptBusEvent events = mock(ScriptBusEvent.class);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> HabStateFactory.writeState(items, events, state));

    assertTrue(exception.getMessage().contains("Output items in openHAB"));
    assertTrue(exception.getMessage().contains("do not match with output fields"));
    verifyNoInteractions(events);
  }

  private Item mockItem(String name) {
    Item item = mock(Item.class);
    when(item.getName()).thenReturn(name);
    return item;
  }

  private GroupItem mockGroupItem(List<Item> members) {
    GroupItem groupItem = mock(GroupItem.class);
    when(groupItem.getAllMembers()).thenReturn(Set.copyOf(members));
    return groupItem;
  }

  private Items mockItems(GroupItem gOutputs) {
    Items items = mock(Items.class);
    when(items.gOutputs()).thenReturn(gOutputs);
    return items;
  }
}