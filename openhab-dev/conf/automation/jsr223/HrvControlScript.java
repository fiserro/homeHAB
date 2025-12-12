import helper.generated.Java223Script;
import helper.rules.annotations.ItemStateChangeTrigger;
import helper.rules.annotations.Rule;
import io.github.fiserro.homehab.hrv.HrvInputType;
import io.github.fiserro.homehab.hrv.HrvRule;
import org.openhab.core.items.Item;

/**
 * HRV (Heat Recovery Ventilator) control script.
 * Maps OpenHAB items to HRV inputs and controls ventilation power based on:
 * - Environmental sensors (humidity, CO2, smoke)
 * - Manual control modes
 * - Temporary boost modes
 */
public class HrvControlScript extends Java223Script {

    private HrvRule hrvRule;

    /**
     * Initialize HRV Rule on first trigger.
     * Creates HrvRule instance and maps all input/output items.
     */
    private void initializeIfNeeded() {
        if (hrvRule != null) {
            return; // Already initialized
        }

        logger.info("Initializing HRV Control Rule...");

        hrvRule = HrvRule.builder()
            // Boolean control modes
            .input(HrvInputType.MANUAL_MODE, "Hrv_Manual_Mode")
            .input(HrvInputType.TEMPORARY_MANUAL_MODE, "Hrv_Temporary_Manual_Mode")
            .input(HrvInputType.BOOST_MODE, "Hrv_Boost_Mode")
            .input(HrvInputType.TEMPORARY_BOOST_MODE, "Hrv_Temporary_Boost_Mode")

            // Boolean sensors
            .input(HrvInputType.EXHAUST_HOOD, "Hrv_Exhaust_Hood")
            .input(HrvInputType.WINDOW_OPEN, "Hrv_Window_Open")

            // Number sensors
            .input(HrvInputType.CO2, "Hrv_CO2")

            // Manual power control
            .input(HrvInputType.MANUAL_POWER, "Hrv_Manual_Power")

            // Output
            .output("Hrv_Power_Output")

            // Services
            .events(events)
            .itemRegistry(itemRegistry)
            .build();

        logger.info("HRV Control Rule initialized successfully");
    }

    /**
     * Universal trigger for all HRV item state changes.
     */
    @Rule(name = "hrv.item.changed", description = "Handle all HRV item changes")
    @ItemStateChangeTrigger(itemName = "Hrv_Manual_Mode")
    @ItemStateChangeTrigger(itemName = "Hrv_Temporary_Manual_Mode")
    @ItemStateChangeTrigger(itemName = "Hrv_Boost_Mode")
    @ItemStateChangeTrigger(itemName = "Hrv_Temporary_Boost_Mode")
    @ItemStateChangeTrigger(itemName = "Hrv_Exhaust_Hood")
    @ItemStateChangeTrigger(itemName = "Hrv_Window_Open")
    @ItemStateChangeTrigger(itemName = "Hrv_CO2")
    @ItemStateChangeTrigger(itemName = "Hrv_Manual_Power")
    @ItemStateChangeTrigger(itemName = "hrv_config_humidity_threshold")
    @ItemStateChangeTrigger(itemName = "hrv_config_co2_threshold")
    @ItemStateChangeTrigger(itemName = "hrv_config_smoke_power")
    @ItemStateChangeTrigger(itemName = "hrv_config_window_open_power")
    @ItemStateChangeTrigger(itemName = "hrv_config_manual_default_power")
    @ItemStateChangeTrigger(itemName = "hrv_config_boost_power")
    @ItemStateChangeTrigger(itemName = "hrv_config_exhaust_hood_power")
    @ItemStateChangeTrigger(itemName = "hrv_config_humidity_power")
    @ItemStateChangeTrigger(itemName = "hrv_config_co2_power")
    @ItemStateChangeTrigger(itemName = "hrv_config_base_power")
    @ItemStateChangeTrigger(itemName = "hrv_config_temporary_mode_timeout_minutes")
    public void onAnyItemChanged(String itemName) {
        // Lazy initialization on first trigger
        initializeIfNeeded();

        // Forward item change to HrvRule
        handleItemChange(itemName);
    }

    /**
     * Generic handler for item state changes.
     * Reads current item state and forwards to HrvRule.
     */
    private void handleItemChange(String itemName) {
        if (hrvRule == null) {
            logger.warn("HRV Rule not initialized yet, ignoring change for: {}", itemName);
            return;
        }

        try {
            Item item = itemRegistry.getItem(itemName);
            String state = item.getState().toString();

            logger.debug("Item changed: {} = {}", itemName, state);
            hrvRule.onInputChanged(itemName, state);

        } catch (Exception e) {
            logger.error("Error handling item change for {}: {}", itemName, e.getMessage(), e);
        }
    }
}
