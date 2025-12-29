import helper.generated.Items;
import helper.generated.Java223Script;
import helper.generated.mqtt.MQTTActions;
import helper.rules.annotations.ItemStateChangeTrigger;
import helper.rules.annotations.Rule;
import org.openhab.core.types.State;

/**
 * MQTT Bridge for ESP32 Panel.
 * Publishes OpenHAB item states to MQTT topics for the HRV control panel.
 * Uses retained messages so panel receives values even if it connects later.
 */
public class PanelMqttBridge extends Java223Script {

    private static final String MQTT_BROKER = "mqtt:broker:mosquitto";
    private static final String STATE_PREFIX = "homehab/state/";

    private MQTTActions mqtt() {
        return new MQTTActions(actions, MQTT_BROKER);
    }

    private void publish(String topic, State state) {
        if (state != null) {
            String value = state.toString();
            if (!value.equals("NULL") && !value.equals("UNDEF")) {
                mqtt().publishMQTT(STATE_PREFIX + topic, value, true);
                System.out.println("[PanelMqttBridge] Published " + topic + " = " + value);
            }
        }
    }

    @Rule(name = "panel.hrv.power", description = "Publish HRV output power to panel")
    @ItemStateChangeTrigger(itemName = Items.hrvOutputPower)
    public void onHrvPowerChanged() {
        publish("hrvOutputPower", _items.hrvOutputPower().getState());
    }

    @Rule(name = "panel.temperature", description = "Publish temperature to panel")
    @ItemStateChangeTrigger(itemName = Items.temperature)
    public void onTemperatureChanged() {
        publish("temperature", _items.temperature().getState());
    }

    @Rule(name = "panel.humidity", description = "Publish air humidity to panel")
    @ItemStateChangeTrigger(itemName = Items.airHumidity)
    public void onHumidityChanged() {
        publish("airHumidity", _items.airHumidity().getState());
    }

    @Rule(name = "panel.co2", description = "Publish CO2 to panel")
    @ItemStateChangeTrigger(itemName = Items.co2)
    public void onCo2Changed() {
        publish("co2", _items.co2().getState());
    }

    @Rule(name = "panel.pressure", description = "Publish pressure to panel")
    @ItemStateChangeTrigger(itemName = Items.pressure)
    public void onPressureChanged() {
        publish("pressure", _items.pressure().getState());
    }

    @Rule(name = "panel.manual.mode", description = "Publish manual mode to panel")
    @ItemStateChangeTrigger(itemName = Items.manualMode)
    public void onManualModeChanged() {
        publish("manualMode", _items.manualMode().getState());
    }

    @Rule(name = "panel.boost.mode", description = "Publish temporary boost mode to panel")
    @ItemStateChangeTrigger(itemName = Items.temporaryBoostMode)
    public void onBoostModeChanged() {
        publish("temporaryBoostMode", _items.temporaryBoostMode().getState());
    }

    @Rule(name = "panel.temp.manual.mode", description = "Publish temporary manual mode to panel")
    @ItemStateChangeTrigger(itemName = Items.temporaryManualMode)
    public void onTempManualModeChanged() {
        publish("temporaryManualMode", _items.temporaryManualMode().getState());
    }

    @Rule(name = "panel.smoke", description = "Publish smoke alarm to panel")
    @ItemStateChangeTrigger(itemName = Items.smoke)
    public void onSmokeChanged() {
        publish("smoke", _items.smoke().getState());
    }

    @Rule(name = "panel.gas", description = "Publish gas alarm to panel")
    @ItemStateChangeTrigger(itemName = Items.gas)
    public void onGasChanged() {
        publish("gas", _items.gas().getState());
    }

    @Rule(name = "panel.manual.power", description = "Publish manual power to panel")
    @ItemStateChangeTrigger(itemName = Items.manualPower)
    public void onManualPowerChanged() {
        publish("manualPower", _items.manualPower().getState());
    }

    @Rule(name = "panel.bypass", description = "Publish bypass state to panel")
    @ItemStateChangeTrigger(itemName = Items.bypass)
    public void onBypassChanged() {
        publish("bypass", _items.bypass().getState());
    }

}
