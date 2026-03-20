"""Meteosource API client for hourly weather forecast."""

import logging
from dataclasses import dataclass, field
from datetime import datetime

import requests

log = logging.getLogger("weather-service")

METEOSOURCE_URL = "https://www.meteosource.com/api/v1/free/point"


@dataclass
class HourlyForecast:
    dt: datetime
    hour: int
    temperature: float
    icon: int
    summary: str
    wind_speed: float
    wind_dir: str
    precip_mm: float
    humidity: int = 0
    cloud_cover: int = 0


def fetch_hourly(api_key: str, lat: float, lon: float,
                 max_hours: int = 24) -> list[HourlyForecast]:
    """Fetch hourly forecast from Meteosource API.

    Returns list of HourlyForecast for the next max_hours hours.
    """
    params = {
        "lat": lat,
        "lon": lon,
        "sections": "hourly",
        "timezone": "Europe/Prague",
        "language": "en",
        "units": "metric",
        "key": api_key,
    }

    resp = requests.get(METEOSOURCE_URL, params=params, timeout=15)
    resp.raise_for_status()
    data = resp.json()

    hourly_data = data.get("hourly", {}).get("data", [])
    forecasts = []

    for entry in hourly_data[:max_hours]:
        date_str = entry.get("date", "")
        try:
            dt = datetime.fromisoformat(date_str)
        except (ValueError, TypeError):
            continue

        wind = entry.get("wind", {})
        precip = entry.get("precipitation", {})
        cloud = entry.get("cloud_cover", {})
        # cloud_cover can be a dict with "total" key or an int
        if isinstance(cloud, dict):
            cloud_val = int(cloud.get("total", 0))
        else:
            cloud_val = int(cloud) if cloud else 0

        forecasts.append(HourlyForecast(
            dt=dt,
            hour=dt.hour,
            temperature=float(entry.get("temperature", 0)),
            icon=int(entry.get("icon", 0)),
            summary=str(entry.get("summary", "")),
            wind_speed=float(wind.get("speed", 0)),
            wind_dir=str(wind.get("dir", "")),
            precip_mm=float(precip.get("total", 0)),
            humidity=int(entry.get("humidity", 0)) if isinstance(entry.get("humidity"), (int, float, str)) else 0,
            cloud_cover=cloud_val,
        ))

    log.info("Fetched %d hourly forecast entries", len(forecasts))
    if forecasts:
        f = forecasts[0]
        log.info("First: %02d:00 %.1fC icon=%d %s",
                 f.hour, f.temperature, f.icon, f.summary)

    return forecasts
