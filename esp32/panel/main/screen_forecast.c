#include "screen_forecast.h"
#include "image_fetch.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <string.h>

static const char *TAG = "forecast";

#define CHART_URL "http://192.168.1.132:3030/weather/chart.rgb565"

static lv_obj_t *home_img = NULL;
static fetched_image_t active_img = {0};
static fetched_image_t pending_img = {0};
static volatile bool chart_ready = false;

static void fetch_task(void *arg)
{
    vTaskDelay(pdMS_TO_TICKS(15000));
    while (1) {
        fetched_image_t tmp = {0};
        if (image_fetch_rgb565(CHART_URL, &tmp) == 0) {
            if (chart_ready) image_fetch_free(&pending_img);
            pending_img = tmp;
            chart_ready = true;
            ESP_LOGI(TAG, "Chart fetched %dx%d", tmp.width, tmp.height);
        }
        vTaskDelay(pdMS_TO_TICKS(600000));
    }
}

void screen_forecast_init(void)
{
    xTaskCreate(fetch_task, "chart", 8192, NULL, 1, NULL);
}

void screen_forecast_set_home_img(lv_obj_t *img)
{
    home_img = img;
}

void screen_forecast_update(void)
{
    if (!chart_ready) return;
    chart_ready = false;

    fetched_image_t old = active_img;
    active_img = pending_img;
    memset(&pending_img, 0, sizeof(pending_img));

    if (home_img) {
        lv_image_set_src(home_img, &active_img.dsc);
        lv_obj_clear_flag(home_img, LV_OBJ_FLAG_HIDDEN);
    }
    image_fetch_free(&old);
    ESP_LOGI(TAG, "Chart displayed %dx%d", active_img.width, active_img.height);
}
