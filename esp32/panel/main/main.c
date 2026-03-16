/**
 * HRV Panel - two screens with swipe navigation.
 * Screen 1: HRV Controller (based on Figma design)
 * Screen 2: Hello World placeholder
 */
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_heap_caps.h"
#include "lvgl.h"
#include "bsp/esp-bsp.h"
#include "bsp/display.h"
#include "bsp_board_extra.h"

/* ── Colors from Figma ── */
#define C_BG         lv_color_hex(0x0d1117)
#define C_CARD       lv_color_hex(0x161b22)
#define C_CARD_BRD   lv_color_hex(0x30363d)
#define C_MODE_BG    lv_color_hex(0x161b22)
#define C_MODE_ACT   lv_color_hex(0x1a6eff)
#define C_TEXT        lv_color_hex(0xffffff)
#define C_TEXT_DIM    lv_color_hex(0x8b949e)
#define C_BAR_BG     lv_color_hex(0x30363d)
#define C_BAR_IND    lv_color_hex(0x4488ff)
#define C_GREEN       lv_color_hex(0x3fb950)
#define C_RED         lv_color_hex(0xf85149)
#define C_YELLOW      lv_color_hex(0xd29922)
#define C_BLUE        lv_color_hex(0x58a6ff)

/* ── Screen references ── */
#define NUM_PAGES 2
static lv_obj_t *screen1;
static lv_obj_t *screen2;
static int current_screen = 0;

/* ── Page dots ── */
static void create_dots(lv_obj_t *parent, int active)
{
    lv_obj_t *footer = lv_obj_create(parent);
    lv_obj_set_size(footer, 720, 24);
    lv_obj_align(footer, LV_ALIGN_BOTTOM_MID, 0, 0);
    lv_obj_set_style_bg_opa(footer, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(footer, 0, 0);
    lv_obj_set_flex_flow(footer, LV_FLEX_FLOW_ROW);
    lv_obj_set_flex_align(footer, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER);
    lv_obj_set_style_pad_column(footer, 10, 0);
    lv_obj_set_scrollbar_mode(footer, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(footer, LV_OBJ_FLAG_SCROLLABLE);

    for (int i = 0; i < NUM_PAGES; i++) {
        lv_obj_t *dot = lv_obj_create(footer);
        lv_obj_set_size(dot, 8, 8);
        lv_obj_set_style_radius(dot, 4, 0);
        lv_obj_set_style_border_width(dot, 0, 0);
        lv_obj_set_style_pad_all(dot, 0, 0);
        lv_obj_set_style_bg_color(dot, (i == active) ? C_TEXT : lv_color_hex(0x444444), 0);
        lv_obj_set_style_bg_opa(dot, LV_OPA_COVER, 0);
        lv_obj_clear_flag(dot, LV_OBJ_FLAG_SCROLLABLE);
    }
}

/* ── Gesture handler ── */
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

/* ── Stat card helper ── */
static lv_obj_t *create_stat_card(lv_obj_t *parent, int x, int y, int w, int h,
                                   const char *label_text, const char *value_text)
{
    lv_obj_t *card = lv_obj_create(parent);
    lv_obj_set_pos(card, x, y);
    lv_obj_set_size(card, w, h);
    lv_obj_set_style_bg_color(card, C_CARD, 0);
    lv_obj_set_style_bg_opa(card, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(card, 14, 0);
    lv_obj_set_style_border_width(card, 1, 0);
    lv_obj_set_style_border_color(card, C_CARD_BRD, 0);
    lv_obj_set_style_pad_left(card, 16, 0);
    lv_obj_set_style_pad_top(card, 12, 0);
    lv_obj_set_scrollbar_mode(card, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(card, LV_OBJ_FLAG_SCROLLABLE);

    lv_obj_t *lbl = lv_label_create(card);
    lv_label_set_text(lbl, label_text);
    lv_obj_set_style_text_color(lbl, C_TEXT_DIM, 0);
    lv_obj_set_style_text_font(lbl, &lv_font_montserrat_12, 0);
    lv_obj_set_pos(lbl, 0, 0);

    lv_obj_t *val = lv_label_create(card);
    lv_label_set_text(val, value_text);
    lv_obj_set_style_text_color(val, C_TEXT, 0);
    lv_obj_set_style_text_font(val, &lv_font_montserrat_22, 0);
    lv_obj_set_pos(val, 0, 22);

    return card;
}

/* ── Mode button helper ── */
static lv_obj_t *create_mode_btn(lv_obj_t *parent, int x, int w,
                                  const char *text, bool active)
{
    lv_obj_t *btn = lv_obj_create(parent);
    lv_obj_set_pos(btn, x, 0);
    lv_obj_set_size(btn, w, 56);
    lv_obj_set_style_bg_color(btn, active ? C_MODE_ACT : C_MODE_BG, 0);
    lv_obj_set_style_bg_opa(btn, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(btn, 14, 0);
    lv_obj_set_style_border_width(btn, active ? 0 : 1, 0);
    lv_obj_set_style_border_color(btn, C_CARD_BRD, 0);
    lv_obj_set_scrollbar_mode(btn, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(btn, LV_OBJ_FLAG_SCROLLABLE);

    lv_obj_t *lbl = lv_label_create(btn);
    lv_label_set_text(lbl, text);
    lv_obj_set_style_text_color(lbl, C_TEXT, 0);
    lv_obj_set_style_text_font(lbl, &lv_font_montserrat_12, 0);
    lv_obj_align(lbl, LV_ALIGN_BOTTOM_MID, 0, -6);

    return btn;
}

/* ── Build HRV Screen ── */
static void build_screen_hrv(lv_obj_t *scr)
{
    lv_obj_set_style_bg_color(scr, C_BG, 0);
    lv_obj_set_style_bg_opa(scr, LV_OPA_COVER, 0);
    lv_obj_clear_flag(scr, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_event_cb(scr, on_gesture, LV_EVENT_GESTURE, NULL);

    /* ── Header ── */
    lv_obj_t *title = lv_label_create(scr);
    lv_label_set_text(title, "HRV Controller");
    lv_obj_set_style_text_color(title, C_TEXT, 0);
    lv_obj_set_style_text_font(title, &lv_font_montserrat_22, 0);
    lv_obj_set_pos(title, 60, 30);

    lv_obj_t *subtitle = lv_label_create(scr);
    lv_label_set_text(subtitle, "Heat Recovery Ventilation");
    lv_obj_set_style_text_color(subtitle, C_TEXT_DIM, 0);
    lv_obj_set_style_text_font(subtitle, &lv_font_montserrat_12, 0);
    lv_obj_set_pos(subtitle, 60, 58);

    /* ── Stat cards (2 cols × 4 rows) ── */
    int card_w = 339;
    int card_h = 100;
    int gap = 10;
    int x1 = 16;
    int x2 = 16 + card_w + gap;
    int y_start = 80;

    create_stat_card(scr, x1, y_start,                      card_w, card_h, "Outdoor Temperature",  "5.3\xC2\xB0""C");
    create_stat_card(scr, x2, y_start,                      card_w, card_h, "Supply Temperature",   "18.3\xC2\xB0""C");
    create_stat_card(scr, x1, y_start + (card_h + gap),     card_w, card_h, "Extract Temperature",  "20.9\xC2\xB0""C");
    create_stat_card(scr, x2, y_start + (card_h + gap),     card_w, card_h, "Exhaust Temperature",  "12.2\xC2\xB0""C");
    create_stat_card(scr, x1, y_start + 2*(card_h + gap),   card_w, card_h, "Room Temperature",     "21.9\xC2\xB0""C");
    create_stat_card(scr, x2, y_start + 2*(card_h + gap),   card_w, card_h, "Humidity",             "43.9%");
    create_stat_card(scr, x1, y_start + 3*(card_h + gap),   card_w, card_h, "CO\xE2\x82\x82 Level","665ppm");
    create_stat_card(scr, x2, y_start + 3*(card_h + gap),   card_w, card_h, "Indoor Pressure",      "1014hPa");

    /* ── Mode buttons ── */
    int mode_y = y_start + 4 * (card_h + gap) + 8;
    lv_obj_t *mode_bar = lv_obj_create(scr);
    lv_obj_set_pos(mode_bar, 16, mode_y);
    lv_obj_set_size(mode_bar, 688, 56);
    lv_obj_set_style_bg_opa(mode_bar, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(mode_bar, 0, 0);
    lv_obj_set_style_pad_all(mode_bar, 0, 0);
    lv_obj_set_scrollbar_mode(mode_bar, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(mode_bar, LV_OBJ_FLAG_SCROLLABLE);

    int btn_w = 128;
    int btn_gap = 12;
    create_mode_btn(mode_bar, 0 * (btn_w + btn_gap), btn_w, "Auto",    true);
    create_mode_btn(mode_bar, 1 * (btn_w + btn_gap), btn_w, "Manual",  false);
    create_mode_btn(mode_bar, 2 * (btn_w + btn_gap), btn_w, "Boost",   false);
    create_mode_btn(mode_bar, 3 * (btn_w + btn_gap), btn_w, "Off",     false);
    create_mode_btn(mode_bar, 4 * (btn_w + btn_gap), btn_w, "Bypass",  false);

    /* ── Power Level bar ── */
    int power_y = mode_y + 64;
    lv_obj_t *power_card = lv_obj_create(scr);
    lv_obj_set_pos(power_card, 16, power_y);
    lv_obj_set_size(power_card, 688, 72);
    lv_obj_set_style_bg_color(power_card, C_CARD, 0);
    lv_obj_set_style_bg_opa(power_card, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(power_card, 14, 0);
    lv_obj_set_style_border_width(power_card, 1, 0);
    lv_obj_set_style_border_color(power_card, C_CARD_BRD, 0);
    lv_obj_set_style_pad_all(power_card, 12, 0);
    lv_obj_set_scrollbar_mode(power_card, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(power_card, LV_OBJ_FLAG_SCROLLABLE);

    lv_obj_t *pw_label = lv_label_create(power_card);
    lv_label_set_text(pw_label, "Power Level");
    lv_obj_set_style_text_color(pw_label, C_TEXT_DIM, 0);
    lv_obj_set_style_text_font(pw_label, &lv_font_montserrat_12, 0);
    lv_obj_set_pos(pw_label, 20, 0);

    lv_obj_t *pw_val = lv_label_create(power_card);
    lv_label_set_text(pw_val, "60%");
    lv_obj_set_style_text_color(pw_val, C_TEXT, 0);
    lv_obj_set_style_text_font(pw_val, &lv_font_montserrat_24, 0);
    lv_obj_align(pw_val, LV_ALIGN_TOP_RIGHT, 0, -4);

    lv_obj_t *bar = lv_bar_create(power_card);
    lv_obj_set_pos(bar, 20, 30);
    lv_obj_set_size(bar, 620, 12);
    lv_bar_set_range(bar, 0, 100);
    lv_bar_set_value(bar, 60, LV_ANIM_OFF);
    lv_obj_set_style_bg_color(bar, C_BAR_BG, 0);
    lv_obj_set_style_radius(bar, 6, 0);
    lv_obj_set_style_bg_color(bar, C_BAR_IND, LV_PART_INDICATOR);
    lv_obj_set_style_radius(bar, 6, LV_PART_INDICATOR);

    /* ── Page dots ── */
    create_dots(scr, 0);
}

/* ── Build Hello Screen ── */
static void build_screen_hello(lv_obj_t *scr)
{
    lv_obj_set_style_bg_color(scr, C_BG, 0);
    lv_obj_set_style_bg_opa(scr, LV_OPA_COVER, 0);
    lv_obj_clear_flag(scr, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_event_cb(scr, on_gesture, LV_EVENT_GESTURE, NULL);

    lv_obj_t *label = lv_label_create(scr);
    lv_label_set_text(label, "Hello World");
    lv_obj_set_style_text_color(label, C_TEXT, 0);
    lv_obj_set_style_text_font(label, &lv_font_montserrat_26, 0);
    lv_obj_align(label, LV_ALIGN_CENTER, 0, -10);

    lv_obj_t *sub = lv_label_create(scr);
    lv_label_set_text(sub, "Swipe right to go back");
    lv_obj_set_style_text_color(sub, C_TEXT_DIM, 0);
    lv_obj_align(sub, LV_ALIGN_CENTER, 0, 20);

    create_dots(scr, 1);
}

/* ── Main ── */
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

    screen1 = lv_obj_create(NULL);
    screen2 = lv_obj_create(NULL);
    build_screen_hrv(screen1);
    build_screen_hello(screen2);
    lv_screen_load(screen1);

    while (true) {
        vTaskDelay(pdMS_TO_TICKS(5));
        lv_task_handler();
    }
}
