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
# Use ENV_FILE if set, otherwise default to .env.${ENV}
if [ -z "$ENV_FILE" ]; then
    ENV_FILE=".env.${ENV}"
fi

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
set -a  # Auto-export all variables
source "$ENV_FILE"
set +a

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

# Step 2: Run generator
echo ""
echo -e "${BLUE}Step 2: Running generator...${NC}"
mvn exec:java -q -Dexec.mainClass="io.github.fiserro.homehab.generator.Generator"

if [ $? -ne 0 ]; then
    echo -e "${RED}Generator failed!${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Generator completed${NC}"

# Step 3: Find the built shaded JAR (contains all dependencies)
JAR_FILE=$(find target -name "homeHAB-*-shaded.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    echo -e "${RED}Error: Shaded JAR file not found in target directory${NC}"
    echo -e "${RED}Make sure Maven shade plugin is configured correctly${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Built: $JAR_FILE${NC}"

# Step 4: Deploy
echo ""
if [ "$REMOTE_DEPLOY" = true ]; then
    # Remote deployment via SCP
    echo -e "${BLUE}Step 4: Deploying to remote OpenHAB server...${NC}"

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

    # Step 5: Deploy UI Pages to remote
    echo ""
    echo -e "${BLUE}Step 5: Deploying UI Pages...${NC}"

    PAGES_SOURCE="openhab-dev/conf/ui-pages.json"
    REMOTE_PAGES_DIR="$REMOTE_PATH/../userdata/jsondb"
    REMOTE_PAGES_TARGET="$REMOTE_PAGES_DIR/uicomponents_ui_page.json"

    if [ -f "$PAGES_SOURCE" ]; then
        # Ensure remote directory exists
        ssh $SSH_KEY "$REMOTE_USER_HOST" "mkdir -p $REMOTE_PAGES_DIR"

        # Copy pages file
        scp $SSH_KEY "$PAGES_SOURCE" "$REMOTE_USER_HOST:$REMOTE_PAGES_TARGET"

        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✓ UI Pages deployed: ${REMOTE_USER_HOST}:${REMOTE_PAGES_TARGET}${NC}"
        else
            echo -e "${YELLOW}Warning: Failed to copy UI pages${NC}"
        fi
    else
        echo -e "${YELLOW}No UI pages source found at: $PAGES_SOURCE${NC}"
    fi

    # Step 6: Deploy Python HRV Bridge (if enabled)
    if [ "${PYTHON_DEPLOY_ENABLED:-false}" = "true" ]; then
        echo ""
        echo -e "${BLUE}Step 6: Deploying Python HRV Bridge...${NC}"

        PYTHON_HOST="${PYTHON_DEPLOY_HOST:-$REMOTE_USER_HOST}"
        PYTHON_PKG_DIR="${PYTHON_PACKAGE_DIR:-src/main/python}"
        PYTHON_SVC="${PYTHON_SERVICE_NAME:-dac-bridge}"

        if [ ! -d "$PYTHON_PKG_DIR" ]; then
            echo -e "${RED}Error: Python package directory not found: $PYTHON_PKG_DIR${NC}"
        else
            # Create temporary archive of Python package
            PYTHON_ARCHIVE="/tmp/dac-bridge-deploy.tar.gz"
            echo -e "${BLUE}Creating Python package archive...${NC}"
            tar -czf "$PYTHON_ARCHIVE" -C "$PYTHON_PKG_DIR" .

            # Upload and install on remote host
            echo -e "${BLUE}Uploading to ${PYTHON_HOST}...${NC}"
            scp $SSH_KEY "$PYTHON_ARCHIVE" "$PYTHON_HOST:/tmp/"

            echo -e "${BLUE}Installing Python package...${NC}"
            ssh $SSH_KEY "$PYTHON_HOST" "
                cd /tmp && \
                rm -rf /tmp/dac-bridge-install && \
                mkdir -p /tmp/dac-bridge-install && \
                tar -xzf dac-bridge-deploy.tar.gz -C /tmp/dac-bridge-install && \
                cd /tmp/dac-bridge-install && \
                sudo pip3 install --break-system-packages -e . && \
                rm -rf /tmp/dac-bridge-deploy.tar.gz /tmp/dac-bridge-install
            "

            if [ $? -eq 0 ]; then
                echo -e "${GREEN}✓ Python HRV Bridge deployed${NC}"
            else
                echo -e "${YELLOW}Warning: Python deployment may have failed${NC}"
            fi

            rm -f "$PYTHON_ARCHIVE"

            # Deploy systemd service file
            SYSTEMD_SERVICE_FILE="systemd/${PYTHON_SVC}.service"
            if [ -f "$SYSTEMD_SERVICE_FILE" ]; then
                echo -e "${BLUE}Deploying systemd service file...${NC}"
                scp $SSH_KEY "$SYSTEMD_SERVICE_FILE" "$PYTHON_HOST:/tmp/${PYTHON_SVC}.service"
                ssh $SSH_KEY "$PYTHON_HOST" "
                    sudo mv /tmp/${PYTHON_SVC}.service /etc/systemd/system/${PYTHON_SVC}.service && \
                    sudo systemctl daemon-reload
                "
                if [ $? -eq 0 ]; then
                    echo -e "${GREEN}✓ Systemd service updated${NC}"
                else
                    echo -e "${YELLOW}Warning: Systemd service update may have failed${NC}"
                fi
            fi
        fi
    else
        echo ""
        echo -e "${YELLOW}Step 6: Skipping Python deployment (PYTHON_DEPLOY_ENABLED=false)${NC}"
    fi

    # Step 7: Restart services
    echo ""
    echo -e "${BLUE}Step 7: Restarting services...${NC}"

    ssh $SSH_KEY "$REMOTE_USER_HOST" "
        echo 'Restarting OpenHAB...'
        sudo systemctl restart openhab
        if [ '${PYTHON_DEPLOY_ENABLED:-false}' = 'true' ]; then
            echo 'Restarting ${PYTHON_SERVICE_NAME:-dac-bridge}...'
            sudo systemctl restart ${PYTHON_SERVICE_NAME:-dac-bridge}
        fi
        echo 'Services restarted'
    "

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Services restarted${NC}"
    else
        echo -e "${YELLOW}Warning: Service restart may have failed${NC}"
    fi

    # Show next steps
    echo ""
    echo -e "${GREEN}=== Deployment Complete ===${NC}"
    echo -e "${BLUE}Deployed components:${NC}"
    echo "  - OpenHAB JAR: ${REMOTE_USER_HOST}:${REMOTE_LIB_DIR}/${JAR_BASENAME}"
    if [ "${PYTHON_DEPLOY_ENABLED:-false}" = "true" ]; then
        echo "  - Python HRV Bridge: ${PYTHON_DEPLOY_HOST:-$REMOTE_USER_HOST}"
    fi
    echo ""
    echo -e "${BLUE}Useful commands:${NC}"
    echo "  - OpenHAB logs: ssh $REMOTE_USER_HOST 'sudo journalctl -u openhab -f'"
    echo "  - HRV Bridge logs: ssh $REMOTE_USER_HOST 'sudo journalctl -u ${PYTHON_SERVICE_NAME:-dac-bridge} -f'"
    echo "  - Restart OpenHAB: ssh $REMOTE_USER_HOST 'sudo systemctl restart openhab'"

else
    # Local deployment
    echo -e "${BLUE}Step 4: Deploying to local OpenHAB...${NC}"

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

    # Step 5: Deploy UI Pages
    echo ""
    echo -e "${BLUE}Step 5: Deploying UI Pages...${NC}"

    PAGES_SOURCE="$OPENHAB_CONF/ui-pages.json"
    PAGES_TARGET_DIR="$SCRIPT_DIR/openhab-dev/userdata/jsondb"
    PAGES_TARGET="$PAGES_TARGET_DIR/uicomponents_ui_page.json"

    if [ -f "$PAGES_SOURCE" ]; then
        # Ensure target directory exists
        mkdir -p "$PAGES_TARGET_DIR"

        # Copy pages file
        cp "$PAGES_SOURCE" "$PAGES_TARGET"

        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✓ UI Pages deployed: $PAGES_TARGET${NC}"
        else
            echo -e "${YELLOW}Warning: Failed to copy UI pages${NC}"
        fi
    else
        echo -e "${YELLOW}No UI pages source found at: $PAGES_SOURCE${NC}"
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
