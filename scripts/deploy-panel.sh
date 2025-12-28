#!/bin/bash

# Deploy ESP32 HRV Panel using ESPHome
# Usage: ./deploy-panel.sh [--compile-only] [--device <ip>]

set -e

# Source common functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# Change to project root
cd "$PROJECT_ROOT"

print_step "Deploying ESP32 Panel"

# Configuration
PANEL_DIR="esp32-panel"
PANEL_CONFIG="hrv-panel.yaml"

# Parse arguments
COMPILE_ONLY=false
DEVICE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --compile-only)
            COMPILE_ONLY=true
            shift
            ;;
        --device)
            DEVICE="$2"
            shift 2
            ;;
        *)
            shift
            ;;
    esac
done

# Check if ESPHome is installed
if ! command -v esphome &> /dev/null; then
    print_error "ESPHome is not installed"
    echo "Install with: pip3 install esphome"
    exit 1
fi

# Check if panel config exists
if [ ! -f "$PANEL_DIR/$PANEL_CONFIG" ]; then
    print_error "Panel config not found: $PANEL_DIR/$PANEL_CONFIG"
    exit 1
fi

cd "$PANEL_DIR"

if [ "$COMPILE_ONLY" = true ]; then
    # Compile only
    echo -e "${BLUE}Compiling ESP32 panel firmware...${NC}"
    esphome compile "$PANEL_CONFIG"

    if [ $? -eq 0 ]; then
        print_success "Firmware compiled successfully"
    else
        print_error "Compilation failed"
        exit 1
    fi
else
    # Compile and upload
    echo -e "${BLUE}Compiling and uploading ESP32 panel firmware...${NC}"

    if [ -n "$DEVICE" ]; then
        # Upload to specific device
        esphome upload "$PANEL_CONFIG" --device "$DEVICE"
    else
        # Auto-discover or use USB
        esphome upload "$PANEL_CONFIG"
    fi

    if [ $? -eq 0 ]; then
        print_success "Panel firmware deployed"
    else
        print_error "Upload failed"
        exit 1
    fi
fi
