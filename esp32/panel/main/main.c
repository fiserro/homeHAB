/**
 * HRV Panel - two screens with swipe navigation + MQTT + OTA.
 *
 * Safety: Ethernet + OTA start FIRST in app_main (before display).
 * MQTT runs in separate task - if it crashes, panel and OTA survive.
 */
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_heap_caps.h"
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
#include "bsp_board_extra.h"
#include <string.h>
#include <stdlib.h>
#include <stdio.h>

static const char *TAG = "panel";

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

/* ── Shared state (written by MQTT task, read by LVGL loop) ── */
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

/* ── LVGL widget refs ── */
static lv_obj_t *screen1, *screen2;
static int current_screen = 0;
static lv_obj_t *lbl_outdoor_val, *lbl_supply_val, *lbl_extract_val, *lbl_exhaust_val;
static lv_obj_t *lbl_room_val, *lbl_humidity_val, *lbl_co2_val, *lbl_pressure_val;
static lv_obj_t *lbl_power_val;
static lv_obj_t *power_bar;
static lv_obj_t *mode_btns[5];

/* ── Page dots ── */
static void create_dots(lv_obj_t *parent, int active)
{
    lv_obj_t *f = lv_obj_create(parent);
    lv_obj_set_size(f, 720, 24);
    lv_obj_align(f, LV_ALIGN_BOTTOM_MID, 0, 0);
    lv_obj_set_style_bg_opa(f, LV_OPA_TRANSP, 0);
    lv_obj_set_style_border_width(f, 0, 0);
    lv_obj_set_flex_flow(f, LV_FLEX_FLOW_ROW);
    lv_obj_set_flex_align(f, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER, LV_FLEX_ALIGN_CENTER);
    lv_obj_set_style_pad_column(f, 10, 0);
    lv_obj_set_scrollbar_mode(f, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(f, LV_OBJ_FLAG_SCROLLABLE);
    for (int i = 0; i < 2; i++) {
        lv_obj_t *d = lv_obj_create(f);
        lv_obj_set_size(d, 8, 8);
        lv_obj_set_style_radius(d, 4, 0);
        lv_obj_set_style_border_width(d, 0, 0);
        lv_obj_set_style_pad_all(d, 0, 0);
        lv_obj_set_style_bg_color(d, (i == active) ? C_TEXT : lv_color_hex(0x444444), 0);
        lv_obj_set_style_bg_opa(d, LV_OPA_COVER, 0);
        lv_obj_clear_flag(d, LV_OBJ_FLAG_SCROLLABLE);
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

/* ── Stat card with icon ── */
static lv_obj_t *create_stat_card(lv_obj_t *parent, int x, int y, int w, int h,
                                   const char *icon, lv_color_t ic_color,
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

    lv_obj_t *ic1 = lv_label_create(card);
    lv_label_set_text(ic1, icon);
    lv_obj_set_style_text_color(ic1, ic_color, 0);
    lv_obj_set_style_text_font(ic1, &lv_font_montserrat_12, 0);
    lv_obj_set_pos(ic1, 0, 1);

    lv_obj_t *lbl = lv_label_create(card);
    lv_label_set_text(lbl, label_text);
    lv_obj_set_style_text_color(lbl, C_TEXT_DIM, 0);
    lv_obj_set_style_text_font(lbl, &lv_font_montserrat_12, 0);
    lv_obj_set_pos(lbl, 18, 0);

    lv_obj_t *val = lv_label_create(card);
    lv_label_set_text(val, value_text);
    lv_obj_set_style_text_color(val, C_TEXT, 0);
    lv_obj_set_style_text_font(val, &lv_font_montserrat_22, 0);
    lv_obj_set_pos(val, 0, 30);

    return val;
}

/* ── Mode button with icon ── */
static lv_obj_t *create_mode_btn(lv_obj_t *parent, int x, int w,
                                  const char *icon, const char *text, bool active)
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

    lv_obj_t *ic = lv_label_create(btn);
    lv_label_set_text(ic, icon);
    lv_obj_set_style_text_color(ic, C_TEXT, 0);
    lv_obj_set_style_text_font(ic, &lv_font_montserrat_18, 0);
    lv_obj_align(ic, LV_ALIGN_CENTER, 0, -8);

    lv_obj_t *lbl = lv_label_create(btn);
    lv_label_set_text(lbl, text);
    lv_obj_set_style_text_color(lbl, C_TEXT, 0);
    lv_obj_set_style_text_font(lbl, &lv_font_montserrat_12, 0);
    lv_obj_align(lbl, LV_ALIGN_BOTTOM_MID, 0, -4);
    return btn;
}

static void update_mode_buttons(void)
{
    bool auto_mode = !state.manual_mode && !state.temp_manual_mode && !state.boost_mode;
    bool active[] = { auto_mode, state.temp_manual_mode, state.boost_mode,
                      state.manual_mode && state.power_val == 0, state.bypass_active };
    for (int i = 0; i < 5; i++) {
        lv_obj_set_style_bg_color(mode_btns[i], active[i] ? C_MODE_ACT : C_MODE_BG, 0);
        lv_obj_set_style_border_width(mode_btns[i], active[i] ? 0 : 1, 0);
    }
}

/* ── Build HRV Screen ── */
static void build_screen_hrv(lv_obj_t *scr)
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

    int cw=339, ch=100, g=10, x1=16, x2=16+cw+g, ys=80;
    // Icons: closest LVGL symbols matching Figma design
    #define IC_YELLOW lv_color_hex(0xd29922)
    #define IC_GREEN  lv_color_hex(0x3fb950)
    #define IC_ORANGE lv_color_hex(0xe3872d)
    #define IC_RED    lv_color_hex(0xf85149)
    #define IC_BLUE   lv_color_hex(0x58a6ff)
    lbl_outdoor_val = create_stat_card(scr, x1, ys,           cw,ch, LV_SYMBOL_IMAGE,    IC_YELLOW, "Outdoor Temperature","--");  // sun-like
    lbl_supply_val  = create_stat_card(scr, x2, ys,           cw,ch, LV_SYMBOL_DOWNLOAD, IC_GREEN,  "Supply Temperature", "--");  // arrow down/in
    lbl_extract_val = create_stat_card(scr, x1, ys+1*(ch+g),  cw,ch, LV_SYMBOL_UPLOAD,   IC_ORANGE, "Extract Temperature","--");  // arrow up/out
    lbl_exhaust_val = create_stat_card(scr, x2, ys+1*(ch+g),  cw,ch, LV_SYMBOL_REFRESH,  IC_RED,    "Exhaust Temperature","--");  // fan-like
    lbl_room_val    = create_stat_card(scr, x1, ys+2*(ch+g),  cw,ch, LV_SYMBOL_HOME,     IC_BLUE,   "Room Temperature",  "--");
    lbl_humidity_val= create_stat_card(scr, x2, ys+2*(ch+g),  cw,ch, LV_SYMBOL_TINT,     IC_BLUE,   "Humidity",          "--");
    lbl_co2_val     = create_stat_card(scr, x1, ys+3*(ch+g),  cw,ch, LV_SYMBOL_LOOP,     IC_YELLOW, "CO2 Level",         "--");  // wind-like
    lbl_pressure_val= create_stat_card(scr, x2, ys+3*(ch+g),  cw,ch, LV_SYMBOL_GPS,      IC_BLUE,   "Indoor Pressure",   "--");  // gauge-like

    /* Mode buttons */
    int my = ys + 4*(ch+g) + 8;
    lv_obj_t *mb = lv_obj_create(scr);
    lv_obj_set_pos(mb, 16, my);
    lv_obj_set_size(mb, 688, 56);
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

    /* Power bar */
    int py = my + 64;
    lv_obj_t *pc = lv_obj_create(scr);
    lv_obj_set_pos(pc, 16, py);
    lv_obj_set_size(pc, 688, 72);
    lv_obj_set_style_bg_color(pc, C_CARD, 0);
    lv_obj_set_style_bg_opa(pc, LV_OPA_COVER, 0);
    lv_obj_set_style_radius(pc, 14, 0);
    lv_obj_set_style_border_width(pc, 1, 0);
    lv_obj_set_style_border_color(pc, C_CARD_BRD, 0);
    lv_obj_set_style_pad_all(pc, 12, 0);
    lv_obj_set_scrollbar_mode(pc, LV_SCROLLBAR_MODE_OFF);
    lv_obj_clear_flag(pc, LV_OBJ_FLAG_SCROLLABLE);

    lv_obj_t *pl = lv_label_create(pc);
    lv_label_set_text(pl, "Power Level");
    lv_obj_set_style_text_color(pl, C_TEXT_DIM, 0);
    lv_obj_set_style_text_font(pl, &lv_font_montserrat_12, 0);
    lv_obj_set_pos(pl, 20, 0);

    lbl_power_val = lv_label_create(pc);
    lv_label_set_text(lbl_power_val, "--%");
    lv_obj_set_style_text_color(lbl_power_val, C_TEXT, 0);
    lv_obj_set_style_text_font(lbl_power_val, &lv_font_montserrat_24, 0);
    lv_obj_align(lbl_power_val, LV_ALIGN_TOP_RIGHT, 0, -4);

    power_bar = lv_bar_create(pc);
    lv_obj_set_pos(power_bar, 20, 30);
    lv_obj_set_size(power_bar, 620, 12);
    lv_bar_set_range(power_bar, 0, 100);
    lv_obj_set_style_bg_color(power_bar, C_BAR_BG, 0);
    lv_obj_set_style_radius(power_bar, 6, 0);
    lv_obj_set_style_bg_color(power_bar, C_BAR_IND, LV_PART_INDICATOR);
    lv_obj_set_style_radius(power_bar, 6, LV_PART_INDICATOR);

    create_dots(scr, 0);
}

static void build_screen_hello(lv_obj_t *scr)
{
    lv_obj_set_style_bg_color(scr, C_BG, 0);
    lv_obj_set_style_bg_opa(scr, LV_OPA_COVER, 0);
    lv_obj_clear_flag(scr, LV_OBJ_FLAG_SCROLLABLE);
    lv_obj_add_event_cb(scr, on_gesture, LV_EVENT_GESTURE, NULL);
    lv_obj_t *l = lv_label_create(scr);
    lv_label_set_text(l, "Hello World");
    lv_obj_set_style_text_color(l, C_TEXT, 0);
    lv_obj_set_style_text_font(l, &lv_font_montserrat_26, 0);
    lv_obj_align(l, LV_ALIGN_CENTER, 0, -10);
    lv_obj_t *s = lv_label_create(scr);
    lv_label_set_text(s, "Swipe right to go back");
    lv_obj_set_style_text_color(s, C_TEXT_DIM, 0);
    lv_obj_align(s, LV_ALIGN_CENTER, 0, 20);
    create_dots(scr, 1);
}

// Rotate through labels one at a time to avoid rendering storm
static int update_slot = 0;

static void update_ui_from_state(void)
{
    if (!state_dirty) return;

    switch (update_slot) {
        case 0:  lv_label_set_text(lbl_outdoor_val, state.temp_outdoor); break;
        case 1:  lv_label_set_text(lbl_supply_val, state.temp_supply); break;
        case 2:  lv_label_set_text(lbl_extract_val, state.temp_extract); break;
        case 3:  lv_label_set_text(lbl_exhaust_val, state.temp_exhaust); break;
        case 4:  lv_label_set_text(lbl_room_val, state.temp_inside); break;
        case 5:  lv_label_set_text(lbl_humidity_val, state.humidity); break;
        case 6:  lv_label_set_text(lbl_co2_val, state.co2); break;
        case 7:  lv_label_set_text(lbl_pressure_val, state.pressure); break;
        case 8:  lv_label_set_text(lbl_power_val, state.power);
                 lv_bar_set_value(power_bar, state.power_val, LV_ANIM_ON); break;
        case 9:  update_mode_buttons(); state_dirty = false; break;
    }
    update_slot = (update_slot + 1) % 10;
}

/* ══════════════════════════════════════════════════════
 *  MQTT - runs in its own task, crash-isolated
 * ══════════════════════════════════════════════════════ */

static void mqtt_data_handler(const char *topic, int topic_len, const char *data, int data_len)
{
    char val[32];
    int len = data_len < 31 ? data_len : 31;
    memcpy(val, data, len);
    val[len] = '\0';

    if (topic_len <= 14) return;
    const char *key = topic + 14;  // skip "homehab/state/"
    int kl = topic_len - 14;

    #define M(s) (kl == (int)strlen(s) && memcmp(key, s, kl) == 0)

    if      (M("temperature/inside"))    snprintf(state.temp_inside, 16, "%.1f\xC2\xB0""C", atof(val));
    else if (M("temperature/outdoor"))   snprintf(state.temp_outdoor, 16, "%.1f\xC2\xB0""C", atof(val));
    else if (M("temperature/supply"))    snprintf(state.temp_supply, 16, "%.1f\xC2\xB0""C", atof(val));
    else if (M("temperature/extract"))   snprintf(state.temp_extract, 16, "%.1f\xC2\xB0""C", atof(val));
    else if (M("temperature/exhaust"))   snprintf(state.temp_exhaust, 16, "%.1f\xC2\xB0""C", atof(val));
    else if (M("airHumidity"))           snprintf(state.humidity, 16, "%.1f%%", atof(val));
    else if (M("co2"))                   snprintf(state.co2, 16, "%d ppm", atoi(val));
    else if (M("pressure"))              snprintf(state.pressure, 16, "%d hPa", atoi(val));
    else if (M("hrvOutputPower"))      { state.power_val = atoi(val); snprintf(state.power, 8, "%d%%", state.power_val); }
    else if (M("manualMode"))            state.manual_mode = (strcmp(val,"ON")==0);
    else if (M("temporaryManualMode"))   state.temp_manual_mode = (strcmp(val,"ON")==0);
    else if (M("temporaryBoostMode"))    state.boost_mode = (strcmp(val,"ON")==0);
    else if (M("bypass"))                state.bypass_active = (strcmp(val,"ON")==0);
    else return;

    state_dirty = true;
    #undef M
}

static void mqtt_event_handler(void *args, esp_event_base_t base, int32_t id, void *data)
{
    esp_mqtt_event_handle_t event = data;
    switch (event->event_id) {
    case MQTT_EVENT_CONNECTED:
        ESP_LOGI(TAG, "MQTT connected");
        esp_mqtt_client_subscribe(event->client, "homehab/state/temperature/#", 0);
        esp_mqtt_client_subscribe(event->client, "homehab/state/airHumidity", 0);
        esp_mqtt_client_subscribe(event->client, "homehab/state/co2", 0);
        esp_mqtt_client_subscribe(event->client, "homehab/state/pressure", 0);
        esp_mqtt_client_subscribe(event->client, "homehab/state/hrvOutputPower", 0);
        esp_mqtt_client_subscribe(event->client, "homehab/state/manualMode", 0);
        esp_mqtt_client_subscribe(event->client, "homehab/state/temporaryManualMode", 0);
        esp_mqtt_client_subscribe(event->client, "homehab/state/temporaryBoostMode", 0);
        esp_mqtt_client_subscribe(event->client, "homehab/state/bypass", 0);
        esp_mqtt_client_publish(event->client, "homehab/panel/status", "online", 0, 1, 1);
        break;
    case MQTT_EVENT_DATA:
        if (event->topic && event->topic_len > 0 && event->data && event->data_len > 0) {
            mqtt_data_handler(event->topic, event->topic_len, event->data, event->data_len);
        }
        break;
    case MQTT_EVENT_DISCONNECTED:
        ESP_LOGW(TAG, "MQTT disconnected");
        break;
    case MQTT_EVENT_ERROR:
        ESP_LOGE(TAG, "MQTT error");
        break;
    default:
        break;
    }
}

static void mqtt_task(void *arg)
{
    // Wait for network to be ready
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

    esp_mqtt_client_handle_t client = esp_mqtt_client_init(&cfg);
    if (client) {
        esp_mqtt_client_register_event(client, ESP_EVENT_ANY_ID, mqtt_event_handler, NULL);
        esp_mqtt_client_start(client);
        ESP_LOGI(TAG, "MQTT client started");
    } else {
        ESP_LOGE(TAG, "MQTT client init failed");
    }

    // Task stays alive (MQTT runs in its own internal task)
    // but we keep this task to prevent stack being freed
    while (1) vTaskDelay(pdMS_TO_TICKS(60000));
}

/* ══════════════════════════════════════════════════════
 *  OTA HTTP Server
 * ══════════════════════════════════════════════════════ */

static esp_err_t ota_handler(httpd_req_t *req)
{
    ESP_LOGI("ota", "OTA started (%d bytes)", req->content_len);
    const esp_partition_t *part = esp_ota_get_next_update_partition(NULL);
    if (!part) { httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "No OTA partition"); return ESP_FAIL; }

    esp_ota_handle_t handle;
    esp_err_t err = esp_ota_begin(part, OTA_WITH_SEQUENTIAL_WRITES, &handle);
    if (err != ESP_OK) { httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "OTA begin failed"); return ESP_FAIL; }

    char *buf = malloc(4096);
    int received = 0;
    while (received < req->content_len) {
        int ret = httpd_req_recv(req, buf, 4096);
        if (ret <= 0) { if (ret == HTTPD_SOCK_ERR_TIMEOUT) continue; free(buf); esp_ota_abort(handle); return ESP_FAIL; }
        esp_ota_write(handle, buf, ret);
        received += ret;
    }
    free(buf);

    err = esp_ota_end(handle);
    if (err != ESP_OK) { httpd_resp_send_err(req, HTTPD_500_INTERNAL_SERVER_ERROR, "Validation failed"); return ESP_FAIL; }

    err = esp_ota_set_boot_partition(part);
    const esp_partition_t *boot = esp_ota_get_boot_partition();
    const esp_partition_t *running = esp_ota_get_running_partition();
    char resp[256];
    snprintf(resp, sizeof(resp), "written=%s err=%s boot=%s running=%s\n",
        part->label, esp_err_to_name(err), boot?boot->label:"?", running?running->label:"?");
    httpd_resp_sendstr(req, resp);
    vTaskDelay(pdMS_TO_TICKS(200));
    esp_restart();
    return ESP_OK;
}

static void start_ota_server(void)
{
    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.recv_wait_timeout = 30;
    httpd_handle_t server = NULL;
    if (httpd_start(&server, &config) == ESP_OK) {
        httpd_uri_t uri = { .uri = "/ota", .method = HTTP_POST, .handler = ota_handler };
        httpd_register_uri_handler(server, &uri);
        ESP_LOGI("ota", "OTA server on port 80");
    }
}

/* ══════════════════════════════════════════════════════
 *  Ethernet
 * ══════════════════════════════════════════════════════ */

static void on_got_ip(void *arg, esp_event_base_t base, int32_t id, void *data)
{
    ip_event_got_ip_t *event = (ip_event_got_ip_t *)data;
    ESP_LOGI("eth", "Got IP: " IPSTR, IP2STR(&event->ip_info.ip));
    // OTA server starts immediately - always available for recovery
    start_ota_server();
}

static void eth_init(void)
{
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_config_t nc = ESP_NETIF_DEFAULT_ETH();
    esp_netif_t *netif = esp_netif_new(&nc);

    eth_mac_config_t mc = ETH_MAC_DEFAULT_CONFIG();
    eth_esp32_emac_config_t ec = ETH_ESP32_EMAC_DEFAULT_CONFIG();
    ec.smi_gpio.mdc_num = 31;
    ec.smi_gpio.mdio_num = 52;
    esp_eth_mac_t *mac = esp_eth_mac_new_esp32(&ec, &mc);

    eth_phy_config_t pc = ETH_PHY_DEFAULT_CONFIG();
    pc.phy_addr = 1;
    pc.reset_gpio_num = 51;
    esp_eth_phy_t *phy = esp_eth_phy_new_ip101(&pc);

    esp_eth_config_t ethc = ETH_DEFAULT_CONFIG(mac, phy);
    esp_eth_handle_t eh = NULL;
    ESP_ERROR_CHECK(esp_eth_driver_install(&ethc, &eh));
    ESP_ERROR_CHECK(esp_netif_attach(netif, esp_eth_new_netif_glue(eh)));
    ESP_ERROR_CHECK(esp_event_handler_register(IP_EVENT, IP_EVENT_ETH_GOT_IP, &on_got_ip, NULL));
    ESP_ERROR_CHECK(esp_eth_start(eh));
    ESP_LOGI("eth", "Ethernet init, waiting for IP...");
}

/* ══════════════════════════════════════════════════════
 *  Main
 * ══════════════════════════════════════════════════════ */

void app_main(void)
{
    // 1. Display FIRST (BSP needs to init before other peripherals)
    bsp_display_cfg_t cfg = {
        .lvgl_port_cfg = ESP_LVGL_PORT_INIT_CONFIG(),
        .buffer_size = BSP_LCD_DRAW_BUFF_SIZE,
        .double_buffer = BSP_LCD_DRAW_BUFF_DOUBLE,
        .flags = { .buff_dma = true, .buff_spiram = false, .sw_rotate = false }
    };
    bsp_display_start_with_config(&cfg);
    bsp_display_backlight_on();

    // 2. Ethernet + OTA
    eth_init();

    // 3. UI
    screen1 = lv_obj_create(NULL);
    screen2 = lv_obj_create(NULL);
    build_screen_hrv(screen1);
    build_screen_hello(screen2);
    lv_screen_load(screen1);

    // 4. MQTT in separate task (crash-isolated, delayed start)
    xTaskCreate(mqtt_task, "mqtt", 8192, NULL, 1, NULL);

    // 5. LVGL loop
    int update_counter = 0;
    while (true) {
        // Update UI from MQTT state every ~500ms (not every frame)
        if (++update_counter >= 100) {
            update_counter = 0;
            update_ui_from_state();
        }
        vTaskDelay(pdMS_TO_TICKS(5));
        lv_task_handler();
    }
}
