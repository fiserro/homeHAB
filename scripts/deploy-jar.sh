#!/bin/bash

# Deploy the homeHAB JAR to OpenHAB automation library
# Usage: ./deploy-jar.sh [dev|prod]

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

print_step "Deploying JAR"

# Find the built shaded JAR
JAR_FILE=$(find target -name "homeHAB-*-shaded.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    print_error "Shaded JAR file not found in target directory"
    print_error "Run deploy-build.sh first"
    exit 1
fi

JAR_BASENAME=$(basename "$JAR_FILE")

if is_remote_deploy; then
    # Remote deployment via SCP
    parse_remote_target || exit 1
    SSH_KEY_OPT=$(get_ssh_key_opt)

    REMOTE_LIB_DIR="$REMOTE_PATH/automation/lib/java"

    # Create remote directory if it doesn't exist
    echo -e "${BLUE}Ensuring remote directory exists...${NC}"
    ssh $SSH_KEY_OPT "$REMOTE_USER_HOST" "mkdir -p $REMOTE_LIB_DIR"

    # Backup old JAR on remote server
    echo -e "${BLUE}Backing up old JAR on remote server...${NC}"
    ssh $SSH_KEY_OPT "$REMOTE_USER_HOST" "
        if [ -f $REMOTE_LIB_DIR/$JAR_BASENAME ]; then
            mv $REMOTE_LIB_DIR/$JAR_BASENAME $REMOTE_LIB_DIR/$JAR_BASENAME.backup.\$(date +%Y%m%d_%H%M%S)
            echo 'Backup created'
        else
            echo 'No existing JAR to backup'
        fi
    "

    # Copy JAR to remote server
    echo -e "${BLUE}Uploading JAR...${NC}"
    scp $SSH_KEY_OPT "$JAR_FILE" "$REMOTE_USER_HOST:$REMOTE_LIB_DIR/"

    if [ $? -eq 0 ]; then
        print_success "Deployed to: ${REMOTE_USER_HOST}:${REMOTE_LIB_DIR}/${JAR_BASENAME}"
    else
        print_error "Failed to upload JAR file"
        exit 1
    fi
else
    # Local deployment
    OPENHAB_CONF="$DEPLOY_TARGET"

    if [ ! -d "$OPENHAB_CONF" ]; then
        print_error "Directory does not exist: $OPENHAB_CONF"
        exit 1
    fi

    LIB_DIR="$OPENHAB_CONF/automation/lib/java"

    # Create directory if it doesn't exist
    if [ ! -d "$LIB_DIR" ]; then
        echo -e "${BLUE}Creating directory: $LIB_DIR${NC}"
        mkdir -p "$LIB_DIR"
    fi

    # Backup old JAR if exists
    OLD_JAR="$LIB_DIR/$JAR_BASENAME"
    if [ -f "$OLD_JAR" ]; then
        echo -e "${BLUE}Backing up old JAR...${NC}"
        mv "$OLD_JAR" "$OLD_JAR.backup.$(date +%Y%m%d_%H%M%S)"
    fi

    # Copy new JAR
    cp "$JAR_FILE" "$LIB_DIR/"

    if [ $? -eq 0 ]; then
        print_success "Deployed to: $LIB_DIR/$JAR_BASENAME"
    else
        print_error "Failed to copy JAR file"
        exit 1
    fi
fi
