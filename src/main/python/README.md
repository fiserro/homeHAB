# Python Services

Python microservices for homeHAB. Each runs as a Docker container.

## Modules

| Module | Profile | Description |
|--------|---------|-------------|
| [hrv_bridge](hrv_bridge/) | prod | MQTT to GPIO/PWM bridge for HRV control (requires RPi hardware) |
| [weather_service](weather_service/) | prod | Meteosource API → InfluxDB + MQTT + HTTP chart server |
| [mqtt_simulator](mqtt_simulator/) | dev | Publishes fake Zigbee sensor data for development |

## Deployment

```bash
# Deploy all Docker services to prod (builds images on Mac, transfers to RPi)
./scripts/deploy-docker.sh prod

# Start dev environment (builds locally)
docker compose --profile dev up -d
```

## Project Structure

```
src/main/python/
├── hrv_bridge/          # HRV Bridge (GPIO, SPI, 1-Wire)
│   ├── __init__.py
│   ├── co2_sensor.py
│   ├── current_sensor.py
│   ├── waveshare_*.py
│   └── Dockerfile
├── weather_service/     # Weather Service (API, charts)
│   ├── weather_service/
│   ├── pyproject.toml
│   └── Dockerfile
└── mqtt_simulator/      # MQTT Simulator (dev only)
    ├── simulator.py
    ├── devices.yaml
    └── Dockerfile
```
