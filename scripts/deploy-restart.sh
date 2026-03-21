#!/bin/bash

# Restart OpenHAB (the only native service)
# Docker services are managed by deploy-docker.sh
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

print_step "Restarting OpenHAB"

if is_remote_deploy; then
    # Prod: restart native OpenHAB on RPi
    parse_remote_target || exit 1
    SSH_KEY_OPT=$(get_ssh_key_opt)

    ssh $SSH_KEY_OPT "$REMOTE_USER_HOST" "
        echo 'Restarting OpenHAB...'
        sudo systemctl restart openhab
        echo 'OpenHAB restarted'
    "

    if [ $? -eq 0 ]; then
        print_success "OpenHAB restarted on $REMOTE_USER_HOST"
    else
        print_warning "OpenHAB restart may have failed"
    fi
else
    # Dev: restart Docker OpenHAB
    echo -e "${BLUE}Restarting local OpenHAB (Docker)...${NC}"
    docker compose --profile dev restart openhab

    if [ $? -eq 0 ]; then
        print_success "OpenHAB restarted"
    else
        print_warning "OpenHAB restart may have failed"
    fi
fi
