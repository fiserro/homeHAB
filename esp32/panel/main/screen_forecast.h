#pragma once
#include "lvgl.h"

void screen_forecast_init(void);                    /* Start fetch task */
void screen_forecast_update(void);                  /* Call from main loop (LVGL thread) */
void screen_forecast_set_home_img(lv_obj_t *img);   /* Set home screen chart widget */
