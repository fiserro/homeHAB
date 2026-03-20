#pragma once
#include "lvgl.h"

/* Sprite atlas: 5x5 grid, 48x48 per icon */
#define WX_ICON_SIZE 64
#define WX_ICON_COLS 5

extern const lv_image_dsc_t wx_icons_atlas;

/*
 * Grid layout (row, col):
 *   0,0: cloudy       0,1: mostly cloudy  0,2: light rain    0,3: rain         0,4: heavy rain
 *   1,0: sunny        1,1: partly sunny   1,2: sun+lt rain   1,3: sun+rain     1,4: sun+heavy rain
 *   2,0: wind+cloud   2,1: overcast       2,2: thunderstorm  2,3: snow         2,4: snowflakes
 *   3,0: moon         3,1: moon+clouds    3,2: raindrops     3,3: sleet        3,4: snowflake
 *   4,0: wind         4,1: hot            4,2: cold           4,3: umbrella     4,4: fog
 */

/* Map Meteosource icon code to (row, col) */
static inline void wx_icon_pos(int code, int *row, int *col)
{
    switch (code) {
    case 1:  *row = 1; *col = 0; break;  /* sunny */
    case 2:  *row = 1; *col = 1; break;  /* mostly sunny */
    case 3:  *row = 1; *col = 1; break;  /* partly sunny */
    case 4:  *row = 0; *col = 1; break;  /* mostly cloudy */
    case 5:  *row = 0; *col = 0; break;  /* cloudy */
    case 6:  *row = 2; *col = 1; break;  /* overcast */
    case 7:  *row = 1; *col = 2; break;  /* light rain */
    case 8:  *row = 0; *col = 3; break;  /* rain */
    case 9:  *row = 0; *col = 4; break;  /* heavy rain */
    case 10: *row = 2; *col = 2; break;  /* thunderstorm */
    case 11: *row = 2; *col = 2; break;  /* thunder + rain */
    case 12: *row = 2; *col = 2; break;  /* heavy thunder */
    case 13: *row = 2; *col = 3; break;  /* light snow */
    case 14: *row = 2; *col = 3; break;  /* snow */
    case 15: *row = 2; *col = 4; break;  /* heavy snow */
    case 16: *row = 2; *col = 3; break;  /* snow + thunder */
    case 17: *row = 2; *col = 4; break;  /* heavy snow + thunder */
    case 18: *row = 3; *col = 0; break;  /* night clear */
    case 19: *row = 3; *col = 0; break;  /* night clear */
    case 20: *row = 3; *col = 1; break;  /* night partly cloudy */
    case 21: *row = 3; *col = 1; break;  /* night mostly cloudy */
    case 22: *row = 0; *col = 0; break;  /* night overcast */
    case 23: *row = 0; *col = 2; break;  /* night light rain */
    case 24: *row = 0; *col = 3; break;  /* night rain */
    case 25: *row = 0; *col = 4; break;  /* night heavy rain */
    case 26: *row = 2; *col = 3; break;  /* night snow */
    default: *row = 0; *col = 0; break;  /* fallback: cloudy */
    }
}
