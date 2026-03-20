idf_component_register(
    SRCS main.c namedays.c screen_forecast.c image_fetch.c font_cz_12.c font_cz_16.c font_cz_22.c weather_icons.c
    INCLUDE_DIRS .
    PRIV_REQUIRES esp_http_server esp_http_client app_update esp_eth esp_netif esp_event mqtt esp_timer esp_lcd
                  espressif__esp_lcd_touch espressif__esp_lcd_touch_gt911
                  waveshare__esp32_p4_wifi6_touch_lcd_4b bsp_extra)
