#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  cat <<'EOF'
Run PACS partition maintenance and validate DB

Usage:
  bash ./scripts/db-partition-maintenance.sh [local|qa|prod] [--db-container NAME]

Example:
  bash ./scripts/db-partition-maintenance.sh qa --db-container udaya_pacs_qa_db
EOF
  exit 0
fi
exec bash "$SCRIPT_DIR/db-admin.sh" partition-maintenance "${1:-local}" "${@:2}"
