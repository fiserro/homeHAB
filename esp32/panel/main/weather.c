#include "weather.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include <string.h>
#include <stdlib.h>

static const char *TAG = "weather";

// Brno coordinates
#define WEATHER_URL "http://api.open-meteo.com/v1/forecast?latitude=49.19&longitude=16.61&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code&timezone=Europe/Prague"

const char *weather_code_to_text(int code)
{
    switch (code) {
        case 0: return "Jasno";
        case 1: return "Skoro jasno";
        case 2: return "Polojasno";
        case 3: return "Zatazeno";
        case 45: case 48: return "Mlha";
        case 51: case 53: case 55: return "Mrholeni";
        case 56: case 57: return "Mrz. mrholeni";
        case 61: case 63: case 65: return "Dest";
        case 66: case 67: return "Mrz. dest";
        case 71: case 73: case 75: return "Snezeni";
        case 77: return "Sn. zrna";
        case 80: case 81: case 82: return "Prehanky";
        case 85: case 86: return "Sn. prehanky";
        case 95: return "Bourka";
        case 96: case 99: return "Bourka s krupobitim";
        default: return "Neznamo";
    }
}

// Simple JSON value extractor (no full parser needed)
static float json_get_float(const char *json, const char *key)
{
    char search[64];
    snprintf(search, sizeof(search), "\"%s\":", key);
    const char *p = strstr(json, search);
    if (!p) return 0;
    p += strlen(search);
    while (*p == ' ') p++;
    return atof(p);
}

static int json_get_int(const char *json, const char *key)
{
    return (int)json_get_float(json, key);
}

int weather_fetch(weather_data_t *out)
{
    esp_http_client_config_t config = {
        .url = WEATHER_URL,
        .timeout_ms = 10000,
    };

    esp_http_client_handle_t client = esp_http_client_init(&config);
    if (!client) return -1;

    esp_err_t err = esp_http_client_open(client, 0);
    if (err != ESP_OK) {
        esp_http_client_cleanup(client);
        return -1;
    }

    int content_length = esp_http_client_fetch_headers(client);
    int status = esp_http_client_get_status_code(client);

    if (status != 200 || content_length <= 0 || content_length > 4096) {
        ESP_LOGE(TAG, "HTTP %d, len=%d", status, content_length);
        esp_http_client_close(client);
        esp_http_client_cleanup(client);
        return -1;
    }

    char *buf = malloc(content_length + 1);
    if (!buf) {
        esp_http_client_close(client);
        esp_http_client_cleanup(client);
        return -1;
    }

    int read = esp_http_client_read(client, buf, content_length);
    buf[read > 0 ? read : 0] = '\0';

    esp_http_client_close(client);
    esp_http_client_cleanup(client);

    if (read <= 0) {
        free(buf);
        return -1;
    }

    // Parse "current" block
    const char *current = strstr(buf, "\"current\"");
    if (!current) {
        ESP_LOGE(TAG, "No 'current' in response");
        free(buf);
        return -1;
    }

    out->temperature = json_get_float(current, "temperature_2m");
    out->humidity = json_get_int(current, "relative_humidity_2m");
    out->wind_speed = json_get_float(current, "wind_speed_10m");
    out->weather_code = json_get_int(current, "weather_code");
    strncpy(out->description, weather_code_to_text(out->weather_code), sizeof(out->description) - 1);

    ESP_LOGI(TAG, "Weather: %.1f C, %d%%, %.1f km/h, code=%d (%s)",
             out->temperature, out->humidity, out->wind_speed,
             out->weather_code, out->description);

    free(buf);
    return 0;
}
