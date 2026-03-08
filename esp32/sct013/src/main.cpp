#include <Arduino.h>
#include <WiFi.h>
#include <ArduinoOTA.h>
#include "config.h"
#include "current_sensor.h"
#include "mqtt_client.h"

// Use ESP-IDF logging (works reliably on ESP32-C3 USB Serial/JTAG)
static const char* TAG = "sct013";
#define LOG(fmt, ...) log_printf(ARDUHAL_LOG_FORMAT(I, fmt), ##__VA_ARGS__)

static CurrentSensor sensors[ADC_CHANNEL_COUNT];
static MqttClient mqtt;
static int lastPublished[ADC_CHANNEL_COUNT];
static unsigned long lastMeasureTime = 0;
static unsigned long lastPublishTime = 0;

static void connectWiFi() {
    LOG("WiFi: connecting to %s...", WIFI_SSID);
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
    }
    LOG("WiFi: connected, IP=%s", WiFi.localIP().toString().c_str());
}

void setup() {
    Serial.begin(115200);
    delay(2000);
    LOG("=== homeHAB SCT013 Current Sensor ===");

    // WiFi
    connectWiFi();

    // OTA
    ArduinoOTA.setHostname(OTA_HOSTNAME);
    ArduinoOTA.onStart([]() { LOG("OTA: update starting..."); });
    ArduinoOTA.onEnd([]() { LOG("OTA: done, rebooting"); });
    ArduinoOTA.onError([](ota_error_t err) { LOG("OTA: error %d", err); });
    ArduinoOTA.begin();
    LOG("OTA: ready at %s.local", OTA_HOSTNAME);

    // MQTT
    mqtt.begin(MQTT_BROKER, MQTT_PORT, MQTT_CLIENT_ID);
    mqtt.connect();

    // Initialize sensors
    for (size_t i = 0; i < ADC_CHANNEL_COUNT; i++) {
        sensors[i].begin(ADC_PINS[i], i);
        lastPublished[i] = -1; // force first publish
        LOG("Sensor ch%d: GPIO%d", i, ADC_PINS[i]);
    }

    LOG("Channels: %d, publish interval: %dms", ADC_CHANNEL_COUNT, PUBLISH_INTERVAL_MS);
}

void loop() {
    // OTA handler
    ArduinoOTA.handle();

    // Reconnect WiFi if needed
    if (WiFi.status() != WL_CONNECTED) {
        LOG("WiFi: lost connection, reconnecting...");
        connectWiFi();
    }

    // MQTT keep-alive / reconnect
    mqtt.loop();

    unsigned long now = millis();

    // Measure all channels at MEASURE_INTERVAL_MS
    if (now - lastMeasureTime >= MEASURE_INTERVAL_MS) {
        lastMeasureTime = now;
        for (size_t i = 0; i < ADC_CHANNEL_COUNT; i++) {
            sensors[i].readPowerFiltered();
        }
    }

    // Publish changed values at PUBLISH_INTERVAL_MS
    if (now - lastPublishTime >= PUBLISH_INTERVAL_MS) {
        lastPublishTime = now;

        if (!mqtt.connected()) return;

        for (size_t i = 0; i < ADC_CHANNEL_COUNT; i++) {
            int watts = sensors[i].readPowerFiltered();
            if (watts != lastPublished[i]) {
                mqtt.publishPower(i, watts);
                LOG("ch%d: %dW", i, watts);
                lastPublished[i] = watts;
            }
        }
    }
}
