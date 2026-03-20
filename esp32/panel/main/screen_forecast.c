#include "screen_forecast.h"
#include "image_fetch.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include <string.h>

static const char *TAG = "forecast";

#define C_BG       lv_color_hex(0x0d1117)
#define C_CARD     lv_color_hex(0x161b22)
#define C_CARD_BRD lv_color_hex(0x30363d)
#define C_TEXT     lv_color_hex(0xffffff)
#define C_TEXT_DIM lv_color_hex(0x8b949e)

#define CHART_URL "http://192.168.1.132:3030/weather/chart.rgb565"

static lv_obj_t *img_chart = NULL;
static lv_obj_t *lbl_status = NULL;
static lv_obj_t *home_img = NULL;  /* Home screen chart widget (set externally) */

/* Double buffer: fetch writes to pending, main loop swaps to active */
static fetched_image_t active_img = {0};
static fetched_image_t pending_img = {0};
static volatile bool chart_ready = false;

/* Background fetch - only does HTTP, never touches LVGL */
static void fetch_task(void *arg)
{
    vTaskDelay(pdMS_TO_TICKS(15000));
    while (1) {
        fetched_image_t tmp = {0};
        if (image_fetch_rgb565(CHART_URL, &tmp) == 0) {
            /* If previous pending wasn't consumed yet, free it */
            if (chart_ready) {
                image_fetch_free(&pending_img);
            }
            pending_img = tmp;
            chart_ready = true;  /* Signal main loop */
            ESP_LOGI(TAG, "Chart fetched %dx%d", tmp.width, tmp.height);
        }
        vTaskDelay(pdMS_TO_TICKS(600000));
    }
}

/* Called from main loop (LVGL thread) - safe to touch LVGL */
void screen_forecast_update(void)
{
    if (!chart_ready) return;
    chart_ready = false;

    fetched_image_t old = active_img;
    active_img = pending_img;
    memset(&pending_img, 0, sizeof(pending_img));

    lv_image_set_src(img_chart, &active_img.dsc);
    lv_obj_clear_flag(img_chart, LV_OBJ_FLAG_HIDDEN);
    lv_label_set_text(lbl_status, "");
    /* Also update home screen chart */
    if (home_img) {
        lv_image_set_src(home_img, &active_img.dsc);
        lv_obj_clear_flag(home_img, LV_OBJ_FLAG_HIDDEN);
    }
    image_fetch_free(&old);

    ESP_LOGI(TAG, "Chart displayed %dx%d", active_img.width, active_img.height);
}

lv_obj_t *screen_forecast_create(void)
{
    lv_obj_t *scr = lv_obj_create(NULL);
    lv_obj_set_style_bg_color(scr, C_BG, 0);
    lv_obj_set_style_bg_opa(scr, LV_OPA_COVER, 0);
    lv_obj_clear_flag(scr, LV_OBJ_FLAG_SCROLLABLE);

    lv_obj_t *title = lv_label_create(scr);
    lv_label_set_text(title, "Predpoved pocasi");
    lv_obj_set_style_text_color(title, C_TEXT, 0);
    lv_obj_set_style_text_font(title, &lv_font_montserrat_18, 0);
    lv_obj_set_pos(title, 24, 16);

    lv_obj_t *card = lv_obj_create(scr);
    lv_obj_set_pos(card, 16, 50);
    lv_obj_set_size(card, 688, 640);
    lv_obj_set_style_bg_color(card, C_CARD, 0);
    lv_obj_set_style_bg_opa(card, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(card, 16, 0);
    lv_obj_set_style_border_width(card, 1, 0);
    lv_obj_set_style_border_color(card, C_CARD_BRD, 0);
    lv_obj_set_style_pad_all(card, 0, 0);
    lv_obj_set_scrollbar_mode(card, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(card, LV_OBJ_FLAG_SCROLLABLE | LV_OBJ_FLAG_CLICKABLE);
    lv_obj_add_flag(card, LV_OBJ_FLAG_GESTURE_BUBBLE | LV_OBJ_FLAG_EVENT_BUBBLE);

    img_chart = lv_image_create(card);
    lv_obj_set_pos(img_chart, 0, 0);
    lv_obj_add_flag(img_chart, LV_OBJ_FLAG_HIDDEN);
    lv_obj_add_flag(img_chart, LV_OBJ_FLAG_GESTURE_BUBBLE | LV_OBJ_FLAG_EVENT_BUBBLE);

    lbl_status = lv_label_create(card);
    lv_label_set_text(lbl_status, "Nacitani...");
    lv_obj_set_style_text_color(lbl_status, C_TEXT_DIM, 0);
    lv_obj_set_style_text_font(lbl_status, &lv_font_montserrat_16, 0);
    lv_obj_align(lbl_status, LV_ALIGN_CENTER, 0, 0);

    xTaskCreate(fetch_task, "chart", 8192, NULL, 1, NULL);

    return scr;
}

void screen_forecast_refresh(void) {}

void screen_forecast_set_home_img(lv_obj_t *img)
{
    home_img = img;
}
