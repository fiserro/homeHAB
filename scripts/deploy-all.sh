#!/bin/bash

# Deploy all homeHAB components
# Usage: ./deploy-all.sh [dev|prod] [--skip-panel] [--skip-restart]

set -e

# Source common functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# Parse arguments
ENV="${1:-dev}"
SKIP_PANEL=false
SKIP_RESTART=false

shift || true
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-panel)
            SKIP_PANEL=true
            shift
            ;;
        --skip-restart)
            SKIP_RESTART=true
            shift
            ;;
        *)
            shift
            ;;
    esac
done

# Change to project root
cd "$PROJECT_ROOT"

echo -e "${BLUE}=== homeHAB Deploy All ===${NC}"

# Load and validate environment
load_env "$ENV" || exit 1
print_env_info

# Step 1: Build
"$SCRIPT_DIR/deploy-build.sh"

# Step 2: Generator
"$SCRIPT_DIR/deploy-generator.sh" "$ENV"

# Step 3: Deploy configs (items, things)
"$SCRIPT_DIR/deploy-configs.sh" "$ENV"

# Step 4: Deploy JAR
"$SCRIPT_DIR/deploy-jar.sh" "$ENV"

# Step 5: Deploy UI Pages
"$SCRIPT_DIR/deploy-ui-pages.sh" "$ENV"

# Step 6: Deploy Python Bridge (prod only, if enabled)
"$SCRIPT_DIR/deploy-python-bridge.sh" "$ENV"

# Step 7: Deploy ESP32 Panel
if [ "$SKIP_PANEL" = true ]; then
    print_step "Deploying ESP32 Panel"
    print_skip "Panel deployment (--skip-panel)"
else
    "$SCRIPT_DIR/deploy-panel.sh" --compile-only
fi

# Step 8: Restart services
if [ "$SKIP_RESTART" = true ]; then
    print_step "Restarting services"
    print_skip "Service restart (--skip-restart)"
else
    "$SCRIPT_DIR/deploy-restart.sh" "$ENV"
fi

# Summary
echo ""
echo -e "${GREEN}=== Deployment Complete ===${NC}"
echo ""
echo -e "${BLUE}Deployed components:${NC}"
echo "  - Generated configs (items, things)"
echo "  - OpenHAB JAR"
echo "  - UI Pages"
if [ "${PYTHON_DEPLOY_ENABLED:-false}" = "true" ]; then
    echo "  - Python HRV Bridge"
fi
if [ "$SKIP_PANEL" != true ]; then
    echo "  - ESP32 Panel (compiled)"
fi

echo ""
echo -e "${BLUE}Useful commands:${NC}"
if is_remote_deploy; then
    parse_remote_target
    echo "  - OpenHAB logs: ssh $REMOTE_USER_HOST 'sudo journalctl -u openhab -f'"
    echo "  - HRV Bridge logs: ssh $REMOTE_USER_HOST 'sudo journalctl -u ${PYTHON_SERVICE_NAME:-hrv-bridge} -f'"
else
    echo "  - OpenHAB logs: docker-compose logs -f openhab"
    echo "  - Panel logs: esphome logs esp32-panel/hrv-panel.yaml"
fi
