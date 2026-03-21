FROM python:3.11-slim

WORKDIR /app

# Install system dependencies for matplotlib
RUN apt-get update && apt-get install -y --no-install-recommends \
    libfreetype6 \
    && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml .
COPY weather_service/ weather_service/

RUN pip install --no-cache-dir .

ENTRYPOINT ["weather-service"]
