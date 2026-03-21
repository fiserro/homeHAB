#!/bin/bash

# Build and deploy Docker services
# Usage: ./deploy-docker.sh [dev|prod] [service...]
#
# Examples:
#   ./deploy-docker.sh dev                    # Build + deploy all dev services
#   ./deploy-docker.sh prod                   # Build + deploy all prod services
#   ./deploy-docker.sh prod weather-service   # Build + deploy only weather-service

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

ENV="${1:-dev}"
shift || true

# Remaining args are service names (empty = all)
SERVICES="$@"

cd "$PROJECT_ROOT"
load_env "$ENV" || exit 1

# Images that need to be built (have a Dockerfile)
BUILD_IMAGES=(
    "homehab-hrv-bridge:latest|src/main/python"
    "homehab-weather:latest|src/main/python/weather_service"
)

# Filter to requested services
if [ -n "$SERVICES" ]; then
    FILTERED=()
    for entry in "${BUILD_IMAGES[@]}"; do
        image="${entry%%|*}"
        name="${image%%:*}"
        name="${name#homehab-}"
        for svc in $SERVICES; do
            if [ "$svc" = "$name" ] || [ "$svc" = "weather-service" -a "$name" = "weather" ]; then
                FILTERED+=("$entry")
            fi
        done
    done
    BUILD_IMAGES=("${FILTERED[@]}")
fi

if [ "$ENV" = "dev" ]; then
    # === DEV: Build and run locally ===
    print_step "Building Docker images (dev)"

    docker compose --profile dev build $SERVICES

    print_step "Starting Docker services (dev)"
    docker compose --profile dev up -d $SERVICES

    print_success "Dev services deployed"
    docker compose --profile dev ps

elif [ "$ENV" = "prod" ]; then
    # === PROD: Build on Mac for ARM64, transfer to RPi ===
    parse_remote_target || exit 1
    SSH_KEY_OPT=$(get_ssh_key_opt)
    REMOTE_HOST="$REMOTE_USER_HOST"

    print_step "Building Docker images for ARM64 (prod)"

    for entry in "${BUILD_IMAGES[@]}"; do
        IMAGE="${entry%%|*}"
        CONTEXT="${entry##*|}"

        echo -e "${BLUE}Building ${IMAGE} from ${CONTEXT}...${NC}"
        docker buildx build \
            --platform linux/arm64 \
            --load \
            -t "$IMAGE" \
            "$CONTEXT"
        print_success "Built $IMAGE"
    done

    print_step "Transferring images to RPi"

    # Collect all image names
    IMAGE_NAMES=""
    for entry in "${BUILD_IMAGES[@]}"; do
        IMAGE="${entry%%|*}"
        IMAGE_NAMES="$IMAGE_NAMES $IMAGE"
    done

    if [ -n "$IMAGE_NAMES" ]; then
        echo -e "${BLUE}Saving and transferring:${IMAGE_NAMES}${NC}"
        docker save $IMAGE_NAMES | ssh $SSH_KEY_OPT "$REMOTE_HOST" "docker load"
        print_success "Images transferred"
    fi

    print_step "Deploying docker-compose.yml to RPi"

    scp $SSH_KEY_OPT "$PROJECT_ROOT/docker-compose.yml" "$REMOTE_HOST:~/docker-compose.yml"
    scp $SSH_KEY_OPT "$PROJECT_ROOT/.env.prod" "$REMOTE_HOST:~/.env"

    # Sync config directories
    scp $SSH_KEY_OPT -r "$PROJECT_ROOT/nginx" "$REMOTE_HOST:~/nginx"
    scp $SSH_KEY_OPT -r "$PROJECT_ROOT/cloudflare/config.yml" "$REMOTE_HOST:~/cloudflare/config.yml" 2>/dev/null || true

    print_step "Starting Docker services on RPi"

    if [ -n "$SERVICES" ]; then
        ssh $SSH_KEY_OPT "$REMOTE_HOST" "cd ~ && docker compose --profile prod up -d $SERVICES"
    else
        ssh $SSH_KEY_OPT "$REMOTE_HOST" "cd ~ && docker compose --profile prod up -d"
    fi

    print_success "Prod services deployed"
    ssh $SSH_KEY_OPT "$REMOTE_HOST" "docker ps --format 'table {{.Names}}\t{{.Status}}'"
fi
