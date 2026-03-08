#pragma once

// ── WiFi ────────────────────────────────────────────────────────────────────
// Override via build_flags in platformio.ini or create secrets.h
#if __has_include("secrets.h")
#include "secrets.h"
#endif

#ifndef WIFI_SSID
#define WIFI_SSID "your-ssid"
#endif

#ifndef WIFI_PASSWORD
#define WIFI_PASSWORD "your-password"
#endif

// ── MQTT ────────────────────────────────────────────────────────────────────
#ifndef MQTT_BROKER
#define MQTT_BROKER "zigbee.home"
#endif

#define MQTT_PORT 1883
#define MQTT_CLIENT_ID "homehab-sct013"
#define OTA_HOSTNAME "homehab-sct013"
#define MQTT_TOPIC_PREFIX "homehab/sct013/"
#define MQTT_STATUS_TOPIC MQTT_TOPIC_PREFIX "status"
#define MQTT_POWER_TOPIC_PREFIX MQTT_TOPIC_PREFIX "power/ch"

// ── SCT013 Sensor ───────────────────────────────────────────────────────────
#define SCT013_RATIO 5.0f          // SCT013-005: 5A input -> 1V output
#define MAINS_VOLTAGE 230.0f       // Mains voltage (V)

// ── ADC Channels ────────────────────────────────────────────────────────────
// ESP32-C3 ADC1 channels: GPIO0-GPIO4
// ADC2 is NOT available when WiFi is active
constexpr uint8_t ADC_PINS[] = {0};
constexpr size_t ADC_CHANNEL_COUNT = sizeof(ADC_PINS) / sizeof(ADC_PINS[0]);

// ── Sampling ────────────────────────────────────────────────────────────────
#define ADC_SAMPLES 200            // Samples per measurement (covers ~1 AC cycle at 50Hz)
#define ADC_SAMPLE_INTERVAL_US 100 // 100us between samples -> 10kHz effective rate

// ── Filtering ───────────────────────────────────────────────────────────────
#define NOISE_THRESHOLD_W 5.0f     // Below this, report 0W
#define MAX_POWER_W 500.0f         // Above this, consider sensor disconnected
#define EMA_ALPHA 0.3f             // EMA smoothing (0-1, lower = smoother)
#define SPIKE_FILTER_PERCENT 10    // Remove top/bottom N% of samples
#define MIN_VARIANCE_MV2 300.0f    // Minimum variance in mV^2 for real AC signal
#define SPIKE_RESET_THRESHOLD 50.0f // Reset EMA if change exceeds this (W)

// ── Timing ──────────────────────────────────────────────────────────────────
#define MEASURE_INTERVAL_MS 200    // Measure all channels every 200ms
#define PUBLISH_INTERVAL_MS 1000   // Publish via MQTT every 1s (only if changed)

// ── Channel Calibration ─────────────────────────────────────────────────────
// Per-channel calibration factors (compensates hardware differences)
constexpr float CHANNEL_CALIBRATION[] = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
