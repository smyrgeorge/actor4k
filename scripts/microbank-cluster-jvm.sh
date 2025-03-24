#!/bin/sh

set -e

# Build the project.
./gradlew :microbank:jvmJar

# Build docker image.
docker build . -t microbank-jvm -f microbank/src/docker/jvm.Dockerfile

# Delete old containers (if any).
docker compose -p microbank -f microbank/src/docker/docker-compose-jvm.yml down -v

# Run project.
docker compose -p microbank -f microbank/src/docker/docker-compose-jvm.yml up

# Clean up.
docker compose -p microbank -f microbank/src/docker/docker-compose-jvm.yml down -v
