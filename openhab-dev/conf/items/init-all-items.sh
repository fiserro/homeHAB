#!/bin/bash
# Initialize all items (input and output) with default values
# Usage: ./init-all-items.sh [openhab-url]

OPENHAB_URL="${1:-http://localhost:8888}"

echo "🔧 Initializing all items with default values..."
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "📥 Input items:"
"$SCRIPT_DIR/input-items-init.sh" "$OPENHAB_URL"
echo ""

echo "📤 Output items:"
"$SCRIPT_DIR/output-items-init.sh" "$OPENHAB_URL"
echo ""

echo "✨ All items initialized!"
