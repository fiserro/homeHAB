#!/bin/bash

# Restart OpenHAB and related services
# Usage: ./deploy-restart.sh [dev|prod]

set -e

# Source common functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# Load environment if not already loaded
if [ -z "$ENV" ]; then
    load_env "${1:-dev}"
fi

# Change to project root
cd "$PROJECT_ROOT"

print_step "Restarting services"

if is_remote_deploy; then
    # Remote restart
    parse_remote_target || exit 1
    SSH_KEY_OPT=$(get_ssh_key_opt)

    ssh $SSH_KEY_OPT "$REMOTE_USER_HOST" "
        echo 'Restarting OpenHAB...'
        sudo systemctl restart openhab
        if [ '${PYTHON_DEPLOY_ENABLED:-false}' = 'true' ]; then
            echo 'Restarting ${PYTHON_SERVICE_NAME:-hrv-bridge}...'
            sudo systemctl restart ${PYTHON_SERVICE_NAME:-hrv-bridge}
        fi
        echo 'Services restarted'
    "

    if [ $? -eq 0 ]; then
        print_success "Services restarted"
    else
        print_warning "Service restart may have failed"
    fi
else
    # Local restart (Docker)
    echo -e "${BLUE}Restarting local OpenHAB (Docker)...${NC}"
    docker-compose restart openhab

    if [ $? -eq 0 ]; then
        print_success "OpenHAB restarted"
    else
        print_warning "OpenHAB restart may have failed"
    fi
fi
