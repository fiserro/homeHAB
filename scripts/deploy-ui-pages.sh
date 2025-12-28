#!/bin/bash

# Deploy UI pages to OpenHAB
# Usage: ./deploy-ui-pages.sh [dev|prod]

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

print_step "Deploying UI Pages"

PAGES_SOURCE="openhab-dev/conf/ui-pages.json"

if [ ! -f "$PAGES_SOURCE" ]; then
    print_warning "No UI pages source found at: $PAGES_SOURCE"
    exit 0
fi

if is_remote_deploy; then
    # Remote deployment
    parse_remote_target || exit 1
    SSH_KEY_OPT=$(get_ssh_key_opt)

    # On OpenHABian: conf=/etc/openhab, userdata=/var/lib/openhab
    REMOTE_PAGES_DIR="/var/lib/openhab/jsondb"
    REMOTE_PAGES_TARGET="$REMOTE_PAGES_DIR/uicomponents_ui_page.json"

    # Ensure remote directory exists (needs sudo)
    ssh $SSH_KEY_OPT "$REMOTE_USER_HOST" "sudo mkdir -p $REMOTE_PAGES_DIR && sudo chown openhab:openhab $REMOTE_PAGES_DIR"

    # Copy pages file (to temp, then sudo move)
    scp $SSH_KEY_OPT "$PAGES_SOURCE" "$REMOTE_USER_HOST:/tmp/uicomponents_ui_page.json"
    ssh $SSH_KEY_OPT "$REMOTE_USER_HOST" "sudo mv /tmp/uicomponents_ui_page.json $REMOTE_PAGES_TARGET && sudo chown openhab:openhab $REMOTE_PAGES_TARGET"

    if [ $? -eq 0 ]; then
        print_success "UI Pages deployed: ${REMOTE_USER_HOST}:${REMOTE_PAGES_TARGET}"
    else
        print_warning "Failed to copy UI pages"
    fi
else
    # Local deployment
    PAGES_TARGET_DIR="$PROJECT_ROOT/openhab-dev/userdata/jsondb"
    PAGES_TARGET="$PAGES_TARGET_DIR/uicomponents_ui_page.json"

    # Ensure target directory exists
    mkdir -p "$PAGES_TARGET_DIR"

    # Copy pages file
    cp "$PAGES_SOURCE" "$PAGES_TARGET"

    if [ $? -eq 0 ]; then
        print_success "UI Pages deployed: $PAGES_TARGET"
    else
        print_warning "Failed to copy UI pages"
    fi
fi
