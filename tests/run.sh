#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p tests/reports
compose() {
  docker compose --project-name "${TRAVEL_TEST_PROJECT:-travelapp-tests}" \
    --env-file tests/.env.example -f compose.test.yaml "$@"
}
cleanup() {
  result=$?
  trap - EXIT
  compose logs --no-color > tests/reports/compose.log 2>&1 || true
  compose down --volumes --remove-orphans || true
  exit "$result"
}
trap cleanup EXIT
compose config --quiet
compose up --build --abort-on-container-exit --exit-code-from tests tests
