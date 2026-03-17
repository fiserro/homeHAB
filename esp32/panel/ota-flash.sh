#!/bin/bash
# Flash firmware to panel via OTA (HTTP POST)
# Usage: ./ota-flash.sh [panel-ip]

set -e

PANEL_IP="${1:-192.168.1.134}"
BIN="build/lvgl_demo_v9.bin"

if [ ! -f "$BIN" ]; then
    echo "Binary not found: $BIN"
    echo "Run 'idf.py build' first"
    exit 1
fi

SIZE=$(stat -f%z "$BIN" 2>/dev/null || stat -c%s "$BIN" 2>/dev/null)
echo "Flashing $BIN ($SIZE bytes) to $PANEL_IP..."

RESP=$(curl -s -X POST \
    --data-binary "@$BIN" \
    -H "Content-Type: application/octet-stream" \
    --connect-timeout 5 \
    --max-time 120 \
    "http://$PANEL_IP/ota")

echo "$RESP"

if echo "$RESP" | grep -q "err=ESP_OK"; then
    echo "OTA success. Panel is rebooting."
else
    echo "OTA failed!"
    exit 1
fi
