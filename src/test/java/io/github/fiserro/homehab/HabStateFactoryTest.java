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
  void shouldCreateTestHabStateWithInputItems() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("manualMode", mockState(OnOffType.ON));
    itemStates.put("temporaryBoostMode", mockState(OnOffType.OFF));
    itemStates.put("humidityThreshold", mockState(new DecimalType(70)));
    itemStates.put("co2ThresholdLow", mockState(new DecimalType(600)));

    TestHabState habState = HabStateFactory.of(TestHabState.class, itemStates);

    assertTrue(habState.manualMode());
    assertFalse(habState.temporaryBoostMode());
    assertEquals(70, habState.humidityThreshold());
    assertEquals(600, habState.co2ThresholdLow());
  }

  @Test
  void shouldUseDefaultValuesForMissingInputItems() {
    Map<String, State> itemStates = new HashMap<>();

    TestHabState habState = HabStateFactory.of(TestHabState.class, itemStates);

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

    TestHabState habState = HabStateFactory.of(TestHabState.class, itemStates);

    assertEquals(65, habState.airHumidity());
  }

  @Test
  void shouldLoadMultipleMqttItems() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("airHumidity", mockState(new DecimalType(65)));
    itemStates.put("co2", mockState(new DecimalType(800)));
    itemStates.put("temperature", mockState(new DecimalType(21)));
    itemStates.put("smoke", mockState(OnOffType.ON));

    TestHabState habState = HabStateFactory.of(TestHabState.class, itemStates);

    assertEquals(65, habState.airHumidity());
    assertEquals(800, habState.co2());
    assertEquals(21, habState.temperature());
    assertTrue(habState.smoke());
  }

  @Test
  void shouldLoadOpenWindowsItem() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("openWindows", mockState(new DecimalType(2)));

    TestHabState habState = HabStateFactory.of(TestHabState.class, itemStates);

    assertEquals(2, habState.openWindows());
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

    TestHabState habState = HabStateFactory.of(TestHabState.class, itemStates);

    assertTrue(habState.manualMode());
    assertEquals(70, habState.humidityThreshold());
    assertEquals(65, habState.airHumidity());
    assertEquals(700, habState.co2());
  }

  @Test
  void shouldReadOutputItemValues() {
    // Output items should be readable from the items map, not just writable
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("hrvOutputPower", mockState(new DecimalType(75)));

    TestHabState state = HabStateFactory.of(TestHabState.class, itemStates);

    // Should read 75 from the items map, not the default value (50)
    assertEquals(75, state.hrvOutputPower());
  }

  @Test
  void shouldWriteStateToOutputItems() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("hrvOutputPower", mockState(new DecimalType(75)));
    TestHabState state = HabStateFactory.of(TestHabState.class, itemStates);
    state = state.withValue("hrvOutputPower", 75);
    ScriptBusEvent events = mock(ScriptBusEvent.class);

    HabStateFactory.writeState(events, state);

    verify(events).sendCommand("hrvOutputPower", "75");
  }

  @Test
  void withHrvOutputPowerShouldReturnNewStateWithUpdatedValue() {
    Map<String, State> itemStates = new HashMap<>();
    itemStates.put("hrvOutputPower", mockState(new DecimalType(50)));
    itemStates.put("hrvOutputIntake", mockState(new DecimalType(55)));
    itemStates.put("hrvOutputExhaust", mockState(new DecimalType(45)));
    TestHabState state = HabStateFactory.of(TestHabState.class, itemStates);

    // Simulate what HrvCalculator does - set new output values
    TestHabState newState = state
        .withValue("hrvOutputPower", 75)
        .withValue("hrvOutputIntake", 78)
        .withValue("hrvOutputExhaust", 72);

    // Original state should be unchanged
    assertEquals(50, state.hrvOutputPower());
    assertEquals(55, state.hrvOutputIntake());
    assertEquals(45, state.hrvOutputExhaust());

    // New state should have the updated values, NOT the original item values
    assertEquals(75, newState.hrvOutputPower());
    assertEquals(78, newState.hrvOutputIntake());
    assertEquals(72, newState.hrvOutputExhaust());
  }

  private State mockState(OnOffType type) {
    State state = mock(State.class);
    when(state.toString()).thenReturn(type == OnOffType.ON ? "ON" : "OFF");
    return state;
  }

  private State mockState(DecimalType type) {
    State state = mock(State.class);
    when(state.toString()).thenReturn(type.toString());
    return state;
  }
}
