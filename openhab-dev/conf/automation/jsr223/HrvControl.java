import helper.generated.Java223Script;
import helper.rules.annotations.Rule;
import helper.rules.annotations.ItemStateChangeTrigger;
import helper.rules.eventinfo.ItemStateChange;
import io.github.fiserro.homehab.OutputItem;
import io.github.fiserro.homehab.hrv.HrvRule;
import io.github.fiserro.homehab.hrv.HrvInputType;
import org.openhab.core.items.Item;
import org.openhab.automation.java223.common.InjectBinding;

/**
 * HRV (Heat Recovery Ventilator) control script.
 * Split into multiple rules due to annotation processing limitations.
 */
public class HrvControl extends Java223Script {

    @OutputItem(type = "Dimmer", label = "HRV Output Power", icon = "fan")
    public static final String OUTPUT_ITEM = "hrv_output_power";

    @InjectBinding(enable = false)
    private HrvRule hrvRule;

    private void initializeIfNeeded() {
        if (hrvRule != null) {
            return;
        }

        logger.info("Initializing HRV Control Rule...");

        hrvRule = HrvRule.builder()
            // Boolean modes (manual control)
            .input(HrvInputType.MANUAL_MODE, "hrv_item_manual_mode")
            .input(HrvInputType.TEMPORARY_MANUAL_MODE, "hrv_item_temporary_manual_mode")
            .input(HrvInputType.BOOST_MODE, "hrv_item_boost_mode")
            .input(HrvInputType.TEMPORARY_BOOST_MODE, "hrv_item_temporary_boost_mode")
            .input(HrvInputType.EXHAUST_HOOD, "hrv_item_exhaust_hood")
            .input(HrvInputType.SMOKE_DETECTOR, "hrv_zigbee_item_0xa4c138aa8b540e22_smoke")
            .input(HrvInputType.HUMIDITY, "hrv_zigbee_item_0x00158d008b8b7beb_humidity")
            // .input(HrvInputType.WINDOW_OPEN, "hrv_zigbee_item_<ieee>_contact")  // Add when sensor available
            // .input(HrvInputType.CO2, "hrv_zigbee_item_<ieee>_co2")  // Add when sensor available
            // Manual power control
            .input(HrvInputType.MANUAL_POWER, "hrv_item_manual_power")
            // Output
            .output(OUTPUT_ITEM)
            .events(events)
            .itemRegistry(itemRegistry)
            .build();

        logger.info("HRV Control Rule initialized successfully");
    }

    @Rule(name = "hrv.zigbee.changed", description = "Handle HRV Zigbee sensor changes")
    @ItemStateChangeTrigger(itemName = "mqttZigbee*")
    public void onZigbeeItemChanged(ItemStateChange eventInfo) {
        initializeIfNeeded();
        handleItemChange(eventInfo.getItemName());
    }

    @Rule(name = "hrv.config.changed", description = "Handle HRV config changes")
    @ItemStateChangeTrigger(itemName = "hrvConfig*")
    public void onConfigChanged(ItemStateChange eventInfo) {
        initializeIfNeeded();
        // Config items don't need to be passed to HrvRule - they're loaded via HrvConfigLoader
        logger.debug("Config item changed: {} = {}", eventInfo.getItemName(), eventInfo.getItemState());
        // TODO: In future, could reload configuration here if needed
    }


    private void handleItemChange(String itemName) {
        // Ignore output item changes to prevent feedback loop
        if (itemName.startsWith("hrv_output_")) {
            logger.debug("Ignoring output item change: {}", itemName);
            return;
        }

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
