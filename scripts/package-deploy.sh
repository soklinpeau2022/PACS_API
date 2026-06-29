#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Build UDAYA PACS API deploy bundle

Usage:
  bash ./scripts/package-deploy.sh --target <local|qa|prod> [--skip-tests] [--skip-pentest]

What it creates:
  dist/udaya_pacs_<target>_api.zip

Options:
  --target <target>  Required. local, qa, or prod.
  --skip-tests       Skip API unit/security test gate.
  --skip-pentest     Skip endpoint pentest gate.

Examples:
  bash ./scripts/package-deploy.sh --target qa
  bash ./scripts/package-deploy.sh --target qa --skip-tests --skip-pentest
  bash ./scripts/package-deploy.sh --target prod
EOF
}

TARGET=""
SKIP_TESTS=false
SKIP_PENTEST=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
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
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$TARGET" ]]; then
  echo "--target is required (local|qa|prod)"
  usage
  exit 1
fi

if [[ "$TARGET" != "local" && "$TARGET" != "qa" && "$TARGET" != "prod" ]]; then
  echo "Invalid target: $TARGET (use local|qa|prod)"
  usage
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

resolve_project_path() {
  local value="$1"
  if [[ -z "$value" ]]; then
    return 1
  fi
  case "$value" in
    /*)
      printf '%s' "$value"
      ;;
    *)
      printf '%s/%s' "$PROJECT_ROOT" "${value#./}"
      ;;
  esac
}

copy_dicom_server_base_image_archive() {
  local include_archive configured_path source_path bundle_path
  include_archive="$(env_file_value "$ENV_FILE_SOURCE" PACS_DICOM_SERVER_PACKAGE_INCLUDE_BASE_IMAGE)"
  configured_path="$(env_file_value "$ENV_FILE_SOURCE" PACS_DICOM_SERVER_BASE_IMAGE_ARCHIVE_HOST_PATH)"
  configured_path="${configured_path:-./dicom-server-images/dicom_server_base.tar}"
  source_path="$(resolve_project_path "$configured_path")"

  if [[ -f "$source_path" ]]; then
    bundle_path="$BUNDLE_DIR/dicom-server-images/dicom_server_base.tar"
    mkdir -p "$(dirname "$bundle_path")"
    cp "$source_path" "$bundle_path"
    set_env_value "$BUNDLE_DIR/$ENV_FILE_NAME" "PACS_DICOM_SERVER_BASE_IMAGE_ARCHIVE_HOST_PATH" "./dicom-server-images/dicom_server_base.tar"
    set_env_value "$BUNDLE_DIR/$ENV_FILE_NAME" "PACS_DICOM_SERVER_BASE_IMAGE_ARCHIVE_CONTAINER_PATH" "/app/dicom-server-images/dicom_server_base.tar"
    set_env_value "$BUNDLE_DIR/$ENV_FILE_NAME" "PACS_DICOM_SERVER_PACKAGE_BASE_IMAGE_ARCHIVE_PATH" "/app/dicom-server-images/dicom_server_base.tar"
    echo "Included DICOM server offline base image: $bundle_path"
    return 0
  fi

  if [[ "${include_archive,,}" == "true" ]]; then
    echo "Warning: DICOM server base image embed is enabled, but archive was not found: $source_path" >&2
    echo "Put dicom_server_base.tar at $configured_path before packaging if DICOM zips must include offline image." >&2
  fi
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
IMAGE_HOST_PATH="$(env_file_value "$ENV_FILE_SOURCE" HOSPITAL_IMAGE_HOST_PATH)"
IMAGE_HOST_PATH="${IMAGE_HOST_PATH:-./runtime-image}"
IMAGE_ROOT_PATH="$(env_file_value "$ENV_FILE_SOURCE" HOSPITAL_IMAGE_ROOT_PATH)"
IMAGE_ROOT_PATH="${IMAGE_ROOT_PATH:-/home/Images}"

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
copy_dicom_server_base_image_archive

cat > "$BUNDLE_DIR/README-DEPLOY.txt" <<EOF
# UDAYA_PACS_API Deploy Bundle

This bundle runs UDAYA_PACS_API with Docker. It does not include PostgreSQL.
Run PostgreSQL on the DB server or point $ENV_FILE_NAME to an existing PostgreSQL host.

## 1) Prepare Ubuntu Folders
\`\`\`bash
sudo mkdir -p /var/www/$BUNDLE_NAME "$IMAGE_HOST_PATH" /var/ut-key
sudo chown -R 10001:10001 "$IMAGE_HOST_PATH"
sudo chmod -R u+rwX "$IMAGE_HOST_PATH"
\`\`\`

## 2) Review Env

Open $ENV_FILE_NAME and confirm:

\`\`\`env
KEY_PATH=/var/ut-key
HOSPITAL_IMAGE_HOST_PATH=$IMAGE_HOST_PATH
HOSPITAL_IMAGE_ROOT_PATH=$IMAGE_ROOT_PATH
SECURITY_JWT_PRIVATE_KEY=file:/app/config/key/private_key.pem
SECURITY_JWT_PUBLIC_KEY=file:/app/config/key/public_key.pem
PACS_RESULT_STATIC_AUTH_ENABLED=true
PACS_DICOM_SERVER_PACKAGE_INCLUDE_BASE_IMAGE=$(env_file_value "$ENV_FILE_SOURCE" PACS_DICOM_SERVER_PACKAGE_INCLUDE_BASE_IMAGE)
PACS_DICOM_SERVER_BASE_IMAGE_ARCHIVE_HOST_PATH=./dicom-server-images/dicom_server_base.tar
\`\`\`

scripts/stack.sh auto-corrects bad key paths, auto-creates
/var/ut-key/private_key.pem and /var/ut-key/public_key.pem if missing, and sets owner 10001:10001 on Linux.
/var/ut-key is the normal QA/PROD key path. For disposable smoke tests or custom installs only,
run with DEPLOY_KEY_PATH=/some/linux/path.

Required QA values:

\`\`\`env
QA_API_PORT=8080
QA_SPRING_DATASOURCE_URL=jdbc:postgresql://utdatabase.lan:5432/emr_pacs_db
QA_SPRING_DATASOURCE_USERNAME=pacs_app_qa
QA_SPRING_DATASOURCE_PASSWORD=<db password>
QA_API_AUTH_URL=http://utpac.lan:8080/pacsApi
QA_SPRINGDOC_SERVER_URL=http://utpac.lan:8080/pacsApi
API_HOST_ALIASES=
CORS_ALLOWED_ORIGINS=http://utpac.lan:4173,http://utpac.lan:3005
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
sudo bash ./scripts/normalize-hospital-image-folders.sh "$IMAGE_HOST_PATH"
sudo find "$IMAGE_HOST_PATH" -maxdepth 3 -type d | sort
\`\`\`

Expected structure:

\`\`\`text
$IMAGE_HOST_PATH/H001_UDAYA_HOSPITAL/LOGO
$IMAGE_HOST_PATH/H001_UDAYA_HOSPITAL/CT_COMPUTED_TOMOGRAPHY
$IMAGE_HOST_PATH/H001_UDAYA_HOSPITAL/MR_MAGNETIC_RESONANCE_IMAGING
\`\`\`

## 5) Deploy
\`\`\`bash
sudo bash ./scripts/stack.sh $TARGET deploy --no-build
sudo bash ./scripts/stack.sh $TARGET health
\`\`\`

## 6) Prod LAN DNS

\`\`\`bash
getent ahostsv4 apiapp.utemr.lan
getent ahostsv4 frontend.utemr.lan
getent ahostsv4 dicomviewer.utemr.lan
\`\`\`

## 7) Health Check
\`\`\`bash
curl -fsS $HEALTH_URL
sudo bash ./scripts/stack.sh $TARGET logs
\`\`\`

## 8) Stop
\`\`\`bash
sudo bash ./scripts/stack.sh $TARGET down
\`\`\`
EOF

create_zip "$DIST_ROOT" "$BUNDLE_NAME" "$ZIP_PATH"

echo "Deploy bundle ready:"
echo "$ZIP_PATH"
