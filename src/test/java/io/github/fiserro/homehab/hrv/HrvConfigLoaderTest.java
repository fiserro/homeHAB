package io.github.fiserro.homehab.hrv;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    private Item humidityItem;

    @Mock
    private Item co2Item;

    private HrvConfigLoader configLoader;

    @BeforeEach
    void setUp() {
        configLoader = new HrvConfigLoader(itemRegistry);
    }

    @Test
    void loadConfiguration_withNoItems_shouldUseDefaults() {
        // Given: No items exist in registry
        when(itemRegistry.get(anyString())).thenReturn(null);

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
    void loadConfiguration_withSomeItems_shouldOverrideDefaults() {
        // Given: Some items exist with custom values
        when(itemRegistry.get("hrv_config_humidity_threshold")).thenReturn(humidityItem);
        when(humidityItem.getState()).thenReturn(new DecimalType(80));

        when(itemRegistry.get("hrv_config_co2_threshold")).thenReturn(co2Item);
        when(co2Item.getState()).thenReturn(new DecimalType(1200));

        when(itemRegistry.get(argThat(name ->
            !name.equals("hrv_config_humidity_threshold") &&
            !name.equals("hrv_config_co2_threshold")))).thenReturn(null);

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
        mockItem("hrv_config_humidity_threshold", 85);
        mockItem("hrv_config_co2_threshold", 1500);
        mockItem("hrv_config_smoke_power", 90);
        mockItem("hrv_config_window_open_power", 5);
        mockItem("hrv_config_manual_default_power", 60);
        mockItem("hrv_config_boost_power", 75);
        mockItem("hrv_config_exhaust_hood_power", 70);
        mockItem("hrv_config_humidity_power", 65);
        mockItem("hrv_config_co2_power", 55);
        mockItem("hrv_config_base_power", 35);
        mockItem("hrv_config_temporary_mode_timeout_minutes", 45);

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
    void loadConfiguration_withItemRegistryException_shouldUseDefaults() {
        // Given: ItemRegistry throws exception
        when(itemRegistry.get(anyString())).thenThrow(new RuntimeException("Registry error"));

        // When
        HrvConfig config = configLoader.loadConfiguration();

        // Then: Should not fail, use defaults
        assertNotNull(config);
        assertEquals(70, config.humidityThreshold());
        assertEquals(1000, config.co2Threshold());
    }

    @Test
    void loadConfiguration_withNonDecimalState_shouldUseDefaults() {
        // Given: Item exists but has non-decimal state
        when(itemRegistry.get("hrv_config_humidity_threshold")).thenReturn(humidityItem);
        when(humidityItem.getState()).thenReturn(null);  // Not a DecimalType

        // When
        HrvConfig config = configLoader.loadConfiguration();

        // Then: Should use default value
        assertEquals(70, config.humidityThreshold());
    }

    /**
     * Helper method to mock an item with a decimal value
     */
    private void mockItem(String itemName, int value) {
        Item item = mock(Item.class);
        when(itemRegistry.get(itemName)).thenReturn(item);
        when(item.getState()).thenReturn(new DecimalType(value));
    }
}
