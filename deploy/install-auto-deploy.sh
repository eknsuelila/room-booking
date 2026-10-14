#!/usr/bin/env bash
# =====================================================================================
# One-time setup ON THE VM: turn on automatic deployment for this app.
#
#   cd ~/apps/<app> && ./deploy/install-auto-deploy.sh
#
# Creates a systemd timer that runs deploy/auto-deploy.sh every 2 minutes as the current
# user (who must be able to run `docker` without sudo, i.e. be in the "docker" group).
# The unit files are shared by all apps in the same folder (systemd "template" units:
# auto-deploy@job-tracker, auto-deploy@expense-tracker, ...).
#
# Useful commands afterwards:
#   systemctl list-timers 'auto-deploy@*'          when each app is checked next
#   journalctl -u auto-deploy@<app> -f              watch deployments live
#   sudo systemctl start auto-deploy@<app>          check/deploy right now
#   sudo systemctl disable --now auto-deploy@<app>.timer   turn it off
# =====================================================================================
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$(basename "$APP_DIR")"
APPS_DIR="$(dirname "$APP_DIR")"
RUN_AS="$(id -un)"

command -v docker >/dev/null || { echo "docker is not installed"; exit 1; }
docker info >/dev/null 2>&1 || { echo "user '$RUN_AS' cannot run docker without sudo (add it to the docker group)"; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required"; exit 1; }
chmod +x "$APP_DIR/deploy/auto-deploy.sh"

sudo tee /etc/systemd/system/auto-deploy@.service >/dev/null <<UNIT
[Unit]
Description=Auto-deploy %i from GitHub (when CI passes)
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=oneshot
User=$RUN_AS
WorkingDirectory=$APPS_DIR/%i
ExecStart=/bin/bash $APPS_DIR/%i/deploy/auto-deploy.sh $APPS_DIR/%i
UNIT

sudo tee /etc/systemd/system/auto-deploy@.timer >/dev/null <<UNIT
[Unit]
Description=Check GitHub for new versions of %i every 2 minutes

[Timer]
OnBootSec=2min
OnUnitActiveSec=2min
RandomizedDelaySec=15

[Install]
WantedBy=timers.target
UNIT

sudo systemctl daemon-reload
sudo systemctl enable --now "auto-deploy@$APP.timer"
echo "Automatic deployment enabled for $APP (checks every 2 minutes)."
echo "Watch it with: journalctl -u auto-deploy@$APP -f"
