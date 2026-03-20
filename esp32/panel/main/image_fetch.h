#pragma once
#include "lvgl.h"
#include "esp_heap_caps.h"

typedef struct {
    uint8_t *data;       // Raw pixel data in PSRAM (after 4-byte header)
    size_t size;         // Total data size including header
    uint16_t width;
    uint16_t height;
    lv_image_dsc_t dsc;  // LVGL image descriptor
} fetched_image_t;

// Fetch raw RGB565 image from URL into PSRAM. Returns 0 on success.
// The server provides a 4-byte header (uint16 width, uint16 height) followed by RGB565 pixels.
int image_fetch_rgb565(const char *url, fetched_image_t *out);

// Free fetched image data
void image_fetch_free(fetched_image_t *img);
