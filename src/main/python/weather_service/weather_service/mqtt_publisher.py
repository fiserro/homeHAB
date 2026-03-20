"""MQTT publisher for current weather conditions."""

import logging

import paho.mqtt.client as mqtt

from .fetcher import HourlyForecast

log = logging.getLogger("weather-service")

TOPIC_PREFIX = "homehab/weather"


class MqttPublisher:
    """Publishes current weather conditions to MQTT."""

    def __init__(self, host: str, port: int, client_id: str = "weather-service"):
        self.host = host
        self.port = port
        self.client = mqtt.Client(client_id=client_id)
        self._connected = False

        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            log.info("MQTT connected to %s:%d", self.host, self.port)
            self._connected = True
        else:
            log.error("MQTT connection failed: rc=%d", rc)

    def _on_disconnect(self, client, userdata, rc):
        if rc != 0:
            log.warning("MQTT disconnected unexpectedly (rc=%d)", rc)
        self._connected = False

    def connect(self):
        """Connect to MQTT broker."""
        self.client.connect(self.host, self.port, keepalive=60)
        self.client.loop_start()

    def publish_current(self, forecast: HourlyForecast):
        """Publish current weather conditions (first forecast hour)."""
        if not self._connected:
            log.warning("MQTT not connected, skipping publish")
            return

        topics = {
            f"{TOPIC_PREFIX}/temperature": f"{forecast.temperature:.1f}",
            f"{TOPIC_PREFIX}/summary": forecast.summary,
            f"{TOPIC_PREFIX}/icon": str(forecast.icon),
            f"{TOPIC_PREFIX}/wind": f"{forecast.wind_speed:.0f} {forecast.wind_dir}",
            f"{TOPIC_PREFIX}/precip": f"{forecast.precip_mm:.1f}",
            f"{TOPIC_PREFIX}/humidity": str(forecast.humidity),
        }

        for topic, value in topics.items():
            self.client.publish(topic, value, retain=True)

        log.info("Published current weather: %.1fC %s",
                 forecast.temperature, forecast.summary)

    def publish_hourly(self, forecasts: list[HourlyForecast]):
        """Publish all hourly forecast data as JSON-like MQTT messages."""
        if not self._connected:
            return

        # Publish count
        self.client.publish(f"{TOPIC_PREFIX}/hourly/count",
                            str(len(forecasts)), retain=True)

        # Publish each hour's data
        for i, f in enumerate(forecasts):
            prefix = f"{TOPIC_PREFIX}/hourly/{i}"
            self.client.publish(f"{prefix}/hour", str(f.hour), retain=True)
            self.client.publish(f"{prefix}/temperature",
                                f"{f.temperature:.1f}", retain=True)
            self.client.publish(f"{prefix}/icon", str(f.icon), retain=True)
            self.client.publish(f"{prefix}/wind_speed",
                                f"{f.wind_speed:.0f}", retain=True)
            self.client.publish(f"{prefix}/wind_dir", f.wind_dir, retain=True)
            self.client.publish(f"{prefix}/precip",
                                f"{f.precip_mm:.1f}", retain=True)
            self.client.publish(f"{prefix}/summary", f.summary, retain=True)

        log.info("Published %d hourly forecasts to MQTT", len(forecasts))

    def disconnect(self):
        """Disconnect from MQTT broker."""
        self.client.loop_stop()
        self.client.disconnect()
