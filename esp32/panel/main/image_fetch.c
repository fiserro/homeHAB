#include "image_fetch.h"
#include "esp_http_client.h"
#include "esp_log.h"
#include <string.h>
#include <stdlib.h>

static const char *TAG = "img_fetch";

int image_fetch_rgb565(const char *url, fetched_image_t *out)
{
    memset(out, 0, sizeof(*out));

    esp_http_client_config_t config = {
        .url = url,
        .timeout_ms = 30000,
        .buffer_size = 4096,
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
        ESP_LOGE(TAG, "HTTP %d for %s", status, url);
        esp_http_client_close(client);
        esp_http_client_cleanup(client);
        return -1;
    }

    // Allocate buffer from PSRAM
    int buf_size = content_length > 0 ? content_length + 1 : 1024 * 1024;
    if (buf_size > 2 * 1024 * 1024) buf_size = 2 * 1024 * 1024;
    uint8_t *buf = heap_caps_malloc(buf_size, MALLOC_CAP_SPIRAM);
    if (!buf) {
        ESP_LOGE(TAG, "PSRAM alloc failed (%d bytes)", buf_size);
        esp_http_client_close(client);
        esp_http_client_cleanup(client);
        return -1;
    }

    int total_read = 0;
    while (total_read < buf_size) {
        int r = esp_http_client_read(client, (char *)buf + total_read,
                                     buf_size - total_read);
        if (r <= 0) break;
        total_read += r;
    }

    esp_http_client_close(client);
    esp_http_client_cleanup(client);

    // Need at least 4-byte header (width + height)
    if (total_read < 4) {
        ESP_LOGE(TAG, "Response too short: %d bytes", total_read);
        free(buf);
        return -1;
    }

    // Parse header: uint16_t width, uint16_t height (little-endian)
    uint16_t w = buf[0] | (buf[1] << 8);
    uint16_t h = buf[2] | (buf[3] << 8);
    int expected = 4 + w * h * 2;

    if (total_read < expected) {
        ESP_LOGE(TAG, "Incomplete: got %d, expected %d (%dx%d)", total_read, expected, w, h);
        free(buf);
        return -1;
    }

    // Set up LVGL image descriptor pointing to pixel data (after header)
    out->data = buf;
    out->size = total_read;
    out->width = w;
    out->height = h;
    out->dsc.header.magic = LV_IMAGE_HEADER_MAGIC;
    out->dsc.header.cf = LV_COLOR_FORMAT_RGB565;
    out->dsc.header.w = w;
    out->dsc.header.h = h;
    out->dsc.data_size = w * h * 2;
    out->dsc.data = buf + 4;  // Skip 4-byte header

    ESP_LOGI(TAG, "Fetched RGB565: %dx%d, %d bytes", w, h, total_read);
    return 0;
}

void image_fetch_free(fetched_image_t *img)
{
    if (img && img->data) {
        free(img->data);
        img->data = NULL;
        img->dsc.data = NULL;
        img->size = 0;
    }
}
