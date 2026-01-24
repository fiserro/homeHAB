#!/bin/bash

# Deploy OpenHAB configuration to production (Raspberry Pi)
# Usage: ./deploy-openhab-prod.sh [--skip-restart] [--dry-run]
#
# Deploys:
# - JAR file → automation/lib/java/
# - items/*.items → items/
# - things/*.things → things/
# - automation/jsr223/*.java → automation/jsr223/
# - html/*.html → html/
# - ui-pages.json → userdata/jsondb/

set -e

# Source common functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# Parse arguments
SKIP_RESTART=false
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-restart)
            SKIP_RESTART=true
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        *)
            shift
            ;;
    esac
done

# Load production environment
load_env "prod" || exit 1

# Change to project root
cd "$PROJECT_ROOT"

echo -e "${BLUE}=== OpenHAB Production Deployment ===${NC}"
print_env_info

# Verify remote deployment
if ! is_remote_deploy; then
    print_error "Production deployment requires DEPLOY_TYPE=remote in .env.prod"
    exit 1
fi

parse_remote_target || exit 1
SSH_KEY_OPT=$(get_ssh_key_opt)

# OpenHABian paths
REMOTE_CONF="$REMOTE_PATH"
REMOTE_USERDATA="/var/lib/openhab"

# Local source directory
LOCAL_CONF="$PROJECT_ROOT/openhab-dev/conf"

# Verify local source exists
if [ ! -d "$LOCAL_CONF" ]; then
    print_error "Source directory not found: $LOCAL_CONF"
    exit 1
fi

# Function to run command (or print in dry-run mode)
run_cmd() {
    if [ "$DRY_RUN" = true ]; then
        echo -e "${YELLOW}[DRY-RUN] $*${NC}"
    else
        eval "$@"
    fi
}

# Function to deploy a directory
deploy_dir() {
    local src_dir="$1"
    local remote_dir="$2"
    local pattern="${3:-*}"

    if [ ! -d "$src_dir" ]; then
        print_warning "Source directory not found: $src_dir"
        return 0
    fi

    local files=$(find "$src_dir" -maxdepth 1 -name "$pattern" -type f 2>/dev/null)
    if [ -z "$files" ]; then
        print_warning "No files matching '$pattern' in $src_dir"
        return 0
    fi

    echo -e "${BLUE}Deploying $src_dir ($pattern) → $remote_dir${NC}"

    # Create remote directory
    run_cmd "ssh $SSH_KEY_OPT $REMOTE_USER_HOST 'sudo mkdir -p $remote_dir'"

    # Copy files to temp, then sudo move
    for file in $files; do
        local filename=$(basename "$file")
        run_cmd "scp $SSH_KEY_OPT '$file' '$REMOTE_USER_HOST:/tmp/$filename'"
        run_cmd "ssh $SSH_KEY_OPT $REMOTE_USER_HOST 'sudo mv /tmp/$filename $remote_dir/ && sudo chown openhab:openhab $remote_dir/$filename'"
        echo "  - $filename"
    done
}

# Function to deploy a single file
deploy_file() {
    local src_file="$1"
    local remote_path="$2"

    if [ ! -f "$src_file" ]; then
        print_warning "Source file not found: $src_file"
        return 0
    fi

    local remote_dir=$(dirname "$remote_path")
    local filename=$(basename "$src_file")

    echo -e "${BLUE}Deploying $src_file → $remote_path${NC}"

    # Create remote directory
    run_cmd "ssh $SSH_KEY_OPT $REMOTE_USER_HOST 'sudo mkdir -p $remote_dir'"

    # Copy file to temp, then sudo move
    run_cmd "scp $SSH_KEY_OPT '$src_file' '$REMOTE_USER_HOST:/tmp/$filename'"
    run_cmd "ssh $SSH_KEY_OPT $REMOTE_USER_HOST 'sudo mv /tmp/$filename $remote_path && sudo chown openhab:openhab $remote_path'"
}

# Step 1: Build JAR
print_step "Building JAR"
if [ "$DRY_RUN" = true ]; then
    echo -e "${YELLOW}[DRY-RUN] mvn clean package -DskipTests${NC}"
else
    mvn clean package -DskipTests -q
fi

# Find the built shaded JAR
JAR_FILE=$(find target -name "homeHAB-*-shaded.jar" 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ] && [ "$DRY_RUN" != true ]; then
    print_error "Shaded JAR file not found"
    exit 1
fi

# Step 2: Deploy JAR
print_step "Deploying JAR"
REMOTE_LIB_DIR="$REMOTE_CONF/automation/lib/java"
if [ -n "$JAR_FILE" ]; then
    JAR_BASENAME=$(basename "$JAR_FILE")
    run_cmd "ssh $SSH_KEY_OPT $REMOTE_USER_HOST 'sudo mkdir -p $REMOTE_LIB_DIR'"
    run_cmd "scp $SSH_KEY_OPT '$JAR_FILE' '$REMOTE_USER_HOST:/tmp/$JAR_BASENAME'"
    run_cmd "ssh $SSH_KEY_OPT $REMOTE_USER_HOST 'sudo mv /tmp/$JAR_BASENAME $REMOTE_LIB_DIR/ && sudo chown openhab:openhab $REMOTE_LIB_DIR/$JAR_BASENAME'"
    print_success "JAR deployed: $JAR_BASENAME"
fi

# Step 3: Deploy JSR223 scripts
print_step "Deploying JSR223 scripts"
deploy_dir "$LOCAL_CONF/automation/jsr223" "$REMOTE_CONF/automation/jsr223" "*.java"

# Fix script permissions (must be readable by OpenHAB)
run_cmd "ssh $SSH_KEY_OPT $REMOTE_USER_HOST 'sudo chmod 644 $REMOTE_CONF/automation/jsr223/*.java'"

# Step 4: Deploy items
print_step "Deploying items"
deploy_dir "$LOCAL_CONF/items" "$REMOTE_CONF/items" "*.items"

# Step 5: Deploy things
print_step "Deploying things"
deploy_dir "$LOCAL_CONF/things" "$REMOTE_CONF/things" "*.things"

# Step 5b: Fix MQTT clientID for production
# The dev config has clientID="homehab-dev", but production needs "homehab-prod"
print_step "Fixing MQTT clientID for production"
run_cmd "ssh $SSH_KEY_OPT $REMOTE_USER_HOST 'sudo sed -i \"s/clientID=\\\"homehab-dev\\\"/clientID=\\\"homehab-prod\\\"/\" $REMOTE_CONF/things/mqtt.things'"

# Step 6: Deploy HTML pages
print_step "Deploying HTML pages"
deploy_dir "$LOCAL_CONF/html" "$REMOTE_CONF/html" "*.html"

# Step 7: Deploy UI pages
print_step "Deploying UI pages"
PAGES_SOURCE="$LOCAL_CONF/ui-pages.json"
REMOTE_PAGES="$REMOTE_USERDATA/jsondb/uicomponents_ui_page.json"
deploy_file "$PAGES_SOURCE" "$REMOTE_PAGES"

# Step 8: Ensure java223 addon is in addons directory
# The marketplace-installed JAR doesn't load the script watcher properly
print_step "Checking java223 addon"
run_cmd "ssh $SSH_KEY_OPT $REMOTE_USER_HOST 'if [ ! -f /usr/share/openhab/addons/org.openhab.automation.java223-*.jar ]; then
    MARKETPLACE_JAR=\$(find /var/lib/openhab/marketplace -name \"org.openhab.automation.java223-*.jar\" 2>/dev/null | head -1)
    if [ -n \"\$MARKETPLACE_JAR\" ]; then
        sudo cp \"\$MARKETPLACE_JAR\" /usr/share/openhab/addons/
        sudo chown openhab:openhab /usr/share/openhab/addons/org.openhab.automation.java223-*.jar
        echo \"Copied java223 JAR to addons directory\"
    fi
fi'"

# Step 9: Restart OpenHAB
if [ "$SKIP_RESTART" = true ]; then
    print_step "Restarting OpenHAB"
    print_skip "Service restart (--skip-restart)"
else
    print_step "Restarting OpenHAB"
    run_cmd "ssh $SSH_KEY_OPT $REMOTE_USER_HOST 'sudo systemctl restart openhab'"
    print_success "OpenHAB restarted"

    # Wait for OpenHAB to start
    print_step "Waiting for OpenHAB to start"
    sleep 45

    # Step 10: Initialize items with default values
    print_step "Initializing items"
    run_cmd "mvn exec:java -Dexec.mainClass=\"io.github.fiserro.homehab.generator.Generator\" -Dexec.args=\"--initEnabled=true --habStateEnabled=false --mqttEnabled=false --openhabUrl=http://openhab.home:8080\" -q"
    print_success "Items initialized"
fi

# Summary
echo ""
echo -e "${GREEN}=== Production Deployment Complete ===${NC}"
echo ""
echo -e "${BLUE}Deployed to: ${YELLOW}${REMOTE_USER_HOST}${NC}"
echo ""
echo -e "${BLUE}Useful commands:${NC}"
echo "  - OpenHAB logs: ssh $REMOTE_USER_HOST 'sudo journalctl -u openhab -f'"
echo "  - OpenHAB status: ssh $REMOTE_USER_HOST 'sudo systemctl status openhab'"
echo "  - Web UI: http://openhab.home:8080"
