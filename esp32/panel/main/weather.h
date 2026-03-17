#pragma once

typedef struct {
    float temperature;
    int humidity;
    float wind_speed;
    int weather_code;   // WMO weather code
    char description[32];
} weather_data_t;

// Fetch weather from Open-Meteo API. Returns ESP_OK on success.
int weather_fetch(weather_data_t *out);

// Convert WMO weather code to Czech description
const char *weather_code_to_text(int code);
