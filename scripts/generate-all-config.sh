#!/bin/bash
# Generate all OpenHAB configuration files
# Usage:
#   ./scripts/generate-all-config.sh --ssh user@host
#   ./scripts/generate-all-config.sh --mqtt-host zigbee.home

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "🚀 Generating all OpenHAB configuration files..."
echo "================================================"
echo ""

# Step 1: Generate input items from HrvState @InputItem annotations
echo "📋 Step 1/3: Generating HRV input items..."
python3 "$SCRIPT_DIR/generate-input-items.py"
echo ""

# Step 2: Generate output items from Control classes
echo "📋 Step 2/3: Generating output items..."
python3 "$SCRIPT_DIR/generate-output-items.py"
echo ""

# Step 3: Generate Zigbee devices configuration
echo "📋 Step 3/3: Generating Zigbee devices configuration..."
python3 "$SCRIPT_DIR/generate-zigbee-config.py" "$@"
echo ""

echo "================================================"
echo "✨ All configuration files generated successfully!"
echo ""
echo "📝 Next steps:"
echo "  1. Review generated files:"
echo "     - openhab-dev/conf/things/zigbee-devices.things"
echo "     - openhab-dev/conf/items/zigbee-devices.items"
echo "     - openhab-dev/conf/items/input-items.items"
echo "     - openhab-dev/conf/items/output-items.items"
echo "  2. Restart OpenHAB: docker-compose restart openhab"
echo "  3. Check logs: docker-compose logs -f openhab"
echo ""
