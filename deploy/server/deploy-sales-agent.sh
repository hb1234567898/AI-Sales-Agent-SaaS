#!/usr/bin/env bash
set -Eeuo pipefail

APP_ROOT="${APP_ROOT:-/www/wwwroot/ai.likeasuka.icu}"
APP_USER="${APP_USER:-root}"
APP_GROUP="${APP_GROUP:-root}"
SERVICE_NAME="${SERVICE_NAME:-ai-sales-agent.service}"
MAX_RELEASES="${MAX_RELEASES:-5}"

archive="${1:-}"
release_id="${2:-}"

case "$APP_ROOT" in
  /www/wwwroot/ai.likeasuka.icu | /opt/* | /srv/*) ;;
  *)
    echo "APP_ROOT must be /www/wwwroot/ai.likeasuka.icu or an application directory below /opt or /srv" >&2
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
  rm -rf -- "$release_dir"
fi

install -d -m 0755 -o "$APP_USER" -g "$APP_GROUP" "$release_dir"
tar --no-same-owner --no-same-permissions -xzf "$archive" -C "$release_dir"
test -s "$release_dir/backend/sales-agent.jar"
test -s "$release_dir/frontend/index.html"
chown -R root:root "$release_dir"
chmod -R a-w "$release_dir"
chmod -R a+rX "$release_dir"

ln -sfn "$release_dir" "$next_link"
mv -Tf "$next_link" "$current_link"
if ! systemctl restart "$SERVICE_NAME"; then
  echo "warning: release is active, but $SERVICE_NAME could not be restarted" >&2
fi

rm -f "$archive"

mapfile -t old_releases < <(find "$releases_dir" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' | sort -nr | tail -n "+$((MAX_RELEASES + 1))" | cut -d' ' -f2-)
for old_release in "${old_releases[@]}"; do
  if [[ "$old_release" != "$previous_release" && "$old_release" != "$release_dir" ]]; then
    rm -rf -- "$old_release"
  fi
done

echo "deployed release $release_id without backend health verification"
