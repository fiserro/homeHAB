#!/bin/bash

# Deploy config files (items, things) to OpenHAB
# Usage: ./deploy-configs.sh [dev|prod]
# For dev: configs are already in the mounted volume
# For prod: copies from openhab-dev/conf to remote

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

print_step "Deploying configs"

if ! is_remote_deploy; then
    print_skip "Configs already in mounted volume for local dev"
    exit 0
fi

# Remote deployment
parse_remote_target || exit 1
SSH_KEY_OPT=$(get_ssh_key_opt)

LOCAL_CONF="$PROJECT_ROOT/openhab-dev/conf"
REMOTE_CONF="$REMOTE_PATH"

# Deploy items
echo -e "${BLUE}Deploying items...${NC}"
for file in "$LOCAL_CONF/items"/*.items; do
    if [ -f "$file" ]; then
        filename=$(basename "$file")
        cat "$file" | ssh $SSH_KEY_OPT "$REMOTE_USER_HOST" "sudo tee $REMOTE_CONF/items/$filename > /dev/null"
        echo "  - $filename"
    fi
done

# Deploy things
echo -e "${BLUE}Deploying things...${NC}"
for file in "$LOCAL_CONF/things"/*.things; do
    if [ -f "$file" ]; then
        filename=$(basename "$file")
        cat "$file" | ssh $SSH_KEY_OPT "$REMOTE_USER_HOST" "sudo tee $REMOTE_CONF/things/$filename > /dev/null"
        echo "  - $filename"
    fi
done

print_success "Configs deployed"
