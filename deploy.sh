#!/bin/bash

# Script for building and deploying homeHAB library to OpenHAB
# Usage:
#   ./deploy.sh dev   # Deploy to development environment (local Docker)
#   ./deploy.sh prod  # Deploy to production environment (remote OpenHABian)

set -e  # Exit on any error

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== homeHAB Build & Deploy ===${NC}"

# Determine environment
ENV="${1:-dev}"

if [ "$ENV" != "dev" ] && [ "$ENV" != "prod" ]; then
    echo -e "${RED}Error: Invalid environment '${ENV}'${NC}"
    echo ""
    echo "Usage:"
    echo "  ./deploy.sh dev   # Deploy to development (local Docker)"
    echo "  ./deploy.sh prod  # Deploy to production (remote OpenHABian)"
    exit 1
fi

# Load environment configuration
ENV_FILE=".env.${ENV}"
if [ ! -f "$ENV_FILE" ]; then
    echo -e "${RED}Error: Configuration file not found: ${ENV_FILE}${NC}"
    echo ""
    if [ "$ENV" = "prod" ]; then
        echo "Create .env.prod based on .env.prod.example:"
        echo "  cp .env.prod.example .env.prod"
        echo "  # Edit .env.prod with your production settings"
    fi
    exit 1
fi

echo -e "${BLUE}Loading config from: ${ENV_FILE}${NC}"
source "$ENV_FILE"

# Display environment info
echo -e "${BLUE}Environment: ${YELLOW}${ENV}${NC}"
echo -e "${BLUE}MQTT Broker: ${YELLOW}${MQTT_BROKER_HOST}:${MQTT_BROKER_PORT}${NC}"
echo -e "${BLUE}MQTT Client ID: ${YELLOW}${MQTT_CLIENT_ID}${NC}"
echo -e "${BLUE}MQTT Topic Prefix: ${YELLOW}${MQTT_TOPIC_PREFIX}${NC}"

# Determine deployment type
REMOTE_DEPLOY=false
SSH_KEY="${SSH_KEY_PATH:+"-i $SSH_KEY_PATH"}"

if [ "$DEPLOY_TYPE" = "remote" ]; then
    # Remote deployment
    REMOTE_DEPLOY=true
    if [[ ! "$DEPLOY_TARGET" =~ ^[^@]+@[^:]+:.+$ ]]; then
        echo -e "${RED}Error: Invalid DEPLOY_TARGET for remote deployment: ${DEPLOY_TARGET}${NC}"
        echo "Expected format: user@host:/path"
        exit 1
    fi
    REMOTE_USER_HOST="${DEPLOY_TARGET%:*}"
    REMOTE_PATH="${DEPLOY_TARGET#*:}"
    echo -e "${BLUE}Deployment mode: ${YELLOW}REMOTE${NC}"
    echo -e "${BLUE}Target: ${YELLOW}${REMOTE_USER_HOST}:${REMOTE_PATH}${NC}"
else
    # Local deployment
    OPENHAB_CONF="$DEPLOY_TARGET"
    if [ ! -d "$OPENHAB_CONF" ]; then
        echo -e "${RED}Error: Directory does not exist: $OPENHAB_CONF${NC}"
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

# Step 2: Find the built shaded JAR (contains all dependencies)
JAR_FILE=$(find target -name "homeHAB-*-shaded.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    echo -e "${RED}Error: Shaded JAR file not found in target directory${NC}"
    echo -e "${RED}Make sure Maven shade plugin is configured correctly${NC}"
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
    if [ "$ENV" = "dev" ]; then
        echo "  2. Check logs: docker-compose logs -f openhab"
        echo "  3. If needed: docker-compose restart openhab"
    else
        echo "  2. Check logs: tail -f $OPENHAB_CONF/../logs/openhab.log"
        echo "  3. If needed: sudo systemctl restart openhab"
    fi
    echo ""
    echo -e "${BLUE}Local location:${NC} $LIB_DIR/$(basename $JAR_FILE)"
fi
