#!/bin/sh

set -e

# Build the project.
./gradlew :microbank:build

# Build docker image.
docker build . -t microbank -f microbank/src/docker/Dockerfile

# Delete old containers (if any).
docker compose -p microbank -f microbank/src/docker/docker-compose.yml down -v

# Run project.
docker compose -p microbank -f microbank/src/docker/docker-compose.yml up

# Clean up.
docker compose -p microbank -f microbank/src/docker/docker-compose.yml down -v
