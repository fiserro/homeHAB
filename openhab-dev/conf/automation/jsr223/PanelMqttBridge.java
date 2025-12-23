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
    private static final String PREFIX = "homehab-dev/";

    private MQTTActions mqtt() {
        return new MQTTActions(actions, MQTT_BROKER);
    }

    private void publish(String topic, State state) {
        if (state != null) {
            String value = state.toString();
            if (!value.equals("NULL") && !value.equals("UNDEF")) {
                mqtt().publishMQTT(PREFIX + topic + "/state", value, true);
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

    @Rule(name = "panel.manual.power", description = "Publish manual power to panel")
    @ItemStateChangeTrigger(itemName = Items.manualPower)
    public void onManualPowerChanged() {
        publish("manualPower", _items.manualPower().getState());
    }

    // Handle commands FROM panel
    @Rule(name = "panel.cmd.manual.power", description = "Handle manual power command from panel")
    @ItemStateChangeTrigger(itemName = Items.panelManualPowerCommand)
    public void onPanelManualPowerCommand() {
        State state = _items.panelManualPowerCommand().getState();
        if (state != null) {
            events.sendCommand(_items.manualPower(), state.toString());
            System.out.println("[PanelMqttBridge] Panel command: manualPower = " + state);
        }
    }

    @Rule(name = "panel.cmd.manual.mode", description = "Handle manual mode command from panel")
    @ItemStateChangeTrigger(itemName = Items.panelManualModeCommand)
    public void onPanelManualModeCommand() {
        State state = _items.panelManualModeCommand().getState();
        if (state != null) {
            events.sendCommand(_items.manualMode(), state.toString());
            System.out.println("[PanelMqttBridge] Panel command: manualMode = " + state);
        }
    }

    @Rule(name = "panel.cmd.boost.mode", description = "Handle boost mode command from panel")
    @ItemStateChangeTrigger(itemName = Items.panelBoostModeCommand)
    public void onPanelBoostModeCommand() {
        State state = _items.panelBoostModeCommand().getState();
        if (state != null) {
            events.sendCommand(_items.temporaryBoostMode(), state.toString());
            System.out.println("[PanelMqttBridge] Panel command: temporaryBoostMode = " + state);
        }
    }

    @Rule(name = "panel.cmd.temp.manual.mode", description = "Handle temporary manual mode command from panel")
    @ItemStateChangeTrigger(itemName = "panelTempManualModeCommand")
    public void onPanelTempManualModeCommand() {
        State state = items.get("panelTempManualModeCommand");
        if (state != null) {
            events.sendCommand(_items.temporaryManualMode(), state.toString());
            System.out.println("[PanelMqttBridge] Panel command: temporaryManualMode = " + state);
        }
    }
}
