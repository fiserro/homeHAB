package io.github.fiserro.homehab;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

class HabStateFactoryTest {

  @Test
  void shouldCreateHabStateWithInputItems() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("manualMode", mockState(OnOffType.ON));
    itemStates.put("temporaryBoostMode", mockState(OnOffType.OFF));
    itemStates.put("humidityThreshold", mockState(new DecimalType(70)));
    itemStates.put("co2ThresholdLow", mockState(new DecimalType(600)));

    HabState habState = HabStateFactory.of(itemStates);

    assertTrue(habState.manualMode());
    assertFalse(habState.temporaryBoostMode());
    assertEquals(70, habState.humidityThreshold());
    assertEquals(600, habState.co2ThresholdLow());
  }

  @Test
  void shouldUseDefaultValuesForMissingInputItems() {
    Map<String, State> itemStates = new HashMap<>();

    HabState habState = HabStateFactory.of(itemStates);

    assertFalse(habState.manualMode());
    assertFalse(habState.temporaryBoostMode());
    assertEquals(60, habState.humidityThreshold());
    assertEquals(500, habState.co2ThresholdLow());
    assertEquals(700, habState.co2ThresholdMid());
    assertEquals(900, habState.co2ThresholdHigh());
  }

  @Test
  void shouldLoadMqttItemFromGroupState() {
    // MqttItem fields read from group items (OpenHAB aggregates automatically)
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("airHumidity", mockState(new DecimalType(65)));

    HabState habState = HabStateFactory.of(itemStates);

    assertEquals(65, habState.airHumidity());
  }

  @Test
  void shouldLoadMultipleMqttItems() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("airHumidity", mockState(new DecimalType(65)));
    itemStates.put("co2", mockState(new DecimalType(800)));
    itemStates.put("temperature", mockState(new DecimalType(21)));
    itemStates.put("smoke", mockState(OnOffType.ON));

    HabState habState = HabStateFactory.of(itemStates);

    assertEquals(65, habState.airHumidity());
    assertEquals(800, habState.co2());
    assertEquals(21, habState.temperature());
    assertTrue(habState.smoke());
  }

  @Test
  void shouldLoadMqttFloatItem() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("openWindows", mockState(new DecimalType(50)));

    HabState habState = HabStateFactory.of(itemStates);

    assertEquals(50.0f, habState.openWindows(), 0.01);
  }

  @Test
  void shouldHandleMixedInputAndMqttItems() {
    Map<String, State> itemStates = new HashMap<>();
    // Input items
    itemStates.put("manualMode", mockState(OnOffType.ON));
    itemStates.put("humidityThreshold", mockState(new DecimalType(70)));
    // Mqtt items (group states with aggregated values)
    itemStates.put("airHumidity", mockState(new DecimalType(65)));
    itemStates.put("co2", mockState(new DecimalType(700)));

    HabState habState = HabStateFactory.of(itemStates);

    assertTrue(habState.manualMode());
    assertEquals(70, habState.humidityThreshold());
    assertEquals(65, habState.airHumidity());
    assertEquals(700, habState.co2());
  }

  @Test
  void shouldWriteStateToOutputItems() {
    HabState state = HabState.builder().hrvOutputPower(75).build();
    ScriptBusEvent events = mock(ScriptBusEvent.class);

    HabStateFactory.writeState(events, state);

    verify(events).sendCommand("hrvOutputPower", "75");
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
}
