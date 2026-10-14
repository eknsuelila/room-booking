#!/usr/bin/env bash
# =====================================================================================
# Continuous deployment for a Docker Compose app on a home-lab VM.
#
# GitHub can't reach a VM inside a private network, so the VM "pulls" instead:
# a systemd timer (see install-auto-deploy.sh) runs this script every 2 minutes.
#
#   1. git fetch            -> is there a new commit on origin/main?        no  -> exit
#   2. GitHub check runs    -> did CI (build + tests) pass for that commit? no  -> wait / skip
#   3. git reset --hard     -> move the working copy to the new commit
#   4. docker compose up    -> rebuild and restart the containers
#   5. health check         -> page returns 200 and API answers 401 (alive + secured)
#   6. if build or health check fails -> roll back to the previous commit and rebuild it
#
# Usage: auto-deploy.sh /home/<user>/apps/<app>
# Untracked files such as docker-compose.override.yml (secrets) are never touched.
# =====================================================================================
set -euo pipefail

APP_DIR="${1:?usage: auto-deploy.sh /path/to/app}"
APP="$(basename "$APP_DIR")"
BRANCH="${DEPLOY_BRANCH:-main}"
STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/auto-deploy"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"   # seconds to wait for the new version to become healthy

mkdir -p "$STATE_DIR"
log() { echo "$(date '+%F %T') [$APP] $*"; }

cd "$APP_DIR"

# Only one deployment per app at a time (the timer could fire while a slow build is running).
exec 9>"$STATE_DIR/$APP.lock"
flock -n 9 || exit 0

git fetch --quiet origin "$BRANCH"
CURRENT="$(git rev-parse HEAD)"
TARGET="$(git rev-parse "origin/$BRANCH")"
[ "$CURRENT" = "$TARGET" ] && exit 0                       # nothing new

# A commit whose CI already failed is remembered, so we don't ask GitHub again every 2 minutes
# (the unauthenticated GitHub API allows 60 requests per hour).
if grep -qx "$TARGET" "$STATE_DIR/$APP.failed" 2>/dev/null; then
  exit 0
fi

# ---- 2. Wait for CI: every GitHub Actions check run on the commit must have succeeded ----
REPO="$(git remote get-url origin | sed -E 's#^.*github\.com[:/]##; s#\.git$##')"
CI_STATE="$(curl -fsS -H 'Accept: application/vnd.github+json' \
    "https://api.github.com/repos/$REPO/commits/$TARGET/check-runs?per_page=100" | python3 -c '
import json, sys
runs = json.load(sys.stdin).get("check_runs", [])
if not runs:
    print("pending")                    # CI has not started yet
elif any(r["status"] != "completed" for r in runs):
    print("pending")                    # still running
elif all(r["conclusion"] in ("success", "skipped", "neutral") for r in runs):
    print("success")
else:
    print("failure")
')" || { log "could not read CI status from GitHub, will retry"; exit 0; }

case "$CI_STATE" in
  pending) log "new commit ${TARGET:0:7} found, waiting for CI to finish"; exit 0 ;;
  failure) log "CI FAILED for ${TARGET:0:7}, not deploying"; echo "$TARGET" >> "$STATE_DIR/$APP.failed"; exit 0 ;;
esac

# ---- 3 + 4. Deploy ----
log "deploying ${CURRENT:0:7} -> ${TARGET:0:7} ($(git log -1 --format=%s "$TARGET"))"
rollback() {
  log "$1, rolling back to ${CURRENT:0:7}"
  echo "$TARGET" >> "$STATE_DIR/$APP.failed"
  git reset --hard --quiet "$CURRENT"
  docker compose up -d --build --remove-orphans || log "ROLLBACK BUILD FAILED, manual attention needed"
  exit 1
}

git reset --hard --quiet "$TARGET"
docker compose up -d --build --remove-orphans || rollback "BUILD FAILED for ${TARGET:0:7}"

# ---- 5. Health check ----
healthy() {
  local port page api
  port="$(docker compose port frontend 80 2>/dev/null | awk -F: '{print $NF}')"
  [ -n "$port" ] || return 1
  page="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/")"
  api="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/api/auth/me")"
  [ "$page" = 200 ] && [ "$api" = 401 ]
}

for _ in $(seq 1 $((HEALTH_TIMEOUT / 5))); do
  if healthy; then
    log "deployed ${TARGET:0:7} successfully"
    docker image prune -f >/dev/null 2>&1 || true          # free disk space from old images
    exit 0
  fi
  sleep 5
done

# ---- 6. Roll back ----
rollback "HEALTH CHECK FAILED for ${TARGET:0:7}"
