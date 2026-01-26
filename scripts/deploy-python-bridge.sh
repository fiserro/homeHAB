#!/bin/bash

# Deploy Python HRV Bridge to remote server
# Usage: ./deploy-python-bridge.sh [dev|prod]
# Note: Only runs for prod environment with PYTHON_DEPLOY_ENABLED=true

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

print_step "Deploying Python HRV Bridge"

# Check if Python deployment is enabled
if [ "${PYTHON_DEPLOY_ENABLED:-false}" != "true" ]; then
    print_skip "Python deployment disabled (PYTHON_DEPLOY_ENABLED=false)"
    exit 0
fi

# Check if remote deployment
if ! is_remote_deploy; then
    print_skip "Python deployment only supported for remote environments"
    exit 0
fi

parse_remote_target || exit 1
SSH_KEY_OPT=$(get_ssh_key_opt)

PYTHON_HOST="${PYTHON_DEPLOY_HOST:-$REMOTE_USER_HOST}"
PYTHON_PKG_DIR="${PYTHON_PACKAGE_DIR:-src/main/python}"
PYTHON_SVC="${PYTHON_SERVICE_NAME:-hrv-bridge}"

if [ ! -d "$PYTHON_PKG_DIR" ]; then
    print_error "Python package directory not found: $PYTHON_PKG_DIR"
    exit 1
fi

# Create temporary archive of Python package
PYTHON_ARCHIVE="/tmp/hrv-bridge-deploy.tar.gz"
echo -e "${BLUE}Creating Python package archive...${NC}"
tar -czf "$PYTHON_ARCHIVE" -C "$PYTHON_PKG_DIR" .

# Upload and install on remote host
echo -e "${BLUE}Uploading to ${PYTHON_HOST}...${NC}"
scp $SSH_KEY_OPT "$PYTHON_ARCHIVE" "$PYTHON_HOST:/tmp/"

echo -e "${BLUE}Installing Python package...${NC}"
ssh $SSH_KEY_OPT "$PYTHON_HOST" "
    cd /tmp && \
    sudo rm -rf /tmp/hrv-bridge-install && \
    mkdir -p /tmp/hrv-bridge-install && \
    tar -xzf hrv-bridge-deploy.tar.gz -C /tmp/hrv-bridge-install && \
    cd /tmp/hrv-bridge-install && \
    sudo pip3 install --break-system-packages --force-reinstall . && \
    sudo rm -rf /tmp/hrv-bridge-deploy.tar.gz /tmp/hrv-bridge-install
"

if [ $? -eq 0 ]; then
    print_success "Python HRV Bridge deployed"
else
    print_warning "Python deployment may have failed"
fi

rm -f "$PYTHON_ARCHIVE"

# Deploy systemd service file
SYSTEMD_SERVICE_FILE="systemd/${PYTHON_SVC}.service"
if [ -f "$SYSTEMD_SERVICE_FILE" ]; then
    echo -e "${BLUE}Deploying systemd service file...${NC}"
    scp $SSH_KEY_OPT "$SYSTEMD_SERVICE_FILE" "$PYTHON_HOST:/tmp/${PYTHON_SVC}.service"
    ssh $SSH_KEY_OPT "$PYTHON_HOST" "
        sudo mv /tmp/${PYTHON_SVC}.service /etc/systemd/system/${PYTHON_SVC}.service && \
        sudo systemctl daemon-reload
    "
    if [ $? -eq 0 ]; then
        print_success "Systemd service updated"
    else
        print_warning "Systemd service update may have failed"
    fi
fi

# Restart service
echo -e "${BLUE}Restarting ${PYTHON_SVC} service...${NC}"
ssh $SSH_KEY_OPT "$PYTHON_HOST" "sudo systemctl restart ${PYTHON_SVC}"
if [ $? -eq 0 ]; then
    print_success "${PYTHON_SVC} service restarted"
else
    print_warning "Service restart may have failed"
fi
