#!/usr/bin/env python3
"""
Weather Service - Fetches weather forecast from Meteosource API,
stores in InfluxDB, and publishes current conditions to MQTT.

Data Flow:
  Meteosource API -> InfluxDB (hourly forecast for Grafana)
                  -> MQTT (current conditions for ESP32 panel)
"""

import argparse
import logging
import signal
import sys
import time

from .fetcher import fetch_hourly
from .influx_writer import InfluxWriter
from .mqtt_publisher import MqttPublisher
from .chart_renderer import render_chart
from .http_server import start_http_server

DEFAULT_MQTT_HOST = "localhost"
DEFAULT_MQTT_PORT = 1883
DEFAULT_INFLUX_URL = "http://localhost:8086"
DEFAULT_INFLUX_ORG = "homehab"
DEFAULT_INFLUX_BUCKET = "weather"
DEFAULT_LAT = 49.19
DEFAULT_LON = 16.61
DEFAULT_FETCH_INTERVAL = 600  # 10 minutes
DEFAULT_HTTP_PORT = 3030
DEFAULT_CHART_PATH = "/tmp/weather_chart.png"

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
log = logging.getLogger("weather-service")


class WeatherService:
    """Main weather service that orchestrates fetch, store, and publish."""

    def __init__(self, api_key: str, lat: float, lon: float,
                 mqtt_host: str, mqtt_port: int,
                 influx_url: str, influx_token: str,
                 influx_org: str, influx_bucket: str,
                 fetch_interval: int,
                 http_port: int = DEFAULT_HTTP_PORT,
                 chart_path: str = DEFAULT_CHART_PATH):
        self.api_key = api_key
        self.lat = lat
        self.lon = lon
        self.fetch_interval = fetch_interval
        self.chart_path = chart_path
        self.http_port = http_port
        self.running = False

        # MQTT publisher
        self.mqtt = MqttPublisher(mqtt_host, mqtt_port)

        # InfluxDB writer (optional - skip if no token)
        self.influx = None
        if influx_token:
            try:
                self.influx = InfluxWriter(
                    influx_url, influx_token, influx_org, influx_bucket)
                log.info("InfluxDB writer initialized: %s/%s",
                         influx_url, influx_bucket)
            except Exception as e:
                log.warning("InfluxDB initialization failed: %s", e)

    def _fetch_and_publish(self):
        """Single fetch-store-publish cycle."""
        try:
            forecasts = fetch_hourly(self.api_key, self.lat, self.lon)
            if not forecasts:
                log.warning("No forecast data received")
                return

            # Write to InfluxDB
            if self.influx:
                try:
                    self.influx.write_forecast(forecasts)
                except Exception as e:
                    log.error("InfluxDB write failed: %s", e)

            # Render chart
            try:
                render_chart(forecasts, self.chart_path)
            except Exception as e:
                log.error("Chart rendering failed: %s", e)

            # Publish current conditions to MQTT
            self.mqtt.publish_current(forecasts[0])

            # Publish all hourly data to MQTT
            self.mqtt.publish_hourly(forecasts)

        except Exception as e:
            log.error("Fetch cycle failed: %s", e)

    def start(self):
        """Start the weather service main loop."""
        self.running = True

        # Start HTTP server for chart images
        start_http_server(self.http_port, self.chart_path)

        # Connect MQTT
        try:
            self.mqtt.connect()
        except Exception as e:
            log.error("MQTT connection failed: %s", e)

        # Wait briefly for MQTT connection
        time.sleep(2)

        log.info("Weather service started (interval: %ds, location: %.2f,%.2f)",
                 self.fetch_interval, self.lat, self.lon)

        # Initial fetch
        self._fetch_and_publish()

        # Main loop
        while self.running:
            for _ in range(self.fetch_interval * 10):
                if not self.running:
                    break
                time.sleep(0.1)

            if self.running:
                self._fetch_and_publish()

    def stop(self):
        """Stop the weather service."""
        self.running = False
        log.info("Shutting down weather service")

        self.mqtt.disconnect()
        if self.influx:
            self.influx.close()

        log.info("Weather service stopped")


def main():
    parser = argparse.ArgumentParser(
        description="Weather Service - Meteosource API to InfluxDB/MQTT"
    )
    parser.add_argument(
        "--api-key", required=True,
        help="Meteosource API key"
    )
    parser.add_argument(
        "--lat", type=float, default=DEFAULT_LAT,
        help=f"Latitude (default: {DEFAULT_LAT})"
    )
    parser.add_argument(
        "--lon", type=float, default=DEFAULT_LON,
        help=f"Longitude (default: {DEFAULT_LON})"
    )
    parser.add_argument(
        "--mqtt-host", "-H", default=DEFAULT_MQTT_HOST,
        help=f"MQTT broker host (default: {DEFAULT_MQTT_HOST})"
    )
    parser.add_argument(
        "--mqtt-port", "-p", type=int, default=DEFAULT_MQTT_PORT,
        help=f"MQTT broker port (default: {DEFAULT_MQTT_PORT})"
    )
    parser.add_argument(
        "--influx-url", default=DEFAULT_INFLUX_URL,
        help=f"InfluxDB URL (default: {DEFAULT_INFLUX_URL})"
    )
    parser.add_argument(
        "--influx-token", default="",
        help="InfluxDB auth token (empty = skip InfluxDB)"
    )
    parser.add_argument(
        "--influx-org", default=DEFAULT_INFLUX_ORG,
        help=f"InfluxDB organization (default: {DEFAULT_INFLUX_ORG})"
    )
    parser.add_argument(
        "--influx-bucket", default=DEFAULT_INFLUX_BUCKET,
        help=f"InfluxDB bucket (default: {DEFAULT_INFLUX_BUCKET})"
    )
    parser.add_argument(
        "--fetch-interval", type=int, default=DEFAULT_FETCH_INTERVAL,
        help=f"Fetch interval in seconds (default: {DEFAULT_FETCH_INTERVAL})"
    )
    parser.add_argument(
        "--http-port", type=int, default=DEFAULT_HTTP_PORT,
        help=f"HTTP port for chart PNG server (default: {DEFAULT_HTTP_PORT})"
    )
    parser.add_argument(
        "--chart-path", default=DEFAULT_CHART_PATH,
        help=f"Path to rendered chart PNG (default: {DEFAULT_CHART_PATH})"
    )
    parser.add_argument(
        "--verbose", "-v", action="store_true",
        help="Enable verbose logging"
    )

    args = parser.parse_args()

    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)

    service = WeatherService(
        api_key=args.api_key,
        lat=args.lat,
        lon=args.lon,
        mqtt_host=args.mqtt_host,
        mqtt_port=args.mqtt_port,
        influx_url=args.influx_url,
        influx_token=args.influx_token,
        influx_org=args.influx_org,
        influx_bucket=args.influx_bucket,
        fetch_interval=args.fetch_interval,
        http_port=args.http_port,
        chart_path=args.chart_path,
    )

    def signal_handler(signum, frame):
        log.info("Received signal %d", signum)
        service.stop()
        sys.exit(0)

    signal.signal(signal.SIGTERM, signal_handler)
    signal.signal(signal.SIGINT, signal_handler)

    service.start()


if __name__ == "__main__":
    main()
