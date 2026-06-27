#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
UDAYA PACS database admin helper

Usage:
  bash ./scripts/db-admin.sh <action> <target> [--build|--no-build] [--db-container NAME] [--backup-dir PATH]

Actions:
  backup                 Create a pg_dump custom-format backup.
  migrate                Backup first, then run API stack deploy with migrations.
  validate               Run DB validation SQL.
  refresh-cache          Refresh PACS week cache, then validate DB.
  partition-maintenance  Run partition maintenance, then validate DB.
  explain                Run performance explain SQL.

Targets:
  local | qa | prod

Options:
  --build              Build image when migrate calls stack deploy.
  --no-build           Use existing image when migrate calls stack deploy.
  --db-container NAME  Override PostgreSQL Docker container.
  --backup-dir PATH    Override backup output folder.

Examples:
  bash ./scripts/db-admin.sh backup local
  bash ./scripts/db-admin.sh migrate qa --no-build
  bash ./scripts/db-admin.sh validate prod --db-container udaya_pacs_prod_db
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

ACTION="${1:-}"
TARGET="${2:-local}"
shift $(( $# >= 1 ? 1 : 0 ))
shift $(( $# >= 1 ? 1 : 0 ))

BUILD=false
NO_BUILD=false
DB_CONTAINER="${PACS_DB_CONTAINER:-}"
BACKUP_DIR=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    --build) BUILD=true ;;
    --no-build) NO_BUILD=true ;;
    --db-container) shift; DB_CONTAINER="${1:-}" ;;
    --backup-dir) shift; BACKUP_DIR="${1:-}" ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
  shift
done

case "$ACTION" in
  backup|migrate|validate|refresh-cache|partition-maintenance|explain) ;;
  *)
    usage >&2
    exit 1
    ;;
esac

if [[ "$TARGET" != "local" && "$TARGET" != "qa" && "$TARGET" != "prod" ]]; then
  echo "Target must be local, qa, or prod." >&2
  usage >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

env_file_value() {
  local file="$1" key="$2"
  [[ -f "$file" ]] || return 0
  awk -F= -v wanted="$key" '$1 == wanted { sub(/^[^=]*=/, ""); sub(/\r$/, ""); print; exit }' "$file"
}

if [[ -z "$DB_CONTAINER" ]]; then
  if [[ "$TARGET" == "local" && "$(docker inspect -f '{{.State.Running}}' pacs-db 2>/dev/null || true)" == "true" ]]; then
    DB_CONTAINER="pacs-db"
  fi
fi
if [[ -z "$DB_CONTAINER" ]]; then
  DB_CONTAINER="$(env_file_value .env.db PACS_DB_CONTAINER_NAME)"
fi
if [[ -z "$DB_CONTAINER" ]]; then
  if [[ "$TARGET" == "local" ]]; then
    DB_CONTAINER="pacs-db"
  else
    DB_CONTAINER="udaya_pacs_db"
  fi
fi

if [[ "$(docker inspect -f '{{.State.Running}}' "$DB_CONTAINER" 2>/dev/null || true)" != "true" ]]; then
  echo "PostgreSQL container '$DB_CONTAINER' is not running." >&2
  exit 1
fi

DB_USER="$(docker exec "$DB_CONTAINER" sh -lc 'printf %s "$POSTGRES_USER"')"
DB_NAME="$(docker exec "$DB_CONTAINER" sh -lc 'printf %s "$POSTGRES_DB"')"
if [[ ! "$DB_USER" =~ ^[A-Za-z_][A-Za-z0-9_]*$ || ! "$DB_NAME" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
  echo "PostgreSQL container returned an unsafe database user or name." >&2
  exit 1
fi

BACKUP_DIR="${BACKUP_DIR:-$PROJECT_ROOT/backups}"

backup_database() {
  local stamp file_name host_path container_path
  mkdir -p "$BACKUP_DIR"
  stamp="$(date +%Y%m%d_%H%M%S)"
  file_name="emr_pacs_backup_${TARGET}_${stamp}.dump"
  host_path="$BACKUP_DIR/$file_name"
  container_path="/tmp/$file_name"
  docker exec "$DB_CONTAINER" pg_dump \
    -U "$DB_USER" -d "$DB_NAME" \
    --format=custom --blobs --no-owner --no-acl \
    --file "$container_path"
  docker cp "$DB_CONTAINER:$container_path" "$host_path"
  docker exec "$DB_CONTAINER" rm -f "$container_path" >/dev/null
  echo "Database backup: $host_path"
}

run_sql_file() {
  local path="$1"
  docker exec -i "$DB_CONTAINER" psql \
    -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 < "$path"
}

case "$ACTION" in
  backup)
    backup_database
    ;;
  validate)
    run_sql_file db/validation/validate_pacs_db.sql
    ;;
  refresh-cache)
    run_sql_file db/scripts/refresh_week_cache.sql
    run_sql_file db/validation/validate_pacs_db.sql
    ;;
  partition-maintenance)
    run_sql_file db/scripts/run_partition_maintenance.sql
    run_sql_file db/validation/validate_pacs_db.sql
    ;;
  explain)
    run_sql_file db/validation/explain_pacs_performance.sql
    ;;
  migrate)
    backup_database
    stack_args=("$TARGET" deploy)
    [[ "$BUILD" == "true" ]] && stack_args+=(--build)
    [[ "$NO_BUILD" == "true" ]] && stack_args+=(--no-build)
    bash "$SCRIPT_DIR/stack.sh" "${stack_args[@]}"
    run_sql_file db/validation/validate_pacs_db.sql
    ;;
esac
