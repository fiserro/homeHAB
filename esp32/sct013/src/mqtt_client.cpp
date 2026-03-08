#include "mqtt_client.h"
#include <stdio.h>

#define LOG(fmt, ...) log_printf(ARDUHAL_LOG_FORMAT(I, fmt), ##__VA_ARGS__)

void MqttClient::begin(const char* broker, uint16_t port, const char* clientId) {
    _broker = broker;
    _port = port;
    _clientId = clientId;
    _mqttClient.setClient(_wifiClient);
    _mqttClient.setServer(_broker, _port);
}

bool MqttClient::connect() {
    LOG("MQTT: connecting to %s:%d as %s...", _broker, _port, _clientId);

    // Connect with LWT: status topic -> "offline" (retained)
    bool ok = _mqttClient.connect(
        _clientId,
        nullptr, nullptr,             // no auth
        MQTT_STATUS_TOPIC, 1, true,   // LWT: topic, QoS 1, retained
        "offline"
    );

    if (ok) {
        LOG("MQTT: connected");
        // Birth message
        _mqttClient.publish(MQTT_STATUS_TOPIC, "online", true);
    } else {
        LOG("MQTT: connect failed, rc=%d", _mqttClient.state());
    }

    return ok;
}

void MqttClient::loop() {
    if (_mqttClient.connected()) {
        _mqttClient.loop();
        return;
    }

    // Auto-reconnect with 5s backoff
    unsigned long now = millis();
    if (now - _lastReconnectAttempt >= 5000) {
        _lastReconnectAttempt = now;
        if (connect()) {
            _lastReconnectAttempt = 0;
        }
    }
}

void MqttClient::publishPower(uint8_t channel, int watts) {
    char topic[64];
    snprintf(topic, sizeof(topic), "%s%d", MQTT_POWER_TOPIC_PREFIX, channel);

    char payload[16];
    snprintf(payload, sizeof(payload), "%d", watts);

    _mqttClient.publish(topic, payload, true); // retained
}

bool MqttClient::connected() {
    return _mqttClient.connected();
}
