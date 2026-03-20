#!/bin/bash

# Deploy Weather Service to remote server
# Usage: ./deploy-weather-service.sh [dev|prod]
# Note: Only runs for prod environment with WEATHER_DEPLOY_ENABLED=true

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

print_step "Deploying Weather Service"

# Check if weather deployment is enabled
if [ "${WEATHER_DEPLOY_ENABLED:-false}" != "true" ]; then
    print_skip "Weather deployment disabled (WEATHER_DEPLOY_ENABLED=false)"
    exit 0
fi

# Check if remote deployment
if ! is_remote_deploy; then
    print_skip "Weather deployment only supported for remote environments"
    exit 0
fi

parse_remote_target || exit 1
SSH_KEY_OPT=$(get_ssh_key_opt)

WEATHER_HOST="${WEATHER_DEPLOY_HOST:-$REMOTE_USER_HOST}"
WEATHER_PKG_DIR="src/main/python/weather_service"
WEATHER_SVC="weather-service"

if [ ! -d "$WEATHER_PKG_DIR" ]; then
    print_error "Weather service directory not found: $WEATHER_PKG_DIR"
    exit 1
fi

# Create temporary archive of weather service package
WEATHER_ARCHIVE="/tmp/weather-service-deploy.tar.gz"
echo -e "${BLUE}Creating weather service package archive...${NC}"
tar -czf "$WEATHER_ARCHIVE" -C "$WEATHER_PKG_DIR" .

# Upload and install on remote host
echo -e "${BLUE}Uploading to ${WEATHER_HOST}...${NC}"
scp $SSH_KEY_OPT "$WEATHER_ARCHIVE" "$WEATHER_HOST:/tmp/"

echo -e "${BLUE}Installing weather service package...${NC}"
ssh $SSH_KEY_OPT "$WEATHER_HOST" "
    cd /tmp && \
    sudo rm -rf /tmp/weather-service-install && \
    mkdir -p /tmp/weather-service-install && \
    tar -xzf weather-service-deploy.tar.gz -C /tmp/weather-service-install && \
    cd /tmp/weather-service-install && \
    sudo pip3 install --break-system-packages --force-reinstall . && \
    sudo rm -rf /tmp/weather-service-deploy.tar.gz /tmp/weather-service-install
"

if [ $? -eq 0 ]; then
    print_success "Weather service deployed"
else
    print_warning "Weather service deployment may have failed"
fi

rm -f "$WEATHER_ARCHIVE"

# Deploy environment file with secrets
if [ -n "$METEOSOURCE_API_KEY" ] && [ -n "$INFLUX_TOKEN" ]; then
    echo -e "${BLUE}Deploying environment file...${NC}"
    ssh $SSH_KEY_OPT "$WEATHER_HOST" "
        sudo tee /etc/weather-service.env > /dev/null << 'ENVEOF'
METEOSOURCE_API_KEY=${METEOSOURCE_API_KEY}
INFLUX_TOKEN=${INFLUX_TOKEN}
ENVEOF
        sudo chmod 600 /etc/weather-service.env
    "
    if [ $? -eq 0 ]; then
        print_success "Environment file deployed"
    fi
fi

# Deploy systemd service file
SYSTEMD_SERVICE_FILE="systemd/${WEATHER_SVC}.service"
if [ -f "$SYSTEMD_SERVICE_FILE" ]; then
    echo -e "${BLUE}Deploying systemd service file...${NC}"
    scp $SSH_KEY_OPT "$SYSTEMD_SERVICE_FILE" "$WEATHER_HOST:/tmp/${WEATHER_SVC}.service"
    ssh $SSH_KEY_OPT "$WEATHER_HOST" "
        sudo mv /tmp/${WEATHER_SVC}.service /etc/systemd/system/${WEATHER_SVC}.service && \
        sudo systemctl daemon-reload
    "
    if [ $? -eq 0 ]; then
        print_success "Systemd service updated"
    else
        print_warning "Systemd service update may have failed"
    fi
fi

# Restart service
echo -e "${BLUE}Restarting ${WEATHER_SVC} service...${NC}"
ssh $SSH_KEY_OPT "$WEATHER_HOST" "sudo systemctl enable ${WEATHER_SVC} && sudo systemctl restart ${WEATHER_SVC}"
if [ $? -eq 0 ]; then
    print_success "${WEATHER_SVC} service restarted"
else
    print_warning "Service restart may have failed"
fi
