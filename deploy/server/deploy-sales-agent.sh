#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT="${APP_ROOT:-/opt/ai-sales-agent}"
APP_USER="${APP_USER:-sales-agent}"
APP_GROUP="${APP_GROUP:-sales-agent}"
SERVICE_NAME="${SERVICE_NAME:-ai-sales-agent.service}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
MAX_RELEASES="${MAX_RELEASES:-5}"

archive="${1:-}"
release_id="${2:-}"

case "$APP_ROOT" in
  /opt/* | /srv/*) ;;
  *)
    echo "APP_ROOT must be an application directory below /opt or /srv" >&2
    exit 2
    ;;
esac

if [[ ! "$MAX_RELEASES" =~ ^[1-9][0-9]*$ ]]; then
  echo "MAX_RELEASES must be a positive integer" >&2
  exit 2
fi

if [[ ! "$release_id" =~ ^[0-9a-f]{40}$ ]]; then
  echo "release id must be a full Git commit SHA" >&2
  exit 2
fi

expected_archive="/tmp/sales-agent-${release_id}.tar.gz"
if [[ "$archive" != "$expected_archive" || -L "$archive" ]]; then
  echo "release archive must be a regular upload at $expected_archive" >&2
  exit 2
fi

if [[ ! -f "$archive" ]]; then
  echo "release archive does not exist: $archive" >&2
  exit 2
fi

while IFS= read -r archive_entry; do
  case "$archive_entry" in
    /* | .. | ../* | */../* | */..)
      echo "release archive contains an unsafe path: $archive_entry" >&2
      exit 2
      ;;
  esac
done < <(tar -tzf "$archive")

releases_dir="$APP_ROOT/releases"
release_dir="$releases_dir/$release_id"
current_link="$APP_ROOT/current"
next_link="$APP_ROOT/current.next"
previous_release=""

if [[ -L "$current_link" ]]; then
  previous_release="$(readlink -f "$current_link")"
  if [[ "$previous_release" != "$releases_dir/"* ]]; then
    echo "current release points outside the managed releases directory" >&2
    exit 2
  fi
fi

install -d -m 0755 -o root -g root "$APP_ROOT" "$releases_dir"
install -d -m 0755 -o "$APP_USER" -g "$APP_GROUP" "$APP_ROOT/logs"

if [[ -e "$release_dir" ]]; then
  if [[ "$previous_release" == "$release_dir" ]] && curl --fail --silent --max-time 3 "$HEALTH_URL" >/dev/null; then
    rm -f "$archive"
    echo "release $release_id is already active and healthy"
    exit 0
  fi
  rm -rf -- "$release_dir"
fi

install -d -m 0755 -o "$APP_USER" -g "$APP_GROUP" "$release_dir"
runuser -u "$APP_USER" -- tar --no-same-owner --no-same-permissions -xzf "$archive" -C "$release_dir"
test -s "$release_dir/backend/sales-agent.jar"
test -s "$release_dir/frontend/index.html"
chown -R root:root "$release_dir"
chmod -R a-w "$release_dir"
chmod -R a+rX "$release_dir"

ln -sfn "$release_dir" "$next_link"
mv -Tf "$next_link" "$current_link"
systemctl restart "$SERVICE_NAME"

healthy=false
for _ in $(seq 1 30); do
  if curl --fail --silent --show-error --max-time 3 "$HEALTH_URL" >/dev/null; then
    healthy=true
    break
  fi
  sleep 2
done

if [[ "$healthy" != true ]]; then
  echo "health check failed, restoring previous application release" >&2
  if [[ -n "$previous_release" && -d "$previous_release" ]]; then
    ln -sfn "$previous_release" "$next_link"
    mv -Tf "$next_link" "$current_link"
    systemctl restart "$SERVICE_NAME"
  else
    systemctl stop "$SERVICE_NAME" || true
    rm -f "$current_link"
  fi
  exit 1
fi

rm -f "$archive"

mapfile -t old_releases < <(find "$releases_dir" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' | sort -nr | tail -n "+$((MAX_RELEASES + 1))" | cut -d' ' -f2-)
for old_release in "${old_releases[@]}"; do
  if [[ "$old_release" != "$previous_release" && "$old_release" != "$release_dir" ]]; then
    rm -rf -- "$old_release"
  fi
done

echo "deployed release $release_id"
