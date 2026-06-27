#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  cat <<'EOF'
Backup DB, then run PACS API migration deploy

Usage:
  bash ./scripts/db-migrate.sh [local|qa|prod] [--build|--no-build] [--db-container NAME] [--backup-dir PATH]

Examples:
  bash ./scripts/db-migrate.sh local --build
  bash ./scripts/db-migrate.sh qa --no-build
EOF
  exit 0
fi
exec bash "$SCRIPT_DIR/db-admin.sh" migrate "${1:-local}" "${@:2}"
