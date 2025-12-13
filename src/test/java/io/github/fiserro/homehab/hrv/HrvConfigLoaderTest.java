package io.github.fiserro.homehab.hrv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.core.automation.module.script.defaultscope.ScriptBusEvent;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.types.DecimalType;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for HrvConfigLoader with mocked ItemRegistry.
 */
@ExtendWith(MockitoExtension.class)
class HrvConfigLoaderTest {

    @Mock
    private ItemRegistry itemRegistry;

    @Mock
    private ScriptBusEvent events;

    @Mock
    private Item humidityItem;

    @Mock
    private Item co2Item;

    private HrvConfigLoader configLoader;

    @BeforeEach
    void setUp() {
        configLoader = new HrvConfigLoader(itemRegistry, events);
    }

    @Test
    void loadConfiguration_withNoItems_shouldUseDefaults() throws Exception {
        // Given: No items exist in registry
        when(itemRegistry.getItem(anyString())).thenThrow(new org.openhab.core.items.ItemNotFoundException(""));

        // When
        HrvConfig config = configLoader.loadConfiguration();

        // Then: All values should be defaults from interface
        assertEquals(70, config.humidityThreshold());
        assertEquals(1000, config.co2Threshold());
        assertEquals(100, config.smokePower());
        assertEquals(0, config.windowOpenPower());
        assertEquals(50, config.manualDefaultPower());
        assertEquals(80, config.boostPower());
        assertEquals(60, config.exhaustHoodPower());
        assertEquals(60, config.humidityPower());
        assertEquals(50, config.co2Power());
        assertEquals(30, config.basePower());
        assertEquals(30, config.temporaryModeTimeoutMinutes());
    }

    @Test
    void loadConfiguration_withSomeItems_shouldOverrideDefaults() throws Exception {
        // Given: Some items exist with custom values
        when(itemRegistry.getItem("hrvConfigHumidityThreshold")).thenReturn(humidityItem);
        when(humidityItem.getState()).thenReturn(new DecimalType(80));

        when(itemRegistry.getItem("hrvConfigCo2Threshold")).thenReturn(co2Item);
        when(co2Item.getState()).thenReturn(new DecimalType(1200));

        when(itemRegistry.getItem(argThat(name ->
            !name.equals("hrvConfigHumidityThreshold") &&
            !name.equals("hrvConfigCo2Threshold")))).thenThrow(new org.openhab.core.items.ItemNotFoundException(""));

        // When
        HrvConfig config = configLoader.loadConfiguration();

        // Then: Custom values from items, others are defaults
        assertEquals(80, config.humidityThreshold());  // From item
        assertEquals(1200, config.co2Threshold());      // From item
        assertEquals(100, config.smokePower());         // Default
        assertEquals(0, config.windowOpenPower());      // Default
        assertEquals(50, config.manualDefaultPower());  // Default
    }

    @Test
    void loadConfiguration_withAllItems_shouldUseAllItemValues() throws Exception {
        // Given: All items exist with custom values
        mockItem("hrvConfigHumidityThreshold", 85);
        mockItem("hrvConfigCo2Threshold", 1500);
        mockItem("hrvConfigSmokePower", 90);
        mockItem("hrvConfigWindowOpenPower", 5);
        mockItem("hrvConfigManualDefaultPower", 60);
        mockItem("hrvConfigBoostPower", 75);
        mockItem("hrvConfigExhaustHoodPower", 70);
        mockItem("hrvConfigHumidityPower", 65);
        mockItem("hrvConfigCo2Power", 55);
        mockItem("hrvConfigBasePower", 35);
        mockItem("hrvConfigTemporaryModeTimeoutMinutes", 45);

        // When
        HrvConfig config = configLoader.loadConfiguration();

        // Then: All values from items
        assertEquals(85, config.humidityThreshold());
        assertEquals(1500, config.co2Threshold());
        assertEquals(90, config.smokePower());
        assertEquals(5, config.windowOpenPower());
        assertEquals(60, config.manualDefaultPower());
        assertEquals(75, config.boostPower());
        assertEquals(70, config.exhaustHoodPower());
        assertEquals(65, config.humidityPower());
        assertEquals(55, config.co2Power());
        assertEquals(35, config.basePower());
        assertEquals(45, config.temporaryModeTimeoutMinutes());
    }

    @Test
    void loadConfiguration_withItemRegistryException_shouldUseDefaults() throws Exception {
        // Given: ItemRegistry throws exception
        when(itemRegistry.getItem(anyString())).thenThrow(new RuntimeException("Registry error"));

        // When
        HrvConfig config = configLoader.loadConfiguration();

        // Then: Should not fail, use defaults
        assertNotNull(config);
        assertEquals(70, config.humidityThreshold());
        assertEquals(1000, config.co2Threshold());
    }

    @Test
    void loadConfiguration_withNonDecimalState_shouldUseDefaults() throws Exception {
        // Given: Item exists but has non-decimal state
        when(itemRegistry.getItem("hrvConfigHumidityThreshold")).thenReturn(humidityItem);
        when(humidityItem.getState()).thenReturn(null);  // Not a DecimalType

        // When
        HrvConfig config = configLoader.loadConfiguration();

        // Then: Should use default value
        assertEquals(70, config.humidityThreshold());
    }

    /**
     * Helper method to mock an item with a decimal value
     */
    private void mockItem(String itemName, int value) throws Exception {
        Item item = mock(Item.class);
        when(itemRegistry.getItem(itemName)).thenReturn(item);
        when(item.getState()).thenReturn(new DecimalType(value));
    }
}
