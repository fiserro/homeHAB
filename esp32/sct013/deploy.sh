#!/usr/bin/env bash
# Build and flash ESP32-C3 SCT013 firmware
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PIO="${PIO:-pio}"

cd "$SCRIPT_DIR"

echo "=== Building ESP32-C3 SCT013 firmware ==="
$PIO run

if [[ "${1:-}" == "--upload" || "${1:-}" == "-u" ]]; then
    echo "=== Flashing via USB ==="
    $PIO run -t upload
fi

if [[ "${1:-}" == "--monitor" || "${1:-}" == "-m" ]]; then
    echo "=== Opening serial monitor ==="
    $PIO device monitor
fi

echo "=== Done ==="
