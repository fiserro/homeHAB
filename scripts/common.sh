#!/bin/bash

# Common functions and variables for deploy scripts
# Source this file in other scripts: source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

# Colors for output
export GREEN='\033[0;32m'
export BLUE='\033[0;34m'
export RED='\033[0;31m'
export YELLOW='\033[1;33m'
export NC='\033[0m' # No Color

# Get the root directory of the project (parent of scripts/)
export PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Load environment configuration
# Usage: load_env [dev|prod]
load_env() {
    local env="${1:-dev}"

    if [ "$env" != "dev" ] && [ "$env" != "prod" ]; then
        echo -e "${RED}Error: Invalid environment '${env}'${NC}"
        echo "Valid environments: dev, prod"
        return 1
    fi

    # Use ENV_FILE if set, otherwise default to .env.${env}
    local env_file="${ENV_FILE:-.env.${env}}"
    local env_path="$PROJECT_ROOT/$env_file"

    if [ ! -f "$env_path" ]; then
        echo -e "${RED}Error: Configuration file not found: ${env_path}${NC}"
        if [ "$env" = "prod" ]; then
            echo ""
            echo "Create .env.prod based on .env.prod.example:"
            echo "  cp .env.prod.example .env.prod"
            echo "  # Edit .env.prod with your production settings"
        fi
        return 1
    fi

    echo -e "${BLUE}Loading config from: ${env_file}${NC}"
    set -a  # Auto-export all variables
    source "$env_path"
    set +a

    export ENV="$env"
    export ENV_FILE="$env_file"

    return 0
}

# Print environment info
print_env_info() {
    echo -e "${BLUE}Environment: ${YELLOW}${ENV}${NC}"
    echo -e "${BLUE}MQTT Broker: ${YELLOW}${MQTT_BROKER_HOST}:${MQTT_BROKER_PORT}${NC}"
    echo -e "${BLUE}MQTT Client ID: ${YELLOW}${MQTT_CLIENT_ID}${NC}"
    echo -e "${BLUE}MQTT Topic Prefix: ${YELLOW}${MQTT_TOPIC_PREFIX}${NC}"

    if [ "$DEPLOY_TYPE" = "remote" ]; then
        echo -e "${BLUE}Deployment mode: ${YELLOW}REMOTE${NC}"
        echo -e "${BLUE}Target: ${YELLOW}${DEPLOY_TARGET}${NC}"
    else
        echo -e "${BLUE}Deployment mode: ${YELLOW}LOCAL${NC}"
        echo -e "${BLUE}Target: ${YELLOW}${DEPLOY_TARGET}${NC}"
    fi
}

# Check if we're doing remote deployment
is_remote_deploy() {
    [ "$DEPLOY_TYPE" = "remote" ]
}

# Get SSH key option for ssh/scp commands
get_ssh_key_opt() {
    if [ -n "$SSH_KEY_PATH" ]; then
        echo "-i $SSH_KEY_PATH"
    fi
}

# Parse remote target (user@host:/path) into components
# Sets: REMOTE_USER_HOST, REMOTE_PATH
parse_remote_target() {
    if [[ "$DEPLOY_TARGET" =~ ^([^@]+@[^:]+):(.+)$ ]]; then
        export REMOTE_USER_HOST="${BASH_REMATCH[1]}"
        export REMOTE_PATH="${BASH_REMATCH[2]}"
        return 0
    else
        echo -e "${RED}Error: Invalid DEPLOY_TARGET format: ${DEPLOY_TARGET}${NC}"
        echo "Expected format: user@host:/path"
        return 1
    fi
}

# Print a step header
print_step() {
    local step_name="$1"
    echo ""
    echo -e "${BLUE}=== ${step_name} ===${NC}"
}

# Print success message
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

# Print warning message
print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# Print error message
print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Print skip message
print_skip() {
    echo -e "${YELLOW}○ Skipping: $1${NC}"
}
