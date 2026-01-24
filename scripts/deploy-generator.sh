#!/bin/bash

# Run the homeHAB generator to create items, things, and UI configurations
# Usage: ./deploy-generator.sh [dev|prod]
# Note: Generator only runs for dev environment (generates into openhab-dev/conf)

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

print_step "Running generator"

# Generator always runs - it generates into openhab-dev/conf which is used by both dev and prod
# Run generator
mvn exec:java -q -Dexec.mainClass="io.github.fiserro.homehab.generator.Generator"

if [ $? -ne 0 ]; then
    print_error "Generator failed!"
    exit 1
fi

print_success "Generator completed"
