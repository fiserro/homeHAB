# Weather Service

Fetches hourly weather forecasts from Meteosource API and distributes them via InfluxDB, MQTT and HTTP.

## Data Flow

```
Meteosource API → Weather Service → InfluxDB (Grafana dashboards)
                                   → MQTT (OpenHAB + ESP32 panel)
                                   → HTTP (chart PNG/RGB565 for ESP32)
```

## Features

- Fetches 24h hourly forecast every 10 minutes
- Writes to InfluxDB for Grafana visualization
- Publishes current conditions + hourly data to MQTT
- Renders dark-theme weather chart (matplotlib)
- Serves chart as PNG and RGB565 (for ESP32 framebuffer) via HTTP on port 3030

## MQTT Topics

Published to `homehab/weather/`:

| Topic | Description |
|-------|-------------|
| `current/temperature` | Current temperature (°C) |
| `current/summary` | Weather summary text |
| `current/icon` | Weather icon code |
| `hourly/<n>/temperature` | Hourly forecast temperature |
| `hourly/<n>/precip_mm` | Hourly precipitation (mm) |

## HTTP Endpoints

| Path | Content |
|------|---------|
| `/weather/chart.png` | Forecast chart (PNG) |
| `/weather/chart.rgb565` | Forecast chart (RGB565 binary for ESP32) |

## Deployment

Runs as Docker container. Deployed via:

```bash
./scripts/deploy-docker.sh prod
```

Configuration is in `docker-compose.yml` (service `weather-service`, profile `prod`).
API keys are in `.env.prod` (`METEOSOURCE_API_KEY`, `INFLUX_TOKEN`).

## Service Management

```bash
ssh openhab.home 'docker logs -f homehab-weather'
ssh openhab.home 'docker restart homehab-weather'
```
