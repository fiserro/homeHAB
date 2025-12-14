import helper.generated.Java223Script;
import helper.rules.annotations.Rule;
import helper.rules.annotations.ItemStateChangeTrigger;
import helper.rules.eventinfo.ItemStateChange;
import io.github.fiserro.homehab.OutputItem;
import io.github.fiserro.homehab.hrv.HrvRule;
import io.github.fiserro.homehab.InputType;
import org.openhab.core.items.Item;
import org.openhab.automation.java223.common.InjectBinding;

/**
 * HRV (Heat Recovery Ventilator) control script.
 * Split into multiple rules due to annotation processing limitations.
 */
public class HrvControl extends Java223Script {

    @OutputItem(type = "Dimmer", label = "HRV Output Power", icon = "fan")

    @InjectBinding(enable = false)
    private HrvRule hrvRule;

    private void initializeIfNeeded() {
        if (hrvRule != null) {
            return;
        }

        logger.info("Initializing HRV Control Rule...");

        hrvRule = HrvRule.builder()
            // @InputItem - user inputs (generated from HrvState)
            .input(InputType.MANUAL_MODE, _items.hrv_manual_mode())
            .input(InputType.TEMPORARY_MANUAL_MODE, _items.hrv_temporary_manual_mode())
            .input(InputType.BOOST_MODE, _items.hrv_boost_mode())
            .input(InputType.TEMPORARY_BOOST_MODE, _items.hrv_temporary_boost_mode())
            .input(InputType.HUMIDITY_THRESHOLD, _items.hrv_humidity_threshold())
            .input(InputType.CO2_THRESHOLD_LOW, _items.hrv_co2_threshold_low())
            .input(InputType.CO2_THRESHOLD_MID, _items.hrv_co2_threshold_mid())
            .input(InputType.CO2_THRESHOLD_HIGH, _items.hrv_co2_threshold_high())
            .input(InputType.HRV_MANUAL_POWER, _items.hrv_manual_power())
            .input(InputType.HRV_BOOST_POWER, _items.hrv_boost_power())
            .input(InputType.HRV_SMOKE_POWER, _items.hrv_smoke_power())
            .input(InputType.HRV_GAS_POWER, _items.hrv_gas_power())
            .input(InputType.HRV_HUMIDITY_POWER, _items.hrv_humidity_power())
            .input(InputType.HRV_CO2_POWER_LOW, _items.hrv_co2_power_low())
            .input(InputType.HRV_CO2_POWER_MID, _items.hrv_co2_power_mid())
            .input(InputType.HRV_CO2_POWER_HIGH, _items.hrv_co2_power_high())
            .input(InputType.HRV_BASE_POWER, _items.hrv_base_power())

            // @MqttItem - sensor inputs (multiple sensors per type supported via Multimap)
            .input(InputType.SMOKE, _items.mqttZigbeeSmoke_0xa4c138aa8b540e22())
            .input(InputType.HUMIDITY, _items.mqttZigbeeHumidity_0x00158d008b8b7beb())
            // Add more sensors as needed (Multimap allows multiple items per InputType):
            // .input(InputType.CO2, _items.mqttZigbeeCo2_<ieee>())
            // .input(InputType.GAS, _items.mqttZigbeeGas_<ieee>())

            // Output
            .output(_items.hrv_output_power())
            .events(events)
            .build();

        logger.info("HRV Control Rule initialized successfully");
    }

    @Rule(name = "hrv.zigbee.changed", description = "Handle HRV Zigbee sensor changes")
    @ItemStateChangeTrigger(itemName = "mqttZigbee*")
    public void onZigbeeItemChanged(ItemStateChange eventInfo) {
        initializeIfNeeded();
        handleItemChange(eventInfo.getItemName());
    }

    @Rule(name = "hrv.input.changed", description = "Handle HRV input item changes")
    @ItemStateChangeTrigger(itemName = "hrv_*")
    public void onInputChanged(ItemStateChange eventInfo) {
        initializeIfNeeded();
        handleItemChange(eventInfo.getItemName());
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
