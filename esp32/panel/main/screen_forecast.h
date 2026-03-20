#pragma once
#include "lvgl.h"

lv_obj_t *screen_forecast_create(void);
void screen_forecast_refresh(void);
void screen_forecast_update(void);  /* Call from main loop (LVGL thread) */
