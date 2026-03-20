"""InfluxDB 2.x writer for weather forecast data."""

import logging

from influxdb_client import InfluxDBClient, Point, WritePrecision
from influxdb_client.client.write_api import SYNCHRONOUS

from .fetcher import HourlyForecast

log = logging.getLogger("weather-service")


class InfluxWriter:
    """Writes weather forecast data to InfluxDB 2.x."""

    def __init__(self, url: str, token: str, org: str, bucket: str):
        self.org = org
        self.bucket = bucket
        self.client = InfluxDBClient(url=url, token=token, org=org)
        self.write_api = self.client.write_api(write_options=SYNCHRONOUS)

    def write_forecast(self, forecasts: list[HourlyForecast]):
        """Write all hourly forecast points to InfluxDB."""
        points = []
        for f in forecasts:
            point = (
                Point("forecast")
                .tag("source", "meteosource")
                .field("temperature", f.temperature)
                .field("precip_mm", f.precip_mm)
                .field("wind_speed", f.wind_speed)
                .field("wind_dir", f.wind_dir)
                .field("humidity", f.humidity)
                .field("cloud_cover", f.cloud_cover)
                .field("icon", f.icon)
                .field("summary", f.summary)
                .time(f.dt, WritePrecision.S)
            )
            points.append(point)

        self.write_api.write(bucket=self.bucket, org=self.org, record=points)
        log.info("Wrote %d forecast points to InfluxDB", len(points))

    def close(self):
        """Close the InfluxDB client."""
        self.client.close()
