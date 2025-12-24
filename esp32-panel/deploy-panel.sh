#!/bin/bash
# Deploy HRV Panel via OTA (Over-The-Air)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

DEVICE_NAME="${1:-hrv-panel.local}"

echo "=== Deploying HRV Panel to $DEVICE_NAME ==="

# Check if device is reachable
echo "Checking device connectivity..."
if ! ping -c 1 -W 2 "$DEVICE_NAME" > /dev/null 2>&1; then
    echo "ERROR: Cannot reach $DEVICE_NAME"
    echo "Make sure the device is powered on and connected to the network."
    echo ""
    echo "To flash via USB instead, run:"
    echo "  esphome run hrv-panel.yaml"
    exit 1
fi

echo "Device is reachable. Starting OTA update..."
echo ""

# Run ESPHome with OTA
esphome run hrv-panel.yaml --device "$DEVICE_NAME"
