#!/usr/bin/env bash
set -euo pipefail

TARGET=""
SKIP_TESTS=false
SKIP_PENTEST=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target)
      TARGET="${2:-}"
      shift 2
      ;;
    --skip-tests)
      SKIP_TESTS=true
      shift
      ;;
    --skip-pentest)
      SKIP_PENTEST=true
      shift
      ;;
    *)
      echo "Unknown option: $1"
      echo "Usage: bash scripts/package-deploy.sh --target <local|qa|prod> [--skip-tests] [--skip-pentest]"
      exit 1
      ;;
  esac
done

if [[ -z "$TARGET" ]]; then
  echo "--target is required (local|qa|prod)"
  exit 1
fi

if [[ "$TARGET" != "local" && "$TARGET" != "qa" && "$TARGET" != "prod" ]]; then
  echo "Invalid target: $TARGET (use local|qa|prod)"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

PROJECT_VERSION="$(awk '
  /<parent>/ { in_parent=1 }
  /<\/parent>/ { in_parent=0; next }
  !in_parent && match($0, /<version>[^<]+<\/version>/) {
    value=substr($0, RSTART+9, RLENGTH-19)
    gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
    print value
    exit
  }
' "$PROJECT_ROOT/pom.xml")"

if [[ -z "${PROJECT_VERSION:-}" ]]; then
  echo "Failed to resolve project version from pom.xml"
  exit 1
fi

DIST_ROOT="$PROJECT_ROOT/dist"
BUNDLE_NAME="udaya_pacs_${TARGET}_api"
BUNDLE_DIR="$DIST_ROOT/$BUNDLE_NAME"
IMAGE_NAME="udaya_pacs_${TARGET}_api:latest"
IMAGE_TAR="udaya_pacs_${TARGET}_api.tar"
ZIP_PATH="$DIST_ROOT/$BUNDLE_NAME.zip"
ENV_FILE_SOURCE=""
ENV_FILE_NAME=""

copy_shell_script() {
  local source_path="$1"
  local target_path="$2"
  cp "$source_path" "$target_path"
  perl -0pi -e 's/\r\n?/\n/g' "$target_path"
  chmod +x "$target_path"
}

set_env_value() {
  local file_path="$1"
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
  ' "$file_path" > "$tmp_file"
  mv "$tmp_file" "$file_path"
}

env_file_value() {
  local file_path="$1"
  local key="$2"
  awk -F= -v wanted="$key" '
    $0 ~ /^[[:space:]]*#/ || $0 ~ /^[[:space:]]*$/ { next }
    $1 == wanted {
      sub(/^[^=]*=/, "")
      sub(/\r$/, "")
      print
      exit
    }
  ' "$file_path"
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

  if command -v powershell.exe >/dev/null 2>&1; then
    local bundle_win zip_win
    bundle_win="$(cygpath -w "$source_parent/$bundle_name")"
    zip_win="$(cygpath -w "$zip_path")"
    powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "\$ProgressPreference='SilentlyContinue'; Compress-Archive -Path '${bundle_win}\\*' -DestinationPath '${zip_win}' -Force"
    return
  fi

  echo "zip command is required to create $zip_path" >&2
  exit 1
}

case "$TARGET" in
  qa)
    ENV_FILE_SOURCE="$PROJECT_ROOT/.env.qa"
    ENV_FILE_NAME=".env.qa"
    ;;
  prod)
    ENV_FILE_SOURCE="$PROJECT_ROOT/.env.prod"
    ENV_FILE_NAME=".env.prod"
    ;;
  local)
    if [[ -f "$PROJECT_ROOT/.env.local" ]]; then
      ENV_FILE_SOURCE="$PROJECT_ROOT/.env.local"
      ENV_FILE_NAME=".env.local"
    else
      ENV_FILE_SOURCE="$PROJECT_ROOT/.env"
      ENV_FILE_NAME=".env"
    fi
    ;;
esac

if [[ ! -f "$ENV_FILE_SOURCE" ]]; then
  echo "Required env file not found for target '$TARGET': $ENV_FILE_SOURCE"
  echo "Create it with real values before packaging."
  exit 1
fi

TARGET_UPPER="$(printf '%s' "$TARGET" | tr '[:lower:]' '[:upper:]')"
API_BASE_URL="$(env_file_value "$ENV_FILE_SOURCE" "${TARGET_UPPER}_API_AUTH_URL")"
if [[ -z "$API_BASE_URL" ]]; then
  API_BASE_URL="$(env_file_value "$ENV_FILE_SOURCE" API_AUTH_URL)"
fi
if [[ -z "$API_BASE_URL" ]]; then
  API_BASE_URL="http://localhost:8080/pacsApi"
fi
HEALTH_URL="${API_BASE_URL%/}/actuator/health"

rm -rf "$BUNDLE_DIR"
mkdir -p "$BUNDLE_DIR/artifact" "$BUNDLE_DIR/scripts"

if [[ "$SKIP_PENTEST" == "false" ]]; then
  bash "$PROJECT_ROOT/scripts/test-gate.sh" --target "$TARGET" --tag "$PROJECT_VERSION" --context "package-deploy"
fi

echo "Building JAR..."
if [[ "$SKIP_TESTS" == "true" ]]; then
  ./mvnw -q clean package -DskipTests
else
  ./mvnw -q clean package
fi

JAR_PATH="$(find "$PROJECT_ROOT/target" -maxdepth 1 -type f -name "*.jar" ! -name "*sources*" ! -name "*javadoc*" | head -n 1 || true)"
if [[ -z "$JAR_PATH" ]]; then
  echo "No JAR produced in target/"
  exit 1
fi
cp "$JAR_PATH" "$BUNDLE_DIR/artifact/app.jar"

echo "Building Docker image $IMAGE_NAME..."
docker build -t "$IMAGE_NAME" .

echo "Exporting Docker image..."
docker save -o "$BUNDLE_DIR/$IMAGE_TAR" "$IMAGE_NAME"

cp "$PROJECT_ROOT/docker-compose.runtime.yml" "$BUNDLE_DIR/docker-compose.yml"
cp "$PROJECT_ROOT/docker-compose.db.yml" "$BUNDLE_DIR/docker-compose.db.yml"
cp "$PROJECT_ROOT/.env.db.example" "$BUNDLE_DIR/.env.db.example"
cp "$ENV_FILE_SOURCE" "$BUNDLE_DIR/$ENV_FILE_NAME"
set_env_value "$BUNDLE_DIR/$ENV_FILE_NAME" "APP_IMAGE" "$IMAGE_NAME"
CONTAINER_KEY="${TARGET_UPPER}_API_CONTAINER_NAME"
set_env_value "$BUNDLE_DIR/$ENV_FILE_NAME" "$CONTAINER_KEY" "$BUNDLE_NAME"
set_env_value "$BUNDLE_DIR/$ENV_FILE_NAME" "API_COMPOSE_PROJECT_NAME" "$BUNDLE_NAME"
copy_shell_script "$PROJECT_ROOT/scripts/stack.sh" "$BUNDLE_DIR/scripts/stack.sh"
copy_shell_script "$PROJECT_ROOT/scripts/normalize-hospital-image-folders.sh" "$BUNDLE_DIR/scripts/normalize-hospital-image-folders.sh"
cp "$PROJECT_ROOT/scripts/stack.ps1" "$BUNDLE_DIR/scripts/stack.ps1"

cat > "$BUNDLE_DIR/README-DEPLOY.txt" <<EOF
# UDAYA_PACS_API Deploy Bundle

This bundle runs UDAYA_PACS_API with Docker. It does not include PostgreSQL.
Run PostgreSQL on the DB server or point $ENV_FILE_NAME to an existing PostgreSQL host.

## 1) Prepare Ubuntu Folders
\`\`\`bash
sudo mkdir -p /var/www/$BUNDLE_NAME /var/ut-image /var/ut-key
\`\`\`

## 2) Review Env

Open $ENV_FILE_NAME and confirm:

\`\`\`env
KEY_PATH=/var/ut-key
HOSPITAL_IMAGE_HOST_PATH=/var/ut-image
HOSPITAL_IMAGE_ROOT_PATH=/var/ut-image
SECURITY_JWT_PRIVATE_KEY=file:/app/config/key/private_key.pem
SECURITY_JWT_PUBLIC_KEY=file:/app/config/key/public_key.pem
PACS_RESULT_STATIC_AUTH_ENABLED=true
\`\`\`

scripts/stack.sh auto-corrects bad key paths, auto-creates
/var/ut-key/private_key.pem and /var/ut-key/public_key.pem if missing, and sets owner 10001:10001 on Linux.
/var/ut-key is the normal QA/PROD key path. For disposable smoke tests or custom installs only,
run with DEPLOY_KEY_PATH=/some/linux/path.

Required QA values:

\`\`\`env
QA_API_PORT=8080
QA_SPRING_DATASOURCE_URL=jdbc:postgresql://DB_SERVER_IP:5432/emr_pacs_db
QA_SPRING_DATASOURCE_USERNAME=pacs_app_qa
QA_SPRING_DATASOURCE_PASSWORD=<db password>
QA_API_AUTH_URL=http://API_SERVER_IP:8080/pacsApi
QA_SPRINGDOC_SERVER_URL=http://API_SERVER_IP:8080/pacsApi
CORS_ALLOWED_ORIGINS=http://FRONTEND_SERVER_IP:4173,http://VIEWER_SERVER_IP:3005
\`\`\`

If QA_SECURITY_JWT_REFRESH_TOKEN_ENCRYPTION_KEY, SECURITY_JWT_KEY_ID, or PACS_RESULT_API_KEY
is empty or placeholder, the deploy script generates it and writes it into $ENV_FILE_NAME.

If Viewer is deployed, Viewer .env.viewer must use the same PACS_RESULT_API_KEY.

## 3) Load Docker Image
\`\`\`bash
sudo docker load -i ./$IMAGE_TAR
\`\`\`

## 4) Normalize Hospital Image Folders
Deploy runs this automatically. You can also run it manually after copying old files:

\`\`\`bash
sudo bash ./scripts/normalize-hospital-image-folders.sh /var/ut-image
sudo find /var/ut-image -maxdepth 3 -type d | sort
\`\`\`

Expected structure:

\`\`\`text
/var/ut-image/H001_UDAYA_HOSPITAL/LOGO
/var/ut-image/H001_UDAYA_HOSPITAL/CT_COMPUTED_TOMOGRAPHY
/var/ut-image/H001_UDAYA_HOSPITAL/MR_MAGNETIC_RESONANCE_IMAGING
\`\`\`

## 5) Deploy
\`\`\`bash
sudo bash ./scripts/stack.sh $TARGET deploy --no-build
sudo bash ./scripts/stack.sh $TARGET health
\`\`\`

## 6) Health Check
\`\`\`bash
curl -fsS $HEALTH_URL
sudo bash ./scripts/stack.sh $TARGET logs
\`\`\`

## 7) Stop
\`\`\`bash
sudo bash ./scripts/stack.sh $TARGET down
\`\`\`
EOF

create_zip "$DIST_ROOT" "$BUNDLE_NAME" "$ZIP_PATH"

echo "Deploy bundle ready:"
echo "$ZIP_PATH"
