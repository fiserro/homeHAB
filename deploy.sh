#!/bin/bash

# Script for building and deploying homeHAB library to OpenHAB (local or remote)
# Usage:
#   Local:  ./deploy.sh [local|/path/to/openhab/conf]
#   Remote: ./deploy.sh user@host:/path/to/openhab/conf
#   Remote with SSH key: ./deploy.sh user@host:/path/to/openhab/conf -i ~/.ssh/key

set -e  # Exit on any error

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== homeHAB Build & Deploy ===${NC}"

# Load configuration from .deploy-config if exists
CONFIG_FILE=".deploy-config"
if [ -f "$CONFIG_FILE" ]; then
    echo -e "${BLUE}Loading config from: ${CONFIG_FILE}${NC}"
    source "$CONFIG_FILE"
fi

# Parse arguments (command line overrides config file)
TARGET="${1:-$DEPLOY_TARGET}"
SSH_KEY="${SSH_KEY_PATH:+"-i $SSH_KEY_PATH"}"
REMOTE_DEPLOY=false

# Command line SSH key overrides config file
if [ "$2" = "-i" ] && [ -n "$3" ]; then
    SSH_KEY="-i $3"
fi

# Determine deployment type (local or remote)
if [[ "$TARGET" =~ ^[^@]+@[^:]+:.+$ ]]; then
    # Remote deployment (user@host:/path)
    REMOTE_DEPLOY=true
    REMOTE_USER_HOST="${TARGET%:*}"
    REMOTE_PATH="${TARGET#*:}"
    echo -e "${BLUE}Deployment mode: ${YELLOW}REMOTE${NC}"
    echo -e "${BLUE}Target: ${YELLOW}${REMOTE_USER_HOST}:${REMOTE_PATH}${NC}"
elif [ -n "$TARGET" ] && [ "$TARGET" != "local" ]; then
    # Local deployment with explicit path
    OPENHAB_CONF="$TARGET"
    echo -e "${BLUE}Deployment mode: ${YELLOW}LOCAL${NC}"
    echo -e "${BLUE}Target: ${YELLOW}${OPENHAB_CONF}${NC}"
else
    # Local deployment with auto-detection
    SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
    if [ -d "$SCRIPT_DIR/openhab-dev/conf" ]; then
        # Prefer local dev environment in this repo
        OPENHAB_CONF="$SCRIPT_DIR/openhab-dev/conf"
    elif [ -n "$OPENHAB_CONF" ]; then
        OPENHAB_CONF="$OPENHAB_CONF"
    elif [ -d "/etc/openhab/conf" ]; then
        OPENHAB_CONF="/etc/openhab/conf"
    elif [ -d "/usr/share/openhab/conf" ]; then
        OPENHAB_CONF="/usr/share/openhab/conf"
    elif [ -d "$HOME/openhab/conf" ]; then
        OPENHAB_CONF="$HOME/openhab/conf"
    else
        echo -e "${RED}Error: OpenHAB configuration directory not found!${NC}"
        echo ""
        echo "Usage:"
        echo "  Local dev:  ./deploy.sh local (deploys to ./openhab-dev/)"
        echo "  Local path: ./deploy.sh /path/to/openhab/conf"
        echo "  Remote:     ./deploy.sh user@host:/path/to/openhab/conf [-i ~/.ssh/key]"
        echo ""
        echo "Examples:"
        echo "  ./deploy.sh local"
        echo "  ./deploy.sh /opt/openhab/conf"
        echo "  ./deploy.sh openhab@192.168.1.100:/etc/openhab/conf"
        echo "  ./deploy.sh openhab@server.local:/opt/openhab/conf -i ~/.ssh/openhab_key"
        exit 1
    fi
    echo -e "${BLUE}Deployment mode: ${YELLOW}LOCAL${NC}"
    echo -e "${BLUE}Target: ${YELLOW}${OPENHAB_CONF}${NC}"
fi

# Step 1: Build the project
echo ""
echo -e "${BLUE}Step 1: Building project...${NC}"
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed!${NC}"
    exit 1
fi

# Step 2: Find the built JAR
JAR_FILE=$(find target -name "homeHAB-*.jar" -not -name "*-shaded.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    echo -e "${RED}Error: JAR file not found in target directory${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Built: $JAR_FILE${NC}"

# Step 3: Deploy
echo ""
if [ "$REMOTE_DEPLOY" = true ]; then
    # Remote deployment via SCP
    echo -e "${BLUE}Step 2: Deploying to remote OpenHAB server...${NC}"

    REMOTE_LIB_DIR="$REMOTE_PATH/automation/lib/java"
    JAR_BASENAME=$(basename "$JAR_FILE")

    # Create remote directory if it doesn't exist
    echo -e "${BLUE}Ensuring remote directory exists...${NC}"
    ssh $SSH_KEY "$REMOTE_USER_HOST" "mkdir -p $REMOTE_LIB_DIR"

    # Backup old JAR on remote server
    echo -e "${BLUE}Backing up old JAR on remote server...${NC}"
    ssh $SSH_KEY "$REMOTE_USER_HOST" "
        if [ -f $REMOTE_LIB_DIR/$JAR_BASENAME ]; then
            mv $REMOTE_LIB_DIR/$JAR_BASENAME $REMOTE_LIB_DIR/$JAR_BASENAME.backup.\$(date +%Y%m%d_%H%M%S)
            echo 'Backup created'
        else
            echo 'No existing JAR to backup'
        fi
    "

    # Copy JAR to remote server
    echo -e "${BLUE}Uploading JAR...${NC}"
    scp $SSH_KEY "$JAR_FILE" "$REMOTE_USER_HOST:$REMOTE_LIB_DIR/"

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Successfully deployed to: ${REMOTE_USER_HOST}:${REMOTE_LIB_DIR}/${JAR_BASENAME}${NC}"
    else
        echo -e "${RED}Failed to upload JAR file${NC}"
        exit 1
    fi

    # Show next steps
    echo ""
    echo -e "${GREEN}=== Deployment Complete ===${NC}"
    echo -e "${BLUE}Next steps:${NC}"
    echo "  1. OpenHAB will automatically detect and load the new library"
    echo "  2. Check logs: ssh $REMOTE_USER_HOST 'tail -f $REMOTE_PATH/../logs/openhab.log'"
    echo "  3. If needed: ssh $REMOTE_USER_HOST 'sudo systemctl restart openhab'"
    echo ""
    echo -e "${BLUE}Remote location:${NC} ${REMOTE_USER_HOST}:${REMOTE_LIB_DIR}/${JAR_BASENAME}"

else
    # Local deployment
    echo -e "${BLUE}Step 2: Deploying to local OpenHAB...${NC}"

    # Verify OpenHAB directory exists
    if [ ! -d "$OPENHAB_CONF" ]; then
        echo -e "${RED}Error: Directory does not exist: $OPENHAB_CONF${NC}"
        exit 1
    fi

    # Create automation/lib/java directory if it doesn't exist
    LIB_DIR="$OPENHAB_CONF/automation/lib/java"
    if [ ! -d "$LIB_DIR" ]; then
        echo -e "${BLUE}Creating directory: $LIB_DIR${NC}"
        mkdir -p "$LIB_DIR"
    fi

    # Backup old JAR if exists
    OLD_JAR="$LIB_DIR/$(basename $JAR_FILE)"
    if [ -f "$OLD_JAR" ]; then
        echo -e "${BLUE}Backing up old JAR...${NC}"
        mv "$OLD_JAR" "$OLD_JAR.backup.$(date +%Y%m%d_%H%M%S)"
    fi

    # Copy new JAR
    cp "$JAR_FILE" "$LIB_DIR/"

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Successfully deployed to: $LIB_DIR/$(basename $JAR_FILE)${NC}"
    else
        echo -e "${RED}Failed to copy JAR file${NC}"
        exit 1
    fi

    # Show next steps
    echo ""
    echo -e "${GREEN}=== Deployment Complete ===${NC}"
    echo -e "${BLUE}Next steps:${NC}"
    echo "  1. OpenHAB will automatically detect and load the new library"
    echo "  2. Check logs: tail -f $OPENHAB_CONF/../logs/openhab.log"
    echo "  3. If needed: sudo systemctl restart openhab"
    echo ""
    echo -e "${BLUE}Local location:${NC} $LIB_DIR/$(basename $JAR_FILE)"
fi
