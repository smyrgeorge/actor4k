#!/bin/sh

set -e

# Build the project.
./gradlew :microbank:linkReleaseExecutableLinuxArm64 -Ptargets=jvm,wasmJs,wasmWasi,linuxArm64

# Build docker image.
docker build . -t microbank-native -f microbank/src/docker/native.Dockerfile

# Delete old containers (if any).
docker compose -p microbank -f microbank/src/docker/docker-compose-native.yml down -v

# Run project.
docker compose -p microbank -f microbank/src/docker/docker-compose-native.yml up

# Clean up.
docker compose -p microbank -f microbank/src/docker/docker-compose-native.yml down -v
