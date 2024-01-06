#!/bin/sh

# Build the project.
./gradlew build

# Build docker image.
docker build . -t microbank -f microbank/src/main/docker/Dockerfile

# Delete old containers (if any).
docker compose down -v

# Run project.
docker compose up

# Delete containers.
docker compose down -v
