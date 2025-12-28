#!/bin/bash

# Build the homeHAB project with Maven
# Usage: ./deploy-build.sh [dev|prod]

set -e

# Source common functions
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

# Change to project root
cd "$PROJECT_ROOT"

print_step "Building project"

# Run Maven build
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    print_error "Build failed!"
    exit 1
fi

# Find the built shaded JAR
JAR_FILE=$(find target -name "homeHAB-*-shaded.jar" | head -1)

if [ -z "$JAR_FILE" ]; then
    print_error "Shaded JAR file not found in target directory"
    print_error "Make sure Maven shade plugin is configured correctly"
    exit 1
fi

print_success "Built: $JAR_FILE"

# Export JAR_FILE for other scripts
echo "$JAR_FILE"
