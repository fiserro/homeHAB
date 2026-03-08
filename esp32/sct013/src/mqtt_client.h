#pragma once

#include <Arduino.h>
#include <PubSubClient.h>
#include <WiFi.h>
#include "config.h"

/**
 * Thin MQTT wrapper for publishing SCT013 power readings.
 * Handles connection, reconnection, LWT, and retained messages.
 */
class MqttClient {
public:
    /** Configure MQTT connection parameters. */
    void begin(const char* broker, uint16_t port, const char* clientId);

    /** Connect to broker. Sets LWT and publishes birth message. Returns true on success. */
    bool connect();

    /** Keep connection alive and auto-reconnect if needed. Call in loop(). */
    void loop();

    /** Publish power reading for a channel (retained). */
    void publishPower(uint8_t channel, int watts);

    /** Returns true if connected to broker. */
    bool connected();

private:
    WiFiClient _wifiClient;
    PubSubClient _mqttClient;
    const char* _broker = nullptr;
    uint16_t _port = 0;
    const char* _clientId = nullptr;
    unsigned long _lastReconnectAttempt = 0;
};
