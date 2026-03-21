# MQTT Simulator

Publishes fake Zigbee sensor data to MQTT for development without real hardware.

## How It Works

Reads device definitions from `devices.yaml` and periodically publishes JSON payloads to `zigbee2mqtt/<device>` topics — the same format as real Zigbee2MQTT. OpenHAB cannot distinguish simulated data from real sensors.

## Configuration

Edit `devices.yaml` to define simulated devices and their value ranges.

## Deployment

Dev only — runs automatically with:

```bash
docker compose --profile dev up -d
```

Defined in `docker-compose.yml` (service `mqtt-simulator`, profile `dev`).
