#!/bin/bash
OPENHAB_URL="${1:-http://localhost:8888}"

items=(
    "manualMode:OFF"
    "temporaryManualMode:OFF"
    "temporaryManualModeDurationSec:28800"
    "boostMode:OFF"
    "temporaryBoostMode:OFF"
    "temporaryBoostModeDurationSec:600"
    "humidityThreshold:60"
    "co2ThresholdLow:500"
    "co2ThresholdMid:700"
    "co2ThresholdHigh:900"
    "manualPower:50"
    "boostPower:95"
    "smokePower:0"
    "gasPower:95"
    "humidityPower:95"
    "co2PowerLow:15"
    "co2PowerMid:50"
    "co2PowerHigh:95"
    "basePower:50"
)

echo "Initializing ${#items[@]} items..."

for item in "${items[@]}"; do
    IFS=':' read -r name value <<< "$item"
    response=$(curl -s -o /dev/null -w "%{http_code}" \
        -X PUT \
        -H "Content-Type: text/plain" \
        --data "$value" \
        "$OPENHAB_URL/rest/items/$name/state")
    
    if [[ "$response" =~ ^(200|201|202|204)$ ]]; then
        echo "  ✓ $name = $value"
    else
        echo "  ✗ $name: HTTP $response"
    fi
done

echo "Done!"
