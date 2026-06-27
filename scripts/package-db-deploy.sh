#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Build UDAYA PACS PostgreSQL deploy bundle

Usage:
  bash ./scripts/package-db-deploy.sh [--target <local|qa|prod>] [--with-image|--no-image] [--no-data] [--source-container <name>] [--source-database <db>] [--source-user <user>]

What it creates:
  dist/udaya_pacs_<target>_db.zip

Options:
  --target <target>          Target env. Default: qa.
  --with-image               Include postgres:18 image tar. Default.
  --no-image                 Do not include postgres image tar.
  --no-data                  Do not export local DB data into init/pacs-db.dump.
  --source-container <name>  Source DB container for data export.
  --source-database <db>     Source DB name for data export.
  --source-user <user>       Source DB user for data export.

Examples:
  bash ./scripts/package-db-deploy.sh --target qa --no-data
  bash ./scripts/package-db-deploy.sh --target prod --no-data --no-image
  bash ./scripts/package-db-deploy.sh --target local
EOF
}

WITH_IMAGE=true
NO_DATA=false
TARGET="qa"
SOURCE_CONTAINER=""
SOURCE_DATABASE=""
SOURCE_USER=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --with-image)
      WITH_IMAGE=true
      shift
      ;;
    --no-image)
      WITH_IMAGE=false
      shift
      ;;
    --no-data)
      NO_DATA=true
      shift
      ;;
    --target)
      TARGET="${2:-qa}"
      shift 2
      ;;
    --source-container)
      SOURCE_CONTAINER="${2:-}"
      shift 2
      ;;
    --source-database)
      SOURCE_DATABASE="${2:-}"
      shift 2
      ;;
    --source-user)
      SOURCE_USER="${2:-}"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      usage
      exit 1
      ;;
  esac
done

if [[ "$TARGET" != "local" && "$TARGET" != "qa" && "$TARGET" != "prod" ]]; then
  echo "Invalid target: $TARGET (use local|qa|prod)"
  usage
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

dotenv_value() {
  local file="$1"
  local name="$2"
  [[ -f "$file" ]] || return 0
  grep -E "^${name}=" "$file" | head -n 1 | sed "s|^${name}=||" | xargs || true
}

SOURCE_CONTAINER="${SOURCE_CONTAINER:-$(dotenv_value "$PROJECT_ROOT/.env.db" PACS_DB_CONTAINER_NAME)}"
SOURCE_DATABASE="${SOURCE_DATABASE:-$(dotenv_value "$PROJECT_ROOT/.env.db" PACS_DB_NAME)}"
SOURCE_USER="${SOURCE_USER:-$(dotenv_value "$PROJECT_ROOT/.env.db" PACS_DB_USER)}"
SOURCE_CONTAINER="${SOURCE_CONTAINER:-pacs-db}"
SOURCE_DATABASE="${SOURCE_DATABASE:-emr_pacs_db}"
SOURCE_USER="${SOURCE_USER:-pacs_app_local_rw}"

DIST_ROOT="$PROJECT_ROOT/dist"
BUNDLE_NAME="udaya_pacs_${TARGET}_db"
BUNDLE_DIR="$DIST_ROOT/$BUNDLE_NAME"
ZIP_PATH="$DIST_ROOT/$BUNDLE_NAME.zip"
POSTGRES_IMAGE="postgres:18"
POSTGRES_TAR="postgres-18.tar"
DUMP_NAME="pacs-db.dump"
CONTAINER_DUMP_PATH="/tmp/$DUMP_NAME"

rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR/init" "$BUNDLE_DIR/scripts"

cp "$PROJECT_ROOT/docker-compose.db.yml" "$BUNDLE_DIR/docker-compose.yml"
perl -0pi -e "s|^  udaya_pacs_db:|  ${BUNDLE_NAME}:|mg" "$BUNDLE_DIR/docker-compose.yml"
cp "$PROJECT_ROOT/.env.db.example" "$BUNDLE_DIR/.env.db.example"
set_env_value() {
  local file="$1"
  local key="$2"
  local value="$3"
  local tmp_file
  tmp_file="$(mktemp)"
  awk -v k="$key" -v v="$value" '
    BEGIN { updated = 0 }
    $0 ~ "^" k "=" {
      print k "=" v
      updated = 1
      next
    }
    { print }
    END {
      if (updated == 0) {
        print k "=" v
      }
    }
  ' "$file" > "$tmp_file"
  mv "$tmp_file" "$file"
}

target_env_path() {
  local target_name="$1"
  local candidates=()
  case "$target_name" in
    local) candidates=(".env.local" ".env") ;;
    qa) candidates=(".env.qa") ;;
    prod) candidates=(".env.prod") ;;
  esac

  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -f "$PROJECT_ROOT/$candidate" ]]; then
      echo "$PROJECT_ROOT/$candidate"
      return 0
    fi
  done
  return 1
}

apply_target_database_env() {
  local target_name="$1"
  local file="$2"
  local env_path
  env_path="$(target_env_path "$target_name" 2>/dev/null || true)"
  [[ -n "$env_path" ]] || return 0

  local target_upper
  target_upper="$(printf '%s' "$target_name" | tr '[:lower:]' '[:upper:]')"

  if [[ "$target_name" == "local" ]]; then
    local local_name local_user local_password local_port
    local_name="$(dotenv_value "$env_path" LOCAL_DB_NAME)"
    local_user="$(dotenv_value "$env_path" LOCAL_DB_USER)"
    local_password="$(dotenv_value "$env_path" LOCAL_DB_PASSWORD)"
    local_port="$(dotenv_value "$env_path" LOCAL_DB_PORT)"
    [[ -n "$local_name" ]] && set_env_value "$file" PACS_DB_NAME "$local_name"
    [[ -n "$local_user" ]] && set_env_value "$file" PACS_DB_USER "$local_user"
    [[ -n "$local_password" ]] && set_env_value "$file" PACS_DB_PASSWORD "$local_password"
    [[ -n "$local_port" ]] && set_env_value "$file" PACS_DB_PORT "$local_port"
    set_env_value "$file" PACS_DB_BIND_ADDRESS "127.0.0.1"
    return 0
  fi

  local url username password bind_address
  url="$(dotenv_value "$env_path" "${target_upper}_SPRING_DATASOURCE_URL")"
  username="$(dotenv_value "$env_path" "${target_upper}_SPRING_DATASOURCE_USERNAME")"
  password="$(dotenv_value "$env_path" "${target_upper}_SPRING_DATASOURCE_PASSWORD")"
  bind_address="$(dotenv_value "$env_path" "${target_upper}_DB_BIND_ADDRESS")"

  if [[ "$url" =~ ^jdbc:postgresql://([^/:?]+)(:([0-9]+))?/([^?]+) ]]; then
    [[ -z "$bind_address" ]] && bind_address="${BASH_REMATCH[1]}"
    set_env_value "$file" PACS_DB_BIND_ADDRESS "$bind_address"
    set_env_value "$file" PACS_DB_PORT "${BASH_REMATCH[3]:-5432}"
    set_env_value "$file" PACS_DB_NAME "${BASH_REMATCH[4]}"
  fi
  [[ -n "$username" ]] && set_env_value "$file" PACS_DB_USER "$username"
  [[ -n "$password" ]] && set_env_value "$file" PACS_DB_PASSWORD "$password"
}

create_zip() {
  local source_parent="$1"
  local bundle_name="$2"
  local zip_path="$3"

  rm -f "$zip_path"
  if command -v zip >/dev/null 2>&1; then
    (cd "$source_parent" && zip -rq "$zip_path" "$bundle_name")
    return
  fi

  local python_bin=""
  if command -v python3 >/dev/null 2>&1; then
    python_bin="python3"
  elif command -v python >/dev/null 2>&1; then
    python_bin="python"
  fi
  if [[ -n "$python_bin" ]]; then
    "$python_bin" - "$source_parent" "$bundle_name" "$zip_path" <<'PY'
import os
import sys
import zipfile

source_parent, bundle_name, zip_path = sys.argv[1:4]
bundle_root = os.path.join(source_parent, bundle_name)
with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    for root, _, files in os.walk(bundle_root):
        for file_name in files:
            full_path = os.path.join(root, file_name)
            archive.write(full_path, os.path.relpath(full_path, source_parent))
PY
    return
  fi

  echo "zip command or Python is required to create $zip_path." >&2
  exit 1
}

set_env_value "$BUNDLE_DIR/.env.db.example" PACS_DB_CONTAINER_NAME "$BUNDLE_NAME"
set_env_value "$BUNDLE_DIR/.env.db.example" PACS_DB_NETWORK_NAME "${BUNDLE_NAME}-network"
set_env_value "$BUNDLE_DIR/.env.db.example" PACS_DB_COMPOSE_PROJECT_NAME "$BUNDLE_NAME"

cp "$BUNDLE_DIR/.env.db.example" "$BUNDLE_DIR/.env.db"
for file in "$BUNDLE_DIR/.env.db.example" "$BUNDLE_DIR/.env.db"; do
  set_env_value "$file" PACS_DB_CONTAINER_NAME "$BUNDLE_NAME"
  set_env_value "$file" PACS_DB_NETWORK_NAME "${BUNDLE_NAME}-network"
  set_env_value "$file" PACS_DB_COMPOSE_PROJECT_NAME "$BUNDLE_NAME"
done
apply_target_database_env "$TARGET" "$BUNDLE_DIR/.env.db"

if [[ "$NO_DATA" == "false" ]]; then
  if [[ "$(docker inspect -f '{{.State.Running}}' "$SOURCE_CONTAINER" 2>/dev/null || true)" != "true" ]]; then
    echo "Source DB container '$SOURCE_CONTAINER' is not running. Start it first, or use --no-data for an empty DB package."
    exit 1
  fi

  echo "Exporting DB from container '$SOURCE_CONTAINER', database '$SOURCE_DATABASE'..."
  docker exec "$SOURCE_CONTAINER" pg_dump \
    -U "$SOURCE_USER" \
    -d "$SOURCE_DATABASE" \
    --format=custom \
    --blobs \
    --no-owner \
    --no-acl \
    -f "$CONTAINER_DUMP_PATH"
  docker cp "$SOURCE_CONTAINER:$CONTAINER_DUMP_PATH" "$BUNDLE_DIR/init/$DUMP_NAME"
  docker exec "$SOURCE_CONTAINER" rm -f "$CONTAINER_DUMP_PATH" >/dev/null

  cat > "$BUNDLE_DIR/backup-info.txt" <<EOF
PACS DB backup
CreatedAt=$(date +"%Y-%m-%d %H:%M:%S %z")
SourceContainer=$SOURCE_CONTAINER
SourceDatabase=$SOURCE_DATABASE
SourceUser=$SOURCE_USER
Format=pg_dump custom format
Includes=schema,data,indexes,constraints,sequences,functions,views
EOF
fi

cat > "$BUNDLE_DIR/init/01-restore-pacs-db.sh" <<'EOF'
#!/usr/bin/env bash
set -e

DUMP_FILE="/docker-entrypoint-initdb.d/pacs-db.dump"

if [ -f "$DUMP_FILE" ]; then
  echo "Restoring PACS database dump into ${POSTGRES_DB}..."
  pg_restore \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --no-owner \
    --no-acl \
    "$DUMP_FILE"
  echo "PACS database restore completed."
else
  echo "No PACS database dump found. Starting with an empty database."
fi
EOF

cat > "$BUNDLE_DIR/scripts/import-db.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-__DB_CONTAINER_NAME__}"
DB_NAME="${DB_NAME:-emr_pacs_db}"
DB_USER="${DB_USER:-pacs_app_local_rw}"
DUMP_PATH="${DUMP_PATH:-./init/pacs-db.dump}"

if [[ ! -f "$DUMP_PATH" ]]; then
  echo "Dump file not found: $DUMP_PATH"
  exit 1
fi

docker cp "$DUMP_PATH" "$CONTAINER_NAME:/tmp/pacs-db.dump"
docker exec "$CONTAINER_NAME" pg_restore \
  --username "$DB_USER" \
  --dbname "$DB_NAME" \
  --clean \
  --if-exists \
  --no-owner \
  --no-acl \
  /tmp/pacs-db.dump
docker exec "$CONTAINER_NAME" rm -f /tmp/pacs-db.dump >/dev/null
echo "DB import completed."
EOF
perl -0pi -e "s|__DB_CONTAINER_NAME__|$BUNDLE_NAME|g" "$BUNDLE_DIR/scripts/import-db.sh"

cat > "$BUNDLE_DIR/scripts/reset-db.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env.db}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi

echo "Resetting DB container and volume. Existing DB data will be removed."
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" down -v --remove-orphans
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d
echo "DB reset completed."
EOF

cat > "$BUNDLE_DIR/scripts/sync-db-credentials.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env.db}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi

env_value() {
  local key="$1"
  awk -F= -v wanted="$key" '$1 == wanted { sub(/^[^=]*=/, ""); sub(/\r$/, ""); print; exit }' "$ENV_FILE"
}

sql_literal() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/''/g")"
}

CONTAINER_NAME="$(env_value PACS_DB_CONTAINER_NAME)"
DB_NAME="$(env_value PACS_DB_NAME)"
DB_USER="$(env_value PACS_DB_USER)"
DB_PASSWORD="$(env_value PACS_DB_PASSWORD)"
CONTAINER_NAME="${CONTAINER_NAME:-__DB_CONTAINER_NAME__}"

if [[ -z "$DB_NAME" || -z "$DB_USER" || -z "$DB_PASSWORD" ]]; then
  echo "PACS_DB_NAME, PACS_DB_USER, and PACS_DB_PASSWORD are required in $ENV_FILE" >&2
  exit 1
fi

if ! docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  echo "DB container not found: $CONTAINER_NAME" >&2
  exit 1
fi

ADMIN_USER=""
for candidate in "$DB_USER" postgres; do
  if docker exec "$CONTAINER_NAME" psql -U "$candidate" -d postgres -v ON_ERROR_STOP=1 -c "select 1" >/dev/null 2>&1; then
    ADMIN_USER="$candidate"
    break
  fi
done

if [[ -z "$ADMIN_USER" ]]; then
  echo "Cannot connect to PostgreSQL inside $CONTAINER_NAME. Make sure the DB container is running." >&2
  exit 1
fi

DB_LITERAL="$(sql_literal "$DB_NAME")"
USER_LITERAL="$(sql_literal "$DB_USER")"
PASSWORD_LITERAL="$(sql_literal "$DB_PASSWORD")"

docker exec -i "$CONTAINER_NAME" psql -U "$ADMIN_USER" -d postgres -v ON_ERROR_STOP=1 <<SQL
DO \$\$
DECLARE
  role_name text := ${USER_LITERAL};
  role_password text := ${PASSWORD_LITERAL};
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
    EXECUTE format('ALTER ROLE %I WITH LOGIN SUPERUSER PASSWORD %L', role_name, role_password);
  ELSE
    EXECUTE format('CREATE ROLE %I WITH LOGIN SUPERUSER PASSWORD %L', role_name, role_password);
  END IF;
END
\$\$;
SELECT format('CREATE DATABASE %I OWNER %I', ${DB_LITERAL}, ${USER_LITERAL})
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = ${DB_LITERAL})
\gexec
SQL

if ! docker exec -e "PGPASSWORD=$DB_PASSWORD" "$CONTAINER_NAME" psql -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -c "select 1" >/dev/null 2>&1; then
  echo "DB credential sync finished, but password login still failed." >&2
  exit 1
fi

echo "DB credentials OK for $DB_USER on $DB_NAME."
EOF
perl -0pi -e "s|__DB_CONTAINER_NAME__|$BUNDLE_NAME|g" "$BUNDLE_DIR/scripts/sync-db-credentials.sh"

cat > "$BUNDLE_DIR/scripts/deploy-db.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${ENV_FILE:-.env.db}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi
if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Compose file not found: $COMPOSE_FILE" >&2
  exit 1
fi

sed -i 's/\r$//' "$ENV_FILE" "$COMPOSE_FILE" ./scripts/*.sh 2>/dev/null || true

if [[ -f "./postgres-18.tar" ]]; then
  docker load -i ./postgres-18.tar
fi

env_value() {
  local key="$1"
  awk -F= -v wanted="$key" '$1 == wanted { sub(/^[^=]*=/, ""); sub(/\r$/, ""); print; exit }' "$ENV_FILE"
}

set_env_value() {
  local key="$1"
  local value="$2"
  local tmp_file
  tmp_file="$(mktemp)"
  awk -v k="$key" -v v="$value" '
    BEGIN { updated = 0 }
    $0 ~ "^" k "=" {
      print k "=" v
      updated = 1
      next
    }
    { print }
    END {
      if (updated == 0) {
        print k "=" v
      }
    }
  ' "$ENV_FILE" > "$tmp_file"
  mv "$tmp_file" "$ENV_FILE"
}

primary_ipv4() {
  local ip
  if command -v ip >/dev/null 2>&1; then
    ip="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{ for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit } }')"
    if [[ -n "$ip" && "$ip" != 127.* && "$ip" != 169.254.* ]]; then
      printf '%s' "$ip"
      return
    fi
  fi
  if command -v hostname >/dev/null 2>&1; then
    ip="$(hostname -I 2>/dev/null | tr ' ' '\n' | awk '!/^127\./ && !/^169\.254\./ && NF { print; exit }')"
    if [[ -n "$ip" ]]; then
      printf '%s' "$ip"
      return
    fi
  fi
  printf '127.0.0.1'
}

db_bind_address="$(env_value PACS_DB_BIND_ADDRESS | tr '[:upper:]' '[:lower:]')"
if [[ "$db_bind_address" == "auto" || "$db_bind_address" == "primary" || "$db_bind_address" == "primary-ip" ]]; then
  set_env_value PACS_DB_BIND_ADDRESS "$(primary_ipv4)"
fi

docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d

CONTAINER_NAME="$(env_value PACS_DB_CONTAINER_NAME)"
CONTAINER_NAME="${CONTAINER_NAME:-__DB_CONTAINER_NAME__}"

for attempt in $(seq 1 60); do
  if docker exec "$CONTAINER_NAME" pg_isready >/dev/null 2>&1; then
    break
  fi
  sleep 1
  if [[ "$attempt" == "60" ]]; then
    docker logs --tail=120 "$CONTAINER_NAME" || true
    echo "DB did not become ready: $CONTAINER_NAME" >&2
    exit 1
  fi
done

bash ./scripts/sync-db-credentials.sh
docker ps --filter "name=$CONTAINER_NAME"
echo "DB deploy completed: $CONTAINER_NAME"
EOF
perl -0pi -e "s|__DB_CONTAINER_NAME__|$BUNDLE_NAME|g" "$BUNDLE_DIR/scripts/deploy-db.sh"

if [[ "$WITH_IMAGE" == "true" ]]; then
  echo "Pulling $POSTGRES_IMAGE..."
  docker pull "$POSTGRES_IMAGE"
  echo "Saving $POSTGRES_IMAGE..."
  docker save -o "$BUNDLE_DIR/$POSTGRES_TAR" "$POSTGRES_IMAGE"
fi

if [[ "$WITH_IMAGE" == "true" ]]; then
  LOAD_STEP="## 1. Load image
\`\`\`bash
docker load -i ./$POSTGRES_TAR
\`\`\`"
else
  LOAD_STEP="## 1. Pull image
\`\`\`bash
docker pull $POSTGRES_IMAGE
\`\`\`"
fi

if [[ "$NO_DATA" == "true" ]]; then
  DATA_STEP="This package does not include a local database dump."
else
  DATA_STEP="This package includes init/$DUMP_NAME exported from the local DB. It restores automatically on the first container start with a fresh volume."
fi

cat > "$BUNDLE_DIR/README-DB-DEPLOY.md" <<EOF
# UDAYA_PACS_DB Deploy

Target: $TARGET
Package: $BUNDLE_NAME

This package is DB only. It creates PostgreSQL database emr_pacs_db.
$DATA_STEP

$LOAD_STEP

## 2. Review env
\`\`\`bash
nano .env.db
\`\`\`

For local-only DB testing, keep PACS_DB_BIND_ADDRESS=127.0.0.1.
For a separate API server, set PACS_DB_BIND_ADDRESS to the DB server private IP and protect port 5432 with firewall rules.

## 3. Start DB, auto-import, and sync credentials
\`\`\`bash
bash ./scripts/deploy-db.sh
\`\`\`

Auto-import runs only when the Postgres volume is new/empty. Credential sync runs every deploy so an existing volume with an old password is repaired without deleting data.

## 4. Sync DB credentials without deleting data
PostgreSQL applies POSTGRES_USER and POSTGRES_PASSWORD only when the volume is empty. Run this manually only if you edited .env.db after deploy:

\`\`\`bash
bash ./scripts/sync-db-credentials.sh
\`\`\`

## 5. Reset DB credentials / fresh DB
Use this only when you want a clean database because it removes the DB volume:

\`\`\`bash
bash ./scripts/reset-db.sh
\`\`\`

## 6. Existing volume import
If the DB container already has data and you need to overwrite it from the bundled dump:

\`\`\`bash
bash ./scripts/import-db.sh
\`\`\`

## 7. Check health
\`\`\`bash
docker inspect $BUNDLE_NAME --format '{{.State.Health.Status}}'
\`\`\`
EOF

chmod +x "$BUNDLE_DIR/init/01-restore-pacs-db.sh" "$BUNDLE_DIR/scripts/deploy-db.sh" "$BUNDLE_DIR/scripts/import-db.sh" "$BUNDLE_DIR/scripts/reset-db.sh" "$BUNDLE_DIR/scripts/sync-db-credentials.sh"

create_zip "$DIST_ROOT" "$BUNDLE_NAME" "$ZIP_PATH"

echo "DB deploy zip ready:"
echo "$ZIP_PATH"
