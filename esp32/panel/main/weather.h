#pragma once
#include "esp_heap_caps.h"

#define WEATHER_MAX_HOURS 12

typedef struct {
    int hour;           // 0-23
    float temperature;
    int icon;           // meteosource icon code
    char summary[32];   // Czech description
    float wind_speed;
    char wind_dir[4];   // N, NE, S, etc.
    float precip_mm;
} weather_hour_t;

// Fetch hourly forecast. Returns 0 on success.
int weather_fetch(weather_hour_t *hours, int max_hours, int *out_count);
