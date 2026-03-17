idf_component_register(
    SRCS main.c
    INCLUDE_DIRS .
    PRIV_REQUIRES esp_http_server app_update esp_eth esp_netif esp_event mqtt
                  waveshare__esp32_p4_wifi6_touch_lcd_4b bsp_extra)
