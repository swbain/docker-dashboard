#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
./gradlew installDist -q
exec ./build/install/docker-dashboard/bin/docker-dashboard "$@"
