/**
 * Two screens with swipe detection - no tileview.
 * Uses lv_screen_load_anim for transitions.
 */
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "lvgl.h"
#include "bsp/esp-bsp.h"
#include "bsp/display.h"
#include "bsp_board_extra.h"

static lv_obj_t *screen1;
static lv_obj_t *screen2;
static lv_obj_t *dots1[2];
static lv_obj_t *dots2[2];
static int current_screen = 0;

static void create_dots(lv_obj_t *parent, lv_obj_t *dot_arr[], int active)
{
    lv_obj_t *footer = lv_obj_create(parent);
    lv_obj_set_size(footer, 720, 30);
    lv_obj_align(footer, LV_ALIGN_BOTTOM_MID, 0, 0);
    lv_obj_set_style_bg_color(footer, lv_color_hex(0x1a1a2e), 0);
    lv_obj_set_style_bg_opa(footer, LV_OPA_COVER, 0);
    lv_obj_set_style_border_width(footer, 0, 0);
    lv_obj_set_flex_flow(footer, LV_FLEX_FLOW_ROW);
    lv_obj_set_flex_align(footer, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER);
    lv_obj_set_style_pad_column(footer, 12, 0);
    lv_obj_set_scrollbar_mode(footer, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(footer, LV_OBJ_FLAG_SCROLLABLE);

    for (int i = 0; i < 2; i++) {
        dot_arr[i] = lv_obj_create(footer);
        lv_obj_set_size(dot_arr[i], 10, 10);
        lv_obj_set_style_radius(dot_arr[i], 5, 0);
        lv_obj_set_style_border_width(dot_arr[i], 0, 0);
        lv_obj_set_style_pad_all(dot_arr[i], 0, 0);
        lv_obj_set_style_bg_color(dot_arr[i],
            (i == active) ? lv_color_white() : lv_color_hex(0x666666), 0);
        lv_obj_set_style_bg_opa(dot_arr[i], LV_OPA_COVER, 0);
        lv_obj_clear_flag(dot_arr[i], LV_OBJ_FLAG_SCROLLABLE);
    }
}

static void on_gesture(lv_event_t *e)
{
    lv_dir_t dir = lv_indev_get_gesture_dir(lv_indev_active());

    if (dir == LV_DIR_LEFT && current_screen == 0) {
        current_screen = 1;
        lv_screen_load_anim(screen2, LV_SCR_LOAD_ANIM_MOVE_LEFT, 300, 0, false);
    } else if (dir == LV_DIR_RIGHT && current_screen == 1) {
        current_screen = 0;
        lv_screen_load_anim(screen1, LV_SCR_LOAD_ANIM_MOVE_RIGHT, 300, 0, false);
    }
}

void app_main(void)
{
    bsp_display_cfg_t cfg = {
        .lvgl_port_cfg = ESP_LVGL_PORT_INIT_CONFIG(),
        .buffer_size = BSP_LCD_DRAW_BUFF_SIZE,
        .double_buffer = BSP_LCD_DRAW_BUFF_DOUBLE,
        .flags = {
            .buff_dma = true,
            .buff_spiram = false,
            .sw_rotate = false,
        }};
    bsp_display_start_with_config(&cfg);
    bsp_display_backlight_on();

    // Screen 1 - Red
    screen1 = lv_obj_create(NULL);
    lv_obj_set_style_bg_color(screen1, lv_color_hex(0xcc0000), 0);
    lv_obj_set_style_bg_opa(screen1, LV_OPA_COVER, 0);
    lv_obj_clear_flag(screen1, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_event_cb(screen1, on_gesture, LV_EVENT_GESTURE, NULL);

    lv_obj_t *l1 = lv_label_create(screen1);
    lv_label_set_text(l1, "Screen 1 - HRV");
    lv_obj_set_style_text_color(l1, lv_color_white(), 0);
    lv_obj_align(l1, LV_ALIGN_CENTER, 0, 0);

    create_dots(screen1, dots1, 0);

    // Screen 2 - Blue
    screen2 = lv_obj_create(NULL);
    lv_obj_set_style_bg_color(screen2, lv_color_hex(0x0000cc), 0);
    lv_obj_set_style_bg_opa(screen2, LV_OPA_COVER, 0);
    lv_obj_clear_flag(screen2, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_event_cb(screen2, on_gesture, LV_EVENT_GESTURE, NULL);

    lv_obj_t *l2 = lv_label_create(screen2);
    lv_label_set_text(l2, "Screen 2 - Hello");
    lv_obj_set_style_text_color(l2, lv_color_white(), 0);
    lv_obj_align(l2, LV_ALIGN_CENTER, 0, 0);

    create_dots(screen2, dots2, 1);

    // Load screen 1
    lv_screen_load(screen1);

    while (true) {
        vTaskDelay(pdMS_TO_TICKS(5));
        lv_task_handler();
    }
}
