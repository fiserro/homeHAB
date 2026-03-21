FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libc6-dev \
    && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml requirements.txt ./
COPY hrv_bridge/ hrv_bridge/

RUN pip install --no-cache-dir .

ENTRYPOINT ["hrv-bridge"]
