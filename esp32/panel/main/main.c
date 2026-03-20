/**
 * HRV Panel v2 - clean rewrite without esp_lvgl_port.
 * Arduino-style LVGL: manual init, direct draw_bitmap flush.
 */
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_heap_caps.h"
#include "esp_timer.h"
#include "esp_lcd_panel_ops.h"
#include "esp_lcd_touch_gt911.h"
#include "esp_http_server.h"
#include "esp_ota_ops.h"
#include "esp_system.h"
#include "esp_netif.h"
#include "esp_eth.h"
#include "esp_event.h"
#include "mqtt_client.h"
#include "driver/gpio.h"
#include "lvgl.h"
#include "bsp/esp-bsp.h"
#include "bsp/display.h"
#include "bsp/touch.h"
#include "bsp_board_extra.h"
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <time.h>
#include <sys/time.h>
#include "esp_sntp.h"
#include "namedays.h"
#include "weather.h"
#include "screen_forecast.h"

static const char *TAG = "panel";

/* ── HW handles ── */
static esp_lcd_panel_handle_t lcd_panel = NULL;
static esp_lcd_touch_handle_t touch_handle = NULL;
static esp_mqtt_client_handle_t mqtt_client = NULL;

/* ── Colors ── */
#define C_BG         lv_color_hex(0x0d1117)
#define C_CARD       lv_color_hex(0x161b22)
#define C_CARD_BRD   lv_color_hex(0x30363d)
#define C_MODE_BG    lv_color_hex(0x161b22)
#define C_MODE_ACT   lv_color_hex(0x1a6eff)
#define C_TEXT        lv_color_hex(0xffffff)
#define C_TEXT_DIM    lv_color_hex(0x8b949e)
#define C_BAR_BG     lv_color_hex(0x30363d)
#define C_BAR_IND    lv_color_hex(0x4488ff)

/* ── State ── */
static volatile bool state_dirty = false;
static struct {
    char temp_inside[16];
    char temp_outdoor[16];
    char temp_supply[16];
    char temp_extract[16];
    char temp_exhaust[16];
    char humidity[16];
    char co2[16];
    char pressure[16];
    char power[8];
    int power_val;
    bool manual_mode;
    bool temp_manual_mode;
    bool boost_mode;
    bool bypass_active;
} state = {
    .temp_inside = "--", .temp_outdoor = "--", .temp_supply = "--",
    .temp_extract = "--", .temp_exhaust = "--",
    .humidity = "--", .co2 = "--", .pressure = "--", .power = "--",
};

/* ── LVGL widgets ── */
static lv_obj_t *scr_home, *scr_forecast, *scr_hrv;
static int current_screen = 0;
#define SCREEN_HOME 0
#define SCREEN_FORECAST 1
#define SCREEN_HRV 2
static lv_obj_t *lbl_outdoor, *lbl_supply, *lbl_extract, *lbl_exhaust;
static lv_obj_t *lbl_room, *lbl_humidity, *lbl_co2, *lbl_pressure;
static lv_obj_t *lbl_power;
static lv_obj_t *power_bar;
static lv_obj_t *mode_btns[5];
static lv_obj_t *btn_minus, *btn_plus;

// Home screen widgets
static lv_obj_t *lbl_time, *lbl_seconds, *lbl_date, *lbl_nameday;
// Weather: 12 hourly columns
static lv_obj_t *wx_hour_lbl[WEATHER_MAX_HOURS];
static lv_obj_t *wx_temp_lbl[WEATHER_MAX_HOURS];
static lv_obj_t *wx_icon_lbl[WEATHER_MAX_HOURS];
static lv_obj_t *wx_wind_lbl[WEATHER_MAX_HOURS];
static weather_hour_t wx_hours[WEATHER_MAX_HOURS];
static int wx_count = 0;

/* ── LVGL core callbacks ── */
static void disp_flush(lv_display_t *d, const lv_area_t *a, uint8_t *px)
{
    esp_lcd_panel_draw_bitmap(lcd_panel, a->x1, a->y1, a->x2+1, a->y2+1, px);
    lv_display_flush_ready(d);
}
static void tick_cb(void *arg) { lv_tick_inc(5); }
static void touch_read(lv_indev_t *inv, lv_indev_data_t *d)
{
    uint16_t x[1], y[1], s[1]; uint8_t cnt = 0;
    esp_lcd_touch_read_data(touch_handle);
    if (esp_lcd_touch_get_coordinates(touch_handle, x, y, s, &cnt, 1) && cnt > 0) {
        d->point.x = x[0]; d->point.y = y[0]; d->state = LV_INDEV_STATE_PRESSED;
    } else { d->state = LV_INDEV_STATE_RELEASED; }
}

/* ── Swipe gesture ── */
static void on_gesture(lv_event_t *e)
{
    lv_dir_t dir = lv_indev_get_gesture_dir(lv_indev_active());
    if (dir == LV_DIR_LEFT) {
        if (current_screen == SCREEN_HOME) {
            current_screen = SCREEN_FORECAST;
            lv_screen_load_anim(scr_forecast, LV_SCR_LOAD_ANIM_MOVE_LEFT, 300, 0, false);
        } else if (current_screen == SCREEN_FORECAST) {
            current_screen = SCREEN_HRV;
            lv_screen_load_anim(scr_hrv, LV_SCR_LOAD_ANIM_MOVE_LEFT, 300, 0, false);
        }
    } else if (dir == LV_DIR_RIGHT) {
        if (current_screen == SCREEN_HRV) {
            current_screen = SCREEN_FORECAST;
            lv_screen_load_anim(scr_forecast, LV_SCR_LOAD_ANIM_MOVE_RIGHT, 300, 0, false);
        } else if (current_screen == SCREEN_FORECAST) {
            current_screen = SCREEN_HOME;
            lv_screen_load_anim(scr_home, LV_SCR_LOAD_ANIM_MOVE_RIGHT, 300, 0, false);
        }
    }
}

/* ── Page dots ── */
static void create_footer(lv_obj_t *parent, int active)
{
    for (int i = 0; i < 3; i++) {
        lv_obj_t *d = lv_obj_create(parent);
        lv_obj_set_size(d, 8, 8);
        lv_obj_set_style_radius(d, 4, 0);
        lv_obj_set_style_border_width(d, 0, 0);
        lv_obj_set_style_pad_all(d, 0, 0);
        lv_obj_set_style_bg_color(d, (i == active) ? C_TEXT : lv_color_hex(0x444444), 0);
        lv_obj_set_style_bg_opa(d, LV_OPA_COVER, 0);
        lv_obj_clear_flag(d, LV_OBJ_FLAG_SCROLLABLE);
        lv_obj_set_pos(d, 333 + i * 18, 704);
    }
}

/* ── Stat card ── */
static lv_obj_t *create_card(lv_obj_t *p, int x, int y, int w, int h,
                              const char *icon, lv_color_t ic, const char *label, const char *val)
{
    lv_obj_t *c = lv_obj_create(p);
    lv_obj_set_pos(c, x, y); lv_obj_set_size(c, w, h);
    lv_obj_set_style_bg_color(c, C_CARD, 0);
    lv_obj_set_style_bg_opa(c, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(c, 14, 0);
    lv_obj_set_style_border_width(c, 1, 0);
    lv_obj_set_style_border_color(c, C_CARD_BRD, 0);
    lv_obj_set_style_pad_left(c, 16, 0);
    lv_obj_set_style_pad_top(c, 12, 0);
    lv_obj_set_scrollbar_mode(c, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(c, LV_OBJ_FLAG_SCROLLABLE);

    lv_obj_t *i1 = lv_label_create(c);
    lv_label_set_text(i1, icon);
    lv_obj_set_style_text_color(i1, ic, 0);
    lv_obj_set_style_text_font(i1, &lv_font_montserrat_12, 0);
    lv_obj_set_pos(i1, 0, 1);

    lv_obj_t *l = lv_label_create(c);
    lv_label_set_text(l, label);
    lv_obj_set_style_text_color(l, C_TEXT_DIM, 0);
    lv_obj_set_style_text_font(l, &lv_font_montserrat_12, 0);
    lv_obj_set_pos(l, 18, 0);

    lv_obj_t *v = lv_label_create(c);
    lv_label_set_text(v, val);
    lv_obj_set_style_text_color(v, C_TEXT, 0);
    lv_obj_set_style_text_font(v, &lv_font_montserrat_22, 0);
    lv_obj_set_pos(v, 0, 30);
    return v;
}

/* ── Mode button ── */
static lv_obj_t *create_mode_btn(lv_obj_t *p, int x, int w, const char *icon, const char *text, bool active)
{
    lv_obj_t *b = lv_obj_create(p);
    lv_obj_set_pos(b, x, 0); lv_obj_set_size(b, w, 56);
    lv_obj_set_style_bg_color(b, active ? C_MODE_ACT : C_MODE_BG, 0);
    lv_obj_set_style_bg_opa(b, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(b, 14, 0);
    lv_obj_set_style_border_width(b, active ? 0 : 1, 0);
    lv_obj_set_style_border_color(b, C_CARD_BRD, 0);
    lv_obj_set_scrollbar_mode(b, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(b, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_set_style_pad_all(b, 0, 0);
    lv_obj_add_flag(b, LV_OBJ_FLAG_CLICKABLE);

    lv_obj_t *ic = lv_label_create(b);
    lv_label_set_text(ic, icon);
    lv_obj_set_style_text_color(ic, C_TEXT, 0);
    lv_obj_set_style_text_font(ic, &lv_font_montserrat_16, 0);
    lv_obj_set_pos(ic, (w - 16) / 2, 6);

    lv_obj_t *l = lv_label_create(b);
    lv_label_set_text(l, text);
    lv_obj_set_style_text_color(l, C_TEXT, 0);
    lv_obj_set_style_text_font(l, &lv_font_montserrat_12, 0);
    lv_obj_set_pos(l, 0, 36);
    lv_obj_set_width(l, w);
    lv_obj_set_style_text_align(l, LV_TEXT_ALIGN_CENTER, 0);
    return b;
}

/* ── Mode logic ── */
static bool is_manual_mode(void) { return state.manual_mode || state.temp_manual_mode; }

static void update_mode_visuals(void)
{
    bool man = is_manual_mode() && state.power_val > 0;
    bool off = is_manual_mode() && state.power_val == 0;
    bool aut = !state.manual_mode && !state.temp_manual_mode && !state.boost_mode;
    bool act[] = { aut, man, state.boost_mode, off, state.bypass_active };
    for (int i = 0; i < 5; i++) {
        lv_obj_set_style_bg_color(mode_btns[i], act[i] ? C_MODE_ACT : C_MODE_BG, 0);
        lv_obj_set_style_border_width(mode_btns[i], act[i] ? 0 : 1, 0);
    }
    bool show = is_manual_mode();
    if (show) { lv_obj_clear_flag(btn_minus, LV_OBJ_FLAG_HIDDEN); lv_obj_clear_flag(btn_plus, LV_OBJ_FLAG_HIDDEN); }
    else { lv_obj_add_flag(btn_minus, LV_OBJ_FLAG_HIDDEN); lv_obj_add_flag(btn_plus, LV_OBJ_FLAG_HIDDEN); }
}

/* ── MQTT command queue ── */
enum { CMD_NONE=0, CMD_AUTO, CMD_MANUAL, CMD_BOOST, CMD_OFF, CMD_BYPASS, CMD_POWER };
static volatile int pending_cmd = CMD_NONE;
static volatile uint32_t mode_click_time = 0;  // tick when mode button was clicked

/* ── Click handlers ── */
#define MODE_COOLDOWN_MS 2000
static void mode_click(void) { mode_click_time = xTaskGetTickCount(); }
static void on_auto(lv_event_t *e)   { state.manual_mode=0; state.temp_manual_mode=0; state.boost_mode=0; update_mode_visuals(); pending_cmd=CMD_AUTO; mode_click(); }
static void on_manual(lv_event_t *e) { state.manual_mode=0; state.temp_manual_mode=1; state.boost_mode=0; if(state.power_val==0){state.power_val=50; snprintf(state.power,8,"50%%"); lv_label_set_text(lbl_power,state.power); lv_bar_set_value(power_bar,50,LV_ANIM_OFF);} update_mode_visuals(); pending_cmd=CMD_MANUAL; mode_click(); }
static void on_boost(lv_event_t *e)  { state.manual_mode=0; state.temp_manual_mode=0; state.boost_mode=1; update_mode_visuals(); pending_cmd=CMD_BOOST; mode_click(); }
static void on_off(lv_event_t *e)    { state.manual_mode=0; state.temp_manual_mode=1; state.boost_mode=0; state.power_val=0; snprintf(state.power,8,"0%%"); lv_label_set_text(lbl_power,state.power); lv_bar_set_value(power_bar,0,LV_ANIM_OFF); update_mode_visuals(); pending_cmd=CMD_OFF; mode_click(); }
static void on_bypass(lv_event_t *e) { state.bypass_active=!state.bypass_active; update_mode_visuals(); pending_cmd=CMD_BYPASS; mode_click(); }
static void on_minus(lv_event_t *e)  { if(state.power_val>=10) state.power_val-=10; else state.power_val=0; snprintf(state.power,8,"%d%%",state.power_val); lv_label_set_text(lbl_power,state.power); lv_bar_set_value(power_bar,state.power_val,LV_ANIM_OFF); pending_cmd=CMD_POWER; }
static void on_plus(lv_event_t *e)   { if(state.power_val<=90) state.power_val+=10; else state.power_val=100; snprintf(state.power,8,"%d%%",state.power_val); lv_label_set_text(lbl_power,state.power); lv_bar_set_value(power_bar,state.power_val,LV_ANIM_OFF); pending_cmd=CMD_POWER; }

/* ── Build HRV Screen ── */
static void build_hrv(lv_obj_t *scr)
{
    lv_obj_set_style_bg_color(scr, C_BG, 0);
    lv_obj_set_style_bg_opa(scr, LV_OPA_COVER, 0);
    lv_obj_clear_flag(scr, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_event_cb(scr, on_gesture, LV_EVENT_GESTURE, NULL);

    lv_obj_t *t = lv_label_create(scr);
    lv_label_set_text(t, "HRV Controller");
    lv_obj_set_style_text_color(t, C_TEXT, 0);
    lv_obj_set_style_text_font(t, &lv_font_montserrat_22, 0);
    lv_obj_set_pos(t, 60, 30);
    lv_obj_t *st = lv_label_create(scr);
    lv_label_set_text(st, "Heat Recovery Ventilation");
    lv_obj_set_style_text_color(st, C_TEXT_DIM, 0);
    lv_obj_set_style_text_font(st, &lv_font_montserrat_12, 0);
    lv_obj_set_pos(st, 60, 58);

    #define IY lv_color_hex(0xd29922)
    #define IG lv_color_hex(0x3fb950)
    #define IO lv_color_hex(0xe3872d)
    #define IR lv_color_hex(0xf85149)
    #define IB lv_color_hex(0x58a6ff)
    int cw=339, ch=100, g=10, x1=16, x2=16+cw+g, ys=80;
    lbl_outdoor = create_card(scr, x1, ys,         cw,ch, LV_SYMBOL_IMAGE,   IY, "Outdoor Temperature","--");
    lbl_supply  = create_card(scr, x2, ys,         cw,ch, LV_SYMBOL_DOWNLOAD,IG, "Supply Temperature", "--");
    lbl_extract = create_card(scr, x1, ys+1*(ch+g),cw,ch, LV_SYMBOL_UPLOAD,  IO, "Extract Temperature","--");
    lbl_exhaust = create_card(scr, x2, ys+1*(ch+g),cw,ch, LV_SYMBOL_REFRESH, IR, "Exhaust Temperature","--");
    lbl_room    = create_card(scr, x1, ys+2*(ch+g),cw,ch, LV_SYMBOL_HOME,    IB, "Room Temperature",  "--");
    lbl_humidity= create_card(scr, x2, ys+2*(ch+g),cw,ch, LV_SYMBOL_TINT,    IB, "Humidity",          "--");
    lbl_co2     = create_card(scr, x1, ys+3*(ch+g),cw,ch, LV_SYMBOL_LOOP,    IY, "CO2 Level",         "--");
    lbl_pressure= create_card(scr, x2, ys+3*(ch+g),cw,ch, LV_SYMBOL_GPS,     IB, "Indoor Pressure",   "--");

    /* Mode buttons */
    int my = ys + 4*(ch+g) + 8;
    lv_obj_t *mb = lv_obj_create(scr);
    lv_obj_set_pos(mb, 16, my); lv_obj_set_size(mb, 688, 56);
    lv_obj_set_style_bg_opa(mb, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(mb, 0, 0);
    lv_obj_set_style_pad_all(mb, 0, 0);
    lv_obj_set_scrollbar_mode(mb, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(mb, LV_OBJ_FLAG_SCROLLABLE);
    int bw=128, bg=12;
    mode_btns[0] = create_mode_btn(mb, 0*(bw+bg), bw, LV_SYMBOL_SETTINGS, "Auto",   true);
    mode_btns[1] = create_mode_btn(mb, 1*(bw+bg), bw, LV_SYMBOL_EDIT,     "Manual", false);
    mode_btns[2] = create_mode_btn(mb, 2*(bw+bg), bw, LV_SYMBOL_CHARGE,   "Boost",  false);
    mode_btns[3] = create_mode_btn(mb, 3*(bw+bg), bw, LV_SYMBOL_POWER,    "Off",    false);
    mode_btns[4] = create_mode_btn(mb, 4*(bw+bg), bw, LV_SYMBOL_SHUFFLE,  "Bypass", false);
    lv_obj_add_event_cb(mode_btns[0], on_auto,  LV_EVENT_CLICKED, NULL);
    lv_obj_add_event_cb(mode_btns[1], on_manual,LV_EVENT_CLICKED, NULL);
    lv_obj_add_event_cb(mode_btns[2], on_boost, LV_EVENT_CLICKED, NULL);
    lv_obj_add_event_cb(mode_btns[3], on_off,   LV_EVENT_CLICKED, NULL);
    lv_obj_add_event_cb(mode_btns[4], on_bypass,LV_EVENT_CLICKED, NULL);

    /* Power bar with +/- */
    int py = my + 64;
    lv_obj_t *pc = lv_obj_create(scr);
    lv_obj_set_pos(pc, 16, py); lv_obj_set_size(pc, 688, 72);
    lv_obj_set_style_bg_color(pc, C_CARD, 0);
    lv_obj_set_style_bg_opa(pc, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(pc, 14, 0);
    lv_obj_set_style_border_width(pc, 1, 0);
    lv_obj_set_style_border_color(pc, C_CARD_BRD, 0);
    lv_obj_set_style_pad_all(pc, 0, 0);
    lv_obj_set_scrollbar_mode(pc, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(pc, LV_OBJ_FLAG_SCROLLABLE);

    btn_minus = lv_obj_create(pc);
    lv_obj_set_pos(btn_minus, 0, 0); lv_obj_set_size(btn_minus, 64, 72);
    lv_obj_set_style_bg_color(btn_minus, lv_color_hex(0xc0392b), 0);
    lv_obj_set_style_bg_opa(btn_minus, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(btn_minus, 14, 0);
    lv_obj_set_style_border_width(btn_minus, 0, 0);
    lv_obj_set_scrollbar_mode(btn_minus, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(btn_minus, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(btn_minus, LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_HIDDEN);
    lv_obj_add_event_cb(btn_minus, on_minus, LV_EVENT_CLICKED, NULL);
    lv_obj_t *ml = lv_label_create(btn_minus);
    lv_label_set_text(ml, LV_SYMBOL_MINUS);
    lv_obj_set_style_text_color(ml, C_TEXT, 0);
    lv_obj_set_style_text_font(ml, &lv_font_montserrat_22, 0);
    lv_obj_center(ml);

    btn_plus = lv_obj_create(pc);
    lv_obj_set_pos(btn_plus, 624, 0); lv_obj_set_size(btn_plus, 64, 72);
    lv_obj_set_style_bg_color(btn_plus, lv_color_hex(0x27ae60), 0);
    lv_obj_set_style_bg_opa(btn_plus, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(btn_plus, 14, 0);
    lv_obj_set_style_border_width(btn_plus, 0, 0);
    lv_obj_set_scrollbar_mode(btn_plus, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(btn_plus, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_flag(btn_plus, LV_OBJ_FLAG_CLICKABLE | LV_OBJ_FLAG_HIDDEN);
    lv_obj_add_event_cb(btn_plus, on_plus, LV_EVENT_CLICKED, NULL);
    lv_obj_t *pl = lv_label_create(btn_plus);
    lv_label_set_text(pl, LV_SYMBOL_PLUS);
    lv_obj_set_style_text_color(pl, C_TEXT, 0);
    lv_obj_set_style_text_font(pl, &lv_font_montserrat_22, 0);
    lv_obj_center(pl);

    lv_obj_t *pwl = lv_label_create(pc);
    lv_label_set_text(pwl, "Power Level");
    lv_obj_set_style_text_color(pwl, C_TEXT_DIM, 0);
    lv_obj_set_style_text_font(pwl, &lv_font_montserrat_12, 0);
    lv_obj_set_pos(pwl, 80, 8);

    lbl_power = lv_label_create(pc);
    lv_label_set_text(lbl_power, "--%");
    lv_obj_set_style_text_color(lbl_power, C_TEXT, 0);
    lv_obj_set_style_text_font(lbl_power, &lv_font_montserrat_24, 0);
    lv_obj_set_pos(lbl_power, 560, 4);

    power_bar = lv_bar_create(pc);
    lv_obj_set_pos(power_bar, 80, 40); lv_obj_set_size(power_bar, 520, 12);
    lv_bar_set_range(power_bar, 0, 100);
    lv_obj_set_style_bg_color(power_bar, C_BAR_BG, 0);
    lv_obj_set_style_radius(power_bar, 6, 0);
    lv_obj_set_style_bg_color(power_bar, C_BAR_IND, LV_PART_INDICATOR);
    lv_obj_set_style_radius(power_bar, 6, LV_PART_INDICATOR);

    create_footer(scr, 2);
}

/* ── Build Home Screen (clock, date, nameday, weather) ── */
static lv_obj_t *make_card_box(lv_obj_t *parent, int x, int y, int w, int h)
{
    lv_obj_t *c = lv_obj_create(parent);
    lv_obj_set_pos(c, x, y); lv_obj_set_size(c, w, h);
    lv_obj_set_style_bg_color(c, C_CARD, 0);
    lv_obj_set_style_bg_opa(c, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(c, 16, 0);
    lv_obj_set_style_border_width(c, 1, 0);
    lv_obj_set_style_border_color(c, C_CARD_BRD, 0);
    lv_obj_set_scrollbar_mode(c, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(c, LV_OBJ_FLAG_SCROLLABLE | LV_OBJ_FLAG_CLICKABLE);
    lv_obj_add_flag(c, LV_OBJ_FLAG_GESTURE_BUBBLE | LV_OBJ_FLAG_EVENT_BUBBLE);
    return c;
}

static void build_home(lv_obj_t *scr)
{
    lv_obj_set_style_bg_color(scr, C_BG, 0);
    lv_obj_set_style_bg_opa(scr, LV_OPA_COVER, 0);
    lv_obj_clear_flag(scr, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_event_cb(scr, on_gesture, LV_EVENT_GESTURE, NULL);

    // Clock card
    lv_obj_t *clk_box = make_card_box(scr, 16, 20, 688, 200);
    lbl_time = lv_label_create(clk_box);
    lv_label_set_text(lbl_time, "00:00");
    lv_obj_set_style_text_color(lbl_time, C_TEXT, 0);
    lv_obj_set_style_text_font(lbl_time, &lv_font_montserrat_26, 0);
    lv_obj_align(lbl_time, LV_ALIGN_CENTER, -20, 0);

    lbl_seconds = lv_label_create(clk_box);
    lv_label_set_text(lbl_seconds, ":00");
    lv_obj_set_style_text_color(lbl_seconds, C_TEXT_DIM, 0);
    lv_obj_set_style_text_font(lbl_seconds, &lv_font_montserrat_20, 0);
    lv_obj_align(lbl_seconds, LV_ALIGN_CENTER, 40, 6);

    // Date + nameday card
    lv_obj_t *date_box = make_card_box(scr, 16, 232, 688, 110);
    lbl_date = lv_label_create(date_box);
    lv_label_set_text(lbl_date, "");
    lv_obj_set_style_text_color(lbl_date, C_TEXT, 0);
    lv_obj_set_style_text_font(lbl_date, &lv_font_montserrat_20, 0);
    lv_obj_align(lbl_date, LV_ALIGN_CENTER, 0, -14);

    lbl_nameday = lv_label_create(date_box);
    lv_label_set_text(lbl_nameday, "");
    lv_obj_set_style_text_color(lbl_nameday, lv_color_hex(0x58a6ff), 0);
    lv_obj_set_style_text_font(lbl_nameday, &lv_font_montserrat_16, 0);
    lv_obj_align(lbl_nameday, LV_ALIGN_CENTER, 0, 18);

    // Weather forecast card - 12 hourly columns
    lv_obj_t *wx_box = make_card_box(scr, 16, 354, 688, 320);

    lv_obj_t *wx_title = lv_label_create(wx_box);
    lv_label_set_text(wx_title, "Pocasi");
    lv_obj_set_style_text_color(wx_title, C_TEXT, 0);
    lv_obj_set_style_text_font(wx_title, &lv_font_montserrat_16, 0);
    lv_obj_set_pos(wx_title, 20, 8);

    int col_w = 56;  // 688 / 12 ≈ 57
    for (int i = 0; i < WEATHER_MAX_HOURS; i++) {
        int cx = 2 + i * col_w;

        wx_hour_lbl[i] = lv_label_create(wx_box);
        lv_label_set_text(wx_hour_lbl[i], "--");
        lv_obj_set_style_text_color(wx_hour_lbl[i], C_TEXT_DIM, 0);
        lv_obj_set_style_text_font(wx_hour_lbl[i], &lv_font_montserrat_12, 0);
        lv_obj_set_pos(wx_hour_lbl[i], cx + 10, 36);

        wx_icon_lbl[i] = lv_label_create(wx_box);
        lv_label_set_text(wx_icon_lbl[i], "");
        lv_obj_set_style_text_color(wx_icon_lbl[i], C_TEXT_DIM, 0);
        lv_obj_set_style_text_font(wx_icon_lbl[i], &lv_font_montserrat_16, 0);
        lv_obj_set_pos(wx_icon_lbl[i], cx + 10, 56);

        wx_temp_lbl[i] = lv_label_create(wx_box);
        lv_label_set_text(wx_temp_lbl[i], "");
        lv_obj_set_style_text_color(wx_temp_lbl[i], C_TEXT, 0);
        lv_obj_set_style_text_font(wx_temp_lbl[i], &lv_font_montserrat_12, 0);
        lv_obj_set_pos(wx_temp_lbl[i], cx + 4, 80);

        wx_wind_lbl[i] = lv_label_create(wx_box);
        lv_label_set_text(wx_wind_lbl[i], "");
        lv_obj_set_style_text_color(wx_wind_lbl[i], C_TEXT_DIM, 0);
        lv_obj_set_style_text_font(wx_wind_lbl[i], &lv_font_montserrat_8, 0);
        lv_obj_set_pos(wx_wind_lbl[i], cx + 4, 100);
    }

    create_footer(scr, 0);
}

/* ── UI update from state ── */
static void update_ui(void)
{
    if (!state_dirty) return;
    state_dirty = false;
    lv_label_set_text(lbl_outdoor, state.temp_outdoor);
    lv_label_set_text(lbl_supply, state.temp_supply);
    lv_label_set_text(lbl_extract, state.temp_extract);
    lv_label_set_text(lbl_exhaust, state.temp_exhaust);
    lv_label_set_text(lbl_room, state.temp_inside);
    lv_label_set_text(lbl_humidity, state.humidity);
    lv_label_set_text(lbl_co2, state.co2);
    lv_label_set_text(lbl_pressure, state.pressure);
    lv_label_set_text(lbl_power, state.power);
    lv_bar_set_value(power_bar, state.power_val, LV_ANIM_OFF);
    update_mode_visuals();
}

/* ── MQTT ── */
static void mqtt_data_handler(const char *topic, int tl, const char *data, int dl)
{
    char val[32]; int len = dl < 31 ? dl : 31;
    memcpy(val, data, len); val[len] = '\0';
    if (tl <= 14) return;
    const char *k = topic + 14; int kl = tl - 14;
    #define M(s) (kl == (int)strlen(s) && memcmp(k, s, kl) == 0)
    if      (M("temperature/inside") || M("temperature")) snprintf(state.temp_inside, 16, "%.1f\xC2\xB0""C", atof(val));
    else if (M("temperature/outdoor"))  snprintf(state.temp_outdoor, 16, "%.1f\xC2\xB0""C", atof(val));
    else if (M("temperature/supply"))   snprintf(state.temp_supply, 16, "%.1f\xC2\xB0""C", atof(val));
    else if (M("temperature/extract"))  snprintf(state.temp_extract, 16, "%.1f\xC2\xB0""C", atof(val));
    else if (M("temperature/exhaust"))  snprintf(state.temp_exhaust, 16, "%.1f\xC2\xB0""C", atof(val));
    else if (M("airHumidity"))          snprintf(state.humidity, 16, "%.1f%%", atof(val));
    else if (M("co2"))                  snprintf(state.co2, 16, "%d ppm", atoi(val));
    else if (M("pressure"))             snprintf(state.pressure, 16, "%d hPa", atoi(val));
    else if (M("hrvOutputPower"))     { state.power_val = atoi(val); snprintf(state.power, 8, "%d%%", state.power_val); }
    else if (M("manualMode") || M("temporaryManualMode") || M("temporaryBoostMode") || M("bypass")) {
        // Ignore mode updates during cooldown after user click
        uint32_t elapsed = (xTaskGetTickCount() - mode_click_time) * portTICK_PERIOD_MS;
        if (elapsed < MODE_COOLDOWN_MS) return;
        if (M("manualMode"))           state.manual_mode = (strcmp(val,"ON")==0);
        else if (M("temporaryManualMode"))  state.temp_manual_mode = (strcmp(val,"ON")==0);
        else if (M("temporaryBoostMode"))   state.boost_mode = (strcmp(val,"ON")==0);
        else if (M("bypass"))               state.bypass_active = (strcmp(val,"ON")==0);
    }
    else return;
    state_dirty = true;
    #undef M
}

static void mqtt_event_handler(void *a, esp_event_base_t b, int32_t id, void *d)
{
    esp_mqtt_event_handle_t ev = d;
    switch (ev->event_id) {
    case MQTT_EVENT_CONNECTED:
        ESP_LOGI(TAG, "MQTT connected");
        esp_mqtt_client_subscribe(ev->client, "homehab/state/temperature/#", 0);
        esp_mqtt_client_subscribe(ev->client, "homehab/state/temperature/inside", 0);
        esp_mqtt_client_subscribe(ev->client, "homehab/state/airHumidity", 0);
        esp_mqtt_client_subscribe(ev->client, "homehab/state/co2", 0);
        esp_mqtt_client_subscribe(ev->client, "homehab/state/pressure", 0);
        esp_mqtt_client_subscribe(ev->client, "homehab/state/hrvOutputPower", 0);
        esp_mqtt_client_subscribe(ev->client, "homehab/state/manualMode", 0);
        esp_mqtt_client_subscribe(ev->client, "homehab/state/temporaryManualMode", 0);
        esp_mqtt_client_subscribe(ev->client, "homehab/state/temporaryBoostMode", 0);
        esp_mqtt_client_subscribe(ev->client, "homehab/state/bypass", 0);
        esp_mqtt_client_publish(ev->client, "homehab/panel/status", "online", 0, 1, 1);
        break;
    case MQTT_EVENT_DATA:
        if (ev->topic && ev->topic_len > 0 && ev->data && ev->data_len > 0)
            mqtt_data_handler(ev->topic, ev->topic_len, ev->data, ev->data_len);
        break;
    default: break;
    }
}

static void send_pending(void)
{
    if (!mqtt_client || pending_cmd == CMD_NONE) return;
    int cmd = pending_cmd; pending_cmd = CMD_NONE;
    switch (cmd) {
    case CMD_AUTO:
        esp_mqtt_client_publish(mqtt_client, "homehab/panel/command/temporaryManualMode", "OFF", 0, 0, 0);
        esp_mqtt_client_publish(mqtt_client, "homehab/panel/command/temporaryBoostMode", "OFF", 0, 0, 0);
        break;
    case CMD_MANUAL: case CMD_POWER: {
        esp_mqtt_client_publish(mqtt_client, "homehab/panel/command/temporaryManualMode", "ON", 0, 0, 0);
        esp_mqtt_client_publish(mqtt_client, "homehab/panel/command/temporaryBoostMode", "OFF", 0, 0, 0);
        char pw[8]; snprintf(pw, 8, "%d", state.power_val);
        esp_mqtt_client_publish(mqtt_client, "homehab/panel/command/manualPower", pw, 0, 0, 0);
        break; }
    case CMD_BOOST:
        esp_mqtt_client_publish(mqtt_client, "homehab/panel/command/temporaryBoostMode", "ON", 0, 0, 0);
        esp_mqtt_client_publish(mqtt_client, "homehab/panel/command/temporaryManualMode", "OFF", 0, 0, 0);
        break;
    case CMD_OFF:
        esp_mqtt_client_publish(mqtt_client, "homehab/panel/command/temporaryManualMode", "ON", 0, 0, 0);
        esp_mqtt_client_publish(mqtt_client, "homehab/panel/command/temporaryBoostMode", "OFF", 0, 0, 0);
        esp_mqtt_client_publish(mqtt_client, "homehab/panel/command/manualPower", "0", 0, 0, 0);
        break;
    case CMD_BYPASS:
        esp_mqtt_client_publish(mqtt_client, "homehab/panel/command/bypass", state.bypass_active ? "ON" : "OFF", 0, 0, 0);
        break;
    }
}

static void mqtt_task(void *arg)
{
    vTaskDelay(pdMS_TO_TICKS(3000));
    esp_mqtt_client_config_t cfg = {};
    cfg.broker.address.uri = "mqtt://192.168.1.132:1883";
    cfg.credentials.client_id = "homehab-panel";
    cfg.session.last_will.topic = "homehab/panel/status";
    cfg.session.last_will.msg = "offline";
    cfg.session.last_will.msg_len = 7;
    cfg.session.last_will.qos = 1;
    cfg.session.last_will.retain = 1;
    cfg.network.reconnect_timeout_ms = 10000;
    cfg.session.keepalive = 300;
    cfg.task.stack_size = 4096;
    cfg.task.priority = 1;
    cfg.buffer.size = 2048;
    mqtt_client = esp_mqtt_client_init(&cfg);
    if (mqtt_client) {
        esp_mqtt_client_register_event(mqtt_client, ESP_EVENT_ANY_ID, mqtt_event_handler, NULL);
        esp_mqtt_client_start(mqtt_client);
    }
    while (1) { send_pending(); vTaskDelay(pdMS_TO_TICKS(100)); }
}

/* ── OTA ── */
static esp_err_t ota_handler(httpd_req_t *req)
{
    const esp_partition_t *part = esp_ota_get_next_update_partition(NULL);
    if (!part) { httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "No OTA partition"); return ESP_FAIL; }
    esp_ota_handle_t h; esp_err_t err = esp_ota_begin(part, OTA_WITH_SEQUENTIAL_WRITES, &h);
    if (err != ESP_OK) { httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "begin fail"); return ESP_FAIL; }
    char *buf = malloc(4096); int rx = 0;
    while (rx < req->content_len) {
        int r = httpd_req_recv(req, buf, 4096);
        if (r <= 0) { if (r == HTTPD_SOCK_ERR_TIMEOUT) continue; free(buf); esp_ota_abort(h); return ESP_FAIL; }
        esp_ota_write(h, buf, r); rx += r;
    }
    free(buf);
    if (esp_ota_end(h) != ESP_OK) { httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "validate fail"); return ESP_FAIL; }
    err = esp_ota_set_boot_partition(part);
    const esp_partition_t *boot = esp_ota_get_boot_partition();
    char resp[128]; snprintf(resp, 128, "written=%s err=%s boot=%s\n", part->label, esp_err_to_name(err), boot?boot->label:"?");
    httpd_resp_sendstr(req, resp);
    vTaskDelay(pdMS_TO_TICKS(200)); esp_restart(); return ESP_OK;
}

static void on_got_ip(void *a, esp_event_base_t b, int32_t id, void *d)
{
    ip_event_got_ip_t *ev = (ip_event_got_ip_t *)d;
    ESP_LOGI("eth", "IP: " IPSTR, IP2STR(&ev->ip_info.ip));
    httpd_config_t hc = HTTPD_DEFAULT_CONFIG(); hc.recv_wait_timeout = 30;
    httpd_handle_t srv = NULL;
    if (httpd_start(&srv, &hc) == ESP_OK) {
        httpd_uri_t u = { .uri = "/ota", .method = HTTP_POST, .handler = ota_handler };
        httpd_register_uri_handler(srv, &u);
    }
    setenv("TZ", "CET-1CEST,M3.5.0,M10.5.0/3", 1); tzset();
    esp_sntp_setoperatingmode(ESP_SNTP_OPMODE_POLL);
    esp_sntp_setservername(0, "pool.ntp.org");
    esp_sntp_init();
}

static void eth_init(void)
{
    esp_netif_init(); esp_event_loop_create_default();
    esp_netif_t *n = esp_netif_new(&(esp_netif_config_t)ESP_NETIF_DEFAULT_ETH());
    eth_mac_config_t mc = ETH_MAC_DEFAULT_CONFIG();
    eth_esp32_emac_config_t ec = ETH_ESP32_EMAC_DEFAULT_CONFIG();
    ec.smi_gpio.mdc_num = 31; ec.smi_gpio.mdio_num = 52;
    esp_eth_mac_t *mac = esp_eth_mac_new_esp32(&ec, &mc);
    eth_phy_config_t pc = ETH_PHY_DEFAULT_CONFIG(); pc.phy_addr = 1; pc.reset_gpio_num = 51;
    esp_eth_phy_t *phy = esp_eth_phy_new_ip101(&pc);
    esp_eth_handle_t eh = NULL;
    esp_eth_driver_install(&(esp_eth_config_t)ETH_DEFAULT_CONFIG(mac, phy), &eh);
    esp_netif_attach(n, esp_eth_new_netif_glue(eh));
    esp_event_handler_register(IP_EVENT, IP_EVENT_ETH_GOT_IP, &on_got_ip, NULL);
    esp_eth_start(eh);
}

/* ── Weather task ── */
static volatile bool weather_dirty = false;

static void weather_task(void *arg)
{
    vTaskDelay(pdMS_TO_TICKS(10000));
    while (1) {
        weather_hour_t tmp[WEATHER_MAX_HOURS];
        int cnt = 0;
        if (weather_fetch(tmp, WEATHER_MAX_HOURS, &cnt) == 0 && cnt > 0) {
            memcpy(wx_hours, tmp, sizeof(tmp));
            wx_count = cnt;
            weather_dirty = true;
        }
        vTaskDelay(pdMS_TO_TICKS(600000));
    }
}

/* Forecast refresh task is managed by screen_forecast module */

/* ── Main ── */
void app_main(void)
{
    ESP_LOGI(TAG, "=== HRV Panel v2 ===");

    // HW
    bsp_lcd_handles_t h;
    bsp_display_new_with_handles(NULL, &h);
    lcd_panel = h.panel;
    bsp_display_backlight_on();
    bsp_i2c_init();
    bsp_touch_new(NULL, &touch_handle);
    eth_init();

    // LVGL
    lv_init();
    esp_timer_handle_t tt;
    esp_timer_create(&(esp_timer_create_args_t){ .callback = tick_cb, .name = "t" }, &tt);
    esp_timer_start_periodic(tt, 5000);

    lv_display_t *disp = lv_display_create(720, 720);
    size_t bsz = 720 * 50 * sizeof(lv_color16_t);
    void *b1 = heap_caps_malloc(bsz, MALLOC_CAP_DMA | MALLOC_CAP_INTERNAL);
    void *b2 = heap_caps_malloc(bsz, MALLOC_CAP_DMA | MALLOC_CAP_INTERNAL);
    lv_display_set_buffers(disp, b1, b2, bsz, LV_DISPLAY_RENDER_MODE_PARTIAL);
    lv_display_set_flush_cb(disp, disp_flush);
    lv_display_set_color_format(disp, LV_COLOR_FORMAT_RGB565);

    lv_indev_t *in = lv_indev_create();
    lv_indev_set_type(in, LV_INDEV_TYPE_POINTER);
    lv_indev_set_read_cb(in, touch_read);

    // UI
    scr_home = lv_obj_create(NULL);
    scr_forecast = screen_forecast_create();
    lv_obj_add_event_cb(scr_forecast, on_gesture, LV_EVENT_GESTURE, NULL);
    create_footer(scr_forecast, 1);
    scr_hrv = lv_obj_create(NULL);
    build_home(scr_home);
    build_hrv(scr_hrv);
    lv_screen_load(scr_home);

    // MQTT
    xTaskCreate(mqtt_task, "mqtt", 8192, NULL, 1, NULL);

    // Weather fetch task
    xTaskCreate(weather_task, "weather", 8192, NULL, 1, NULL);

    // Forecast chart fetch is started inside screen_forecast_create()

    // Loop
    static const char *cz_days[] = {"Nedele","Pondeli","Utery","Streda","Ctvrtek","Patek","Sobota"};
    static const char *cz_months[] = {"Ledna","Unora","Brezna","Dubna","Kvetna","Cervna","Cervence","Srpna","Zari","Rijna","Listopadu","Prosince"};
    int last_sec = -1;
    bool date_needs_update = true;
    while (true) {
        update_ui();
        screen_forecast_update();
        if (weather_dirty) {
            weather_dirty = false;
            char buf[16];
            for (int i = 0; i < wx_count && i < WEATHER_MAX_HOURS; i++) {
                snprintf(buf, 16, "%02d:00", wx_hours[i].hour);
                lv_label_set_text(wx_hour_lbl[i], buf);

                // Icon: sun/moon/cloud/rain symbols
                const char *sym;
                int ic = wx_hours[i].icon;
                if (ic <= 3) sym = LV_SYMBOL_IMAGE;           // sun
                else if (ic <= 6) sym = LV_SYMBOL_EYE_CLOSE;  // cloudy
                else if (ic <= 12) sym = LV_SYMBOL_TINT;      // rain
                else if (ic <= 17) sym = LV_SYMBOL_DOWNLOAD;  // snow
                else if (ic <= 26) sym = LV_SYMBOL_IMAGE;     // night clear
                else sym = LV_SYMBOL_EYE_CLOSE;               // night cloudy
                lv_label_set_text(wx_icon_lbl[i], sym);

                snprintf(buf, 16, "%.0f\xC2\xB0", wx_hours[i].temperature);
                lv_label_set_text(wx_temp_lbl[i], buf);

                snprintf(buf, 16, "%.0f %s", wx_hours[i].wind_speed, wx_hours[i].wind_dir);
                lv_label_set_text(wx_wind_lbl[i], buf);
            }
        }

        // Clock update every second
        time_t now; time(&now); struct tm ti; localtime_r(&now, &ti);
        if (ti.tm_year > 124 && ti.tm_sec != last_sec) {
            last_sec = ti.tm_sec;
            char buf[64];
            snprintf(buf, 32, "%02d:%02d", ti.tm_hour, ti.tm_min);
            lv_label_set_text(lbl_time, buf);
            snprintf(buf, 32, ":%02d", ti.tm_sec);
            lv_label_set_text(lbl_seconds, buf);
            // Date + nameday on first run and every minute
            if (date_needs_update || ti.tm_sec == 0) {
                date_needs_update = false;
                snprintf(buf, 64, "%s, %d. %s %d",
                    cz_days[ti.tm_wday], ti.tm_mday, cz_months[ti.tm_mon], ti.tm_year + 1900);
                lv_label_set_text(lbl_date, buf);
                const char *name = nameday_get(ti.tm_mon + 1, ti.tm_mday);
                if (name && name[0]) {
                    snprintf(buf, 64, "Svatek ma: %s", name);
                    lv_label_set_text(lbl_nameday, buf);
                }
            }
        }

        lv_timer_handler();
        vTaskDelay(pdMS_TO_TICKS(5));
    }
}
