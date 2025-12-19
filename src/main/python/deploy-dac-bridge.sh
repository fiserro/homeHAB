#!/bin/bash
# Deploy DAC Bridge to Raspberry Pi
# Usage: ./deploy-dac-bridge.sh [user@host]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_HOST="${1:-robertfiser@openhab.home}"
REMOTE_TMP="/tmp/dac-bridge-install"

echo "=== DAC Bridge Deployment ==="
echo "Target: $TARGET_HOST"
echo ""

# Build wheel
echo "Building distribution..."
cd "$SCRIPT_DIR"
python3 -m pip install --quiet build
python3 -m build --wheel --outdir dist/

WHEEL_FILE=$(ls -t dist/*.whl | head -1)
echo "Built: $WHEEL_FILE"

# Copy files to remote
echo ""
echo "Copying files to $TARGET_HOST..."
ssh "$TARGET_HOST" "mkdir -p $REMOTE_TMP"
scp "$WHEEL_FILE" "$TARGET_HOST:$REMOTE_TMP/"
scp "$SCRIPT_DIR/dac-bridge.service" "$TARGET_HOST:$REMOTE_TMP/"

# Install on remote
echo ""
echo "Installing on remote host..."
ssh "$TARGET_HOST" bash << 'REMOTE_SCRIPT'
set -e
cd /tmp/dac-bridge-install

# Install wheel
echo "Installing Python package..."
sudo pip3 install --break-system-packages --force-reinstall *.whl

# Install systemd service
echo "Installing systemd service..."
sudo cp dac-bridge.service /etc/systemd/system/
sudo systemctl daemon-reload

# Enable and start service
echo "Enabling and starting service..."
sudo systemctl enable dac-bridge.service
sudo systemctl restart dac-bridge.service

# Show status
echo ""
echo "Service status:"
sudo systemctl status dac-bridge.service --no-pager || true

# Cleanup
rm -rf /tmp/dac-bridge-install
REMOTE_SCRIPT

echo ""
echo "=== Deployment complete ==="
echo ""
echo "Useful commands:"
echo "  ssh $TARGET_HOST 'sudo systemctl status dac-bridge'"
echo "  ssh $TARGET_HOST 'sudo journalctl -u dac-bridge -f'"
echo ""
echo "Test with:"
echo "  mosquitto_pub -h openhab.home -t 'homehab/dac/power/set' -m '50'"
