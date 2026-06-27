#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  cat <<'EOF'
Backup PACS PostgreSQL database

Usage:
  bash ./scripts/db-backup.sh [local|qa|prod] [--db-container NAME] [--backup-dir PATH]

Example:
  bash ./scripts/db-backup.sh qa --db-container udaya_pacs_qa_db
EOF
  exit 0
fi
exec bash "$SCRIPT_DIR/db-admin.sh" backup "${1:-local}" "${@:2}"
