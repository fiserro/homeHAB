#!/bin/bash
# Initialize output items with default values via OpenHAB REST API
# Usage: ./output-items-init.sh [openhab-url]

OPENHAB_URL="${1:-http://localhost:8888}"

items=(
    "hrvOutputPower:50"
)

echo "Initializing ${#items[@]} output items..."

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