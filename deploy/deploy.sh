#!/usr/bin/env bash
# Deploys (or rolls back to) a released image tag on the server.
#
#   deploy/deploy.sh <git-sha>     deploy that release
#   deploy/deploy.sh --rollback    go back to the release that ran before the current one
#
# Run from the repository checkout on the server (after a git fetch). Every successful deploy is appended to
# deploy/.releases, which is what --rollback reads. If the new backend does not become healthy
# in time, the script rolls back to the previous release on its own.
set -euo pipefail

cd "$(dirname "$0")"
COMPOSE=(docker compose -p cloudpilot -f docker-compose.prod.yml --env-file .env)
RELEASES=.releases
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-180}

current_tag() { grep -E '^IMAGE_TAG=' .env | cut -d= -f2; }

set_tag() {
  if grep -qE '^IMAGE_TAG=' .env; then
    sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$1/" .env
  else
    echo "IMAGE_TAG=$1" >> .env
  fi
}

wait_healthy() {
  local waited=0 state
  while (( waited < HEALTH_TIMEOUT )); do
    state=$(docker inspect -f '{{.State.Health.Status}}' "$("${COMPOSE[@]}" ps -q backend)" 2>/dev/null || echo starting)
    [[ "$state" == healthy ]] && return 0
    sleep 5; waited=$((waited + 5))
  done
  return 1
}

start() {
  # Use the Compose, Caddy and Prometheus config from the same commit as the images.
  git checkout --quiet "$1"
  set_tag "$1"
  "${COMPOSE[@]}" pull --quiet
  "${COMPOSE[@]}" up -d --remove-orphans
}

# The whole run lives in one function: bash parses it completely before it runs, so the
# git checkout inside start() can safely replace this file mid-run.
main() {
  [[ -f .env ]] || { echo "deploy/.env is missing (copy .env.prod.example)" >&2; exit 1; }

  if [[ "${1:-}" == "--rollback" ]]; then
    [[ -f $RELEASES ]] && (( $(wc -l < $RELEASES) >= 2 )) || { echo "No earlier release to roll back to" >&2; exit 1; }
    target=$(tail -n 2 $RELEASES | head -n 1)
    echo "Rolling back $(current_tag) -> $target"
    start "$target"
    wait_healthy || { echo "Rollback target $target is not healthy either; investigate" >&2; exit 1; }
    sed -i '$ d' $RELEASES   # forget the bad release so a second --rollback goes further back
    echo "Rolled back to $target"
    exit 0
  fi

  target=${1:?usage: deploy.sh <git-sha> | --rollback}
  [[ "$target" =~ ^[0-9a-f]{7,40}$ ]] || { echo "Expected a git commit SHA, got '$target'" >&2; exit 1; }
  previous=$(current_tag || true)

  echo "Deploying $target (was: ${previous:-nothing})"
  start "$target"
  if wait_healthy; then
    echo "$target" >> $RELEASES
    docker image prune -f > /dev/null
    echo "Deployed $target"
  else
    echo "Backend did not become healthy within ${HEALTH_TIMEOUT}s" >&2
    if [[ -n "$previous" && "$previous" != "$target" ]]; then
      echo "Automatic rollback to $previous" >&2
      start "$previous"
      wait_healthy || echo "Previous release is not healthy either; investigate" >&2
    fi
    exit 1
  fi
}

main "$@"; exit
