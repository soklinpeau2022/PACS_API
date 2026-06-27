#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

ENV_FILE="$PROJECT_ROOT/.env.db"
OUTPUT_PATH="$PROJECT_ROOT/dist/emr_pacs_db_schema.sql"
CONTAINER_NAME=""
DB_NAME=""
DB_USER=""
DB_PASSWORD=""
KEEP_TEMP=false

usage() {
  cat <<'EOF'
Usage: bash tools/export_complete_schema.sh [options]

Options:
  --env-file <path>        Env file to read. Default: .env.db
  --output-path <path>     SQL output path. Default: dist/emr_pacs_db_schema.sql
  --container-name <name>  PostgreSQL Docker container name
  --database <name>        Source database name
  --user <name>            Source database user
  --password <password>    Source database password
  --keep-temp              Keep temporary validation database
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="$2"
      shift 2
      ;;
    --output-path)
      OUTPUT_PATH="$2"
      shift 2
      ;;
    --container-name)
      CONTAINER_NAME="$2"
      shift 2
      ;;
    --database)
      DB_NAME="$2"
      shift 2
      ;;
    --user)
      DB_USER="$2"
      shift 2
      ;;
    --password)
      DB_PASSWORD="$2"
      shift 2
      ;;
    --keep-temp)
      KEEP_TEMP=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

env_value() {
  local key="$1"
  [[ -f "$ENV_FILE" ]] || return 0
  awk -F= -v wanted="$key" '
    $0 ~ /^[[:space:]]*#/ || $0 ~ /^[[:space:]]*$/ { next }
    $1 == wanted {
      sub(/^[^=]*=/, "")
      sub(/\r$/, "")
      print
      exit
    }
  ' "$ENV_FILE"
}

CONTAINER_NAME="${CONTAINER_NAME:-$(env_value PACS_DB_CONTAINER_NAME)}"
DB_NAME="${DB_NAME:-$(env_value PACS_DB_NAME)}"
DB_USER="${DB_USER:-$(env_value PACS_DB_USER)}"
DB_PASSWORD="${DB_PASSWORD:-$(env_value PACS_DB_PASSWORD)}"

CONTAINER_NAME="${CONTAINER_NAME:-pacs-db}"
DB_NAME="${DB_NAME:-emr_pacs_db}"
DB_USER="${DB_USER:-pacs_app_local_rw}"

if [[ -z "$DB_PASSWORD" ]]; then
  echo "Database password is required through --password or PACS_DB_PASSWORD in $ENV_FILE." >&2
  exit 1
fi
if ! docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  echo "PostgreSQL container not found: $CONTAINER_NAME" >&2
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT_PATH")"
TMP_DB="${DB_NAME}_schema_check_$(date +%Y%m%d%H%M%S)"
trap 'if [[ "$KEEP_TEMP" == "false" ]]; then docker exec -e "PGPASSWORD=$DB_PASSWORD" "$CONTAINER_NAME" dropdb -h 127.0.0.1 -U "$DB_USER" --if-exists "$TMP_DB" >/dev/null 2>&1 || true; fi' EXIT

echo "Exporting schema from $DB_NAME in $CONTAINER_NAME..."
docker exec -e "PGPASSWORD=$DB_PASSWORD" "$CONTAINER_NAME" pg_dump \
  -h 127.0.0.1 \
  -U "$DB_USER" \
  -d "$DB_NAME" \
  --schema-only \
  --no-owner \
  --no-acl > "$OUTPUT_PATH"

echo "Validating schema restore in temporary database $TMP_DB..."
docker exec -e "PGPASSWORD=$DB_PASSWORD" "$CONTAINER_NAME" createdb \
  -h 127.0.0.1 \
  -U "$DB_USER" \
  "$TMP_DB"
docker exec -i -e "PGPASSWORD=$DB_PASSWORD" "$CONTAINER_NAME" psql \
  -h 127.0.0.1 \
  -U "$DB_USER" \
  -d "$TMP_DB" \
  -v ON_ERROR_STOP=1 < "$OUTPUT_PATH" >/dev/null

source_counts="$(docker exec -e "PGPASSWORD=$DB_PASSWORD" "$CONTAINER_NAME" psql -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME" -Atc "select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in ('r','p','v','m','S','i')")"
restored_counts="$(docker exec -e "PGPASSWORD=$DB_PASSWORD" "$CONTAINER_NAME" psql -h 127.0.0.1 -U "$DB_USER" -d "$TMP_DB" -Atc "select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind in ('r','p','v','m','S','i')")"

if [[ "$source_counts" != "$restored_counts" ]]; then
  echo "Schema validation failed: source object count $source_counts, restored object count $restored_counts." >&2
  exit 1
fi

echo "Schema export OK: $OUTPUT_PATH"
