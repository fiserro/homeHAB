#!/bin/bash

# Deploy ESP32 HRV Panel (native ESP-IDF)
# Usage: ./deploy-panel.sh [--compile-only] [--device <ip>]

set -e

# Source common functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# Change to project root
cd "$PROJECT_ROOT"

print_step "Deploying ESP32 Panel"

# Configuration
PANEL_DIR="esp32/panel"
DEFAULT_DEVICE="panel.home"

# Parse arguments
COMPILE_ONLY=false
DEVICE="$DEFAULT_DEVICE"

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

# Check if panel directory exists
if [ ! -f "$PANEL_DIR/CMakeLists.txt" ]; then
    print_error "Panel project not found: $PANEL_DIR/CMakeLists.txt"
    exit 1
fi

cd "$PANEL_DIR"

if [ "$COMPILE_ONLY" = true ]; then
    echo -e "${BLUE}Compiling ESP32 panel firmware...${NC}"
    idf.py build

    if [ $? -eq 0 ]; then
        print_success "Firmware compiled successfully"
    else
        print_error "Compilation failed"
        exit 1
    fi
else
    echo -e "${BLUE}Compiling and flashing ESP32 panel firmware...${NC}"

    if [ -f "ota-flash.sh" ]; then
        ./ota-flash.sh "$DEVICE"
    else
        idf.py build
        echo -e "${BLUE}Flashing to ${DEVICE}...${NC}"
        idf.py -p "$DEVICE" flash
    fi

    if [ $? -eq 0 ]; then
        print_success "Panel firmware deployed to $DEVICE"
    else
        print_error "Flash failed"
        exit 1
    fi
fi
