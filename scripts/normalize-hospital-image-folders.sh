#!/usr/bin/env bash
set -euo pipefail

IMAGE_ROOT="${1:-${HOSPITAL_IMAGE_HOST_PATH:-/var/ut-image}}"

if [ ! -d "$IMAGE_ROOT" ]; then
  echo "Image root not found: $IMAGE_ROOT"
  exit 0
fi

echo "Normalizing hospital image folders under $IMAGE_ROOT"

find "$IMAGE_ROOT" -mindepth 1 -maxdepth 1 -type d | sort | while IFS= read -r legacy_dir; do
  legacy_name="$(basename "$legacy_dir")"
  case "$legacy_name" in
    *_*) continue ;;
  esac

  legacy_logo_dir="$legacy_dir/LOGO"
  if [ ! -d "$legacy_logo_dir" ]; then
    continue
  fi

  mapfile -t candidates < <(find "$IMAGE_ROOT" -mindepth 1 -maxdepth 1 -type d -name "${legacy_name}_*" | sort)
  if [ "${#candidates[@]}" -ne 1 ]; then
    echo "Skip $legacy_name: expected one canonical ${legacy_name}_* folder, found ${#candidates[@]}"
    continue
  fi

  canonical_dir="${candidates[0]}"
  canonical_logo_dir="$canonical_dir/LOGO"
  mkdir -p "$canonical_logo_dir"

  find "$legacy_logo_dir" -mindepth 1 -maxdepth 1 -type f | while IFS= read -r file_path; do
    file_name="$(basename "$file_path")"
    target_path="$canonical_logo_dir/$file_name"
    if [ -e "$target_path" ]; then
      target_path="$canonical_logo_dir/${file_name%.*}-migrated-${RANDOM}.${file_name##*.}"
    fi
    mv "$file_path" "$target_path"
    echo "Moved $legacy_name/LOGO/$file_name -> $(basename "$canonical_dir")/LOGO/$(basename "$target_path")"
  done

  rmdir "$legacy_logo_dir" 2>/dev/null || true
  rmdir "$legacy_dir" 2>/dev/null || true
done

if command -v chown >/dev/null 2>&1; then
  chown -R 10001:10001 "$IMAGE_ROOT" 2>/dev/null || true
fi
chmod -R u+rwX "$IMAGE_ROOT" 2>/dev/null || true

echo "Hospital image folder normalization finished."
