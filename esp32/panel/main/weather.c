#include "weather.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"
#include "esp_log.h"
#include <string.h>
#include <stdlib.h>

static const char *TAG = "weather";

#include "secrets.h"
#define METEOSOURCE_URL "https://www.meteosource.com/api/v1/free/point?lat=49.19&lon=16.61&sections=hourly&timezone=Europe/Prague&language=en&units=metric&key=" METEOSOURCE_API_KEY

// Simple JSON helpers
static const char *find_key(const char *json, const char *key)
{
    char search[64];
    snprintf(search, sizeof(search), "\"%s\":", key);
    return strstr(json, search);
}

static float get_float(const char *p)
{
    while (*p && (*p < '0' || *p > '9') && *p != '-') p++;
    return atof(p);
}

static int get_int(const char *p)
{
    return (int)get_float(p);
}

static void get_string(const char *p, char *out, int maxlen)
{
    // find opening quote after colon
    p = strchr(p, ':');
    if (!p) { out[0] = 0; return; }
    p = strchr(p, '"');
    if (!p) { out[0] = 0; return; }
    p++; // skip quote
    int i = 0;
    while (*p && *p != '"' && i < maxlen - 1) out[i++] = *p++;
    out[i] = 0;
}

// Parse one hourly entry starting at ptr, advance ptr past it
static int parse_hour(const char **ptr, weather_hour_t *h)
{
    const char *p = *ptr;
    // Find next { which starts an hour object
    p = strchr(p, '{');
    if (!p) return -1;

    // Find closing } at same level
    const char *end = p + 1;
    int depth = 1;
    while (*end && depth > 0) {
        if (*end == '{') depth++;
        else if (*end == '}') depth--;
        end++;
    }

    // Parse fields within this object
    const char *f;

    // date: "2025-09-24T09:00:00" -> extract hour
    f = find_key(p, "date");
    if (f) {
        // find T then read 2 digits for hour
        const char *t = strchr(f, 'T');
        if (t) h->hour = (t[1] - '0') * 10 + (t[2] - '0');
    }

    // temperature
    f = find_key(p, "temperature");
    if (f) h->temperature = get_float(f + 14);

    // icon
    f = find_key(p, "icon");
    if (f) h->icon = get_int(f + 6);

    // summary
    f = find_key(p, "summary");
    if (f) get_string(f, h->summary, sizeof(h->summary));

    // wind.speed - find "wind" then "speed" inside it
    f = find_key(p, "wind");
    if (f) {
        const char *ws = find_key(f, "speed");
        if (ws && ws < end) h->wind_speed = get_float(ws + 7);
        const char *wd = find_key(f, "dir");
        if (wd && wd < end) get_string(wd, h->wind_dir, sizeof(h->wind_dir));
    }

    // precipitation.total
    f = find_key(p, "precipitation");
    if (f) {
        const char *pt = find_key(f, "total");
        if (pt && pt < end) h->precip_mm = get_float(pt + 7);
    }

    *ptr = end;
    return 0;
}

int weather_fetch(weather_hour_t *hours, int max_hours, int *out_count)
{
    esp_http_client_config_t config = {
        .url = METEOSOURCE_URL,
        .timeout_ms = 15000,
        .buffer_size = 4096,
        .crt_bundle_attach = esp_crt_bundle_attach,
    };

    esp_http_client_handle_t client = esp_http_client_init(&config);
    if (!client) return -1;

    esp_err_t err = esp_http_client_open(client, 0);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "HTTP open failed: %s", esp_err_to_name(err));
        esp_http_client_cleanup(client);
        return -1;
    }

    int content_length = esp_http_client_fetch_headers(client);
    int status = esp_http_client_get_status_code(client);

    if (status != 200) {
        ESP_LOGE(TAG, "HTTP %d", status);
        esp_http_client_close(client);
        esp_http_client_cleanup(client);
        return -1;
    }

    // Read response - can be large, allocate from SPIRAM
    int buf_size = content_length > 0 ? content_length + 1 : 8192;
    if (buf_size > 32768) buf_size = 32768;
    char *buf = heap_caps_malloc(buf_size, MALLOC_CAP_SPIRAM);
    if (!buf) {
        esp_http_client_close(client);
        esp_http_client_cleanup(client);
        return -1;
    }

    int total_read = 0;
    while (total_read < buf_size - 1) {
        int r = esp_http_client_read(client, buf + total_read, buf_size - 1 - total_read);
        if (r <= 0) break;
        total_read += r;
    }
    buf[total_read] = '\0';

    esp_http_client_close(client);
    esp_http_client_cleanup(client);

    if (total_read < 10) {
        ESP_LOGE(TAG, "Response too short: %d", total_read);
        free(buf);
        return -1;
    }

    // Find "data" array inside "hourly"
    const char *hourly = strstr(buf, "\"hourly\"");
    if (!hourly) { ESP_LOGE(TAG, "No hourly"); free(buf); return -1; }
    const char *data = strstr(hourly, "\"data\"");
    if (!data) { ESP_LOGE(TAG, "No data"); free(buf); return -1; }
    const char *arr = strchr(data + 6, '[');
    if (!arr) { free(buf); return -1; }

    const char *ptr = arr + 1;
    int count = 0;
    while (count < max_hours) {
        memset(&hours[count], 0, sizeof(weather_hour_t));
        if (parse_hour(&ptr, &hours[count]) != 0) break;
        count++;
    }

    *out_count = count;
    ESP_LOGI(TAG, "Parsed %d hourly entries", count);
    if (count > 0) {
        ESP_LOGI(TAG, "First: %dh %.0fC icon=%d %s", hours[0].hour, hours[0].temperature, hours[0].icon, hours[0].summary);
    }

    free(buf);
    return 0;
}
