#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
UDAYA PACS API stack

Usage:
  bash ./scripts/stack.sh <target> <action> [--build|--no-build]

Targets:
  local | qa | prod

Actions:
  deploy                    Build or load API image, start tmp, health-check, promote.
  up                        Start API container directly.
  down                      Stop/remove API and Redis containers.
  restart                   Restart API container.
  health                    Check API and Redis health.
  logs                      Show API logs.
  ps                        Show API/Redis container status.
  db-backup                 Backup PostgreSQL through db-admin.sh.
  db-migrate                Backup DB, then run Flyway migrations.
  db-validate               Run DB validation SQL.
  db-refresh-cache          Refresh PACS week cache and validate DB.
  db-partition-maintenance  Run partition maintenance and validate DB.

Options:
  --build     Build image before deploy.
  --no-build  Use existing loaded image/bundle.

Examples:
  bash ./scripts/stack.sh qa deploy --no-build
  bash ./scripts/stack.sh qa health
  bash ./scripts/stack.sh local deploy --build
  bash ./scripts/stack.sh local db-migrate --build
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

TARGET="${1:-}"
ACTION="${2:-up}"
BUILD=false
NO_BUILD=false

shift $(( $# >= 1 ? 1 : 0 ))
shift $(( $# >= 1 ? 1 : 0 ))

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build)
      BUILD=true
      shift
      ;;
    --no-build)
      NO_BUILD=true
      shift
      ;;
    *)
      echo "Unknown option: $1"
      usage
      exit 1
      ;;
  esac
done

if [[ "$TARGET" != "local" && "$TARGET" != "qa" && "$TARGET" != "prod" ]]; then
  echo "Target is required: local, qa, or prod"
  usage
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

case "$TARGET" in
  local)
    ENV_FILE=".env.local"
    [[ -f "$ENV_FILE" ]] || ENV_FILE=".env"
    PORT_KEY="LOCAL_API_PORT"
    CONTAINER_KEY="LOCAL_API_CONTAINER_NAME"
    DEFAULT_PORT="8080"
    ;;
  qa)
    ENV_FILE=".env.qa"
    PORT_KEY="QA_API_PORT"
    CONTAINER_KEY="QA_API_CONTAINER_NAME"
    DEFAULT_PORT="8080"
    ;;
  prod)
    ENV_FILE=".env.prod"
    PORT_KEY="PROD_API_PORT"
    CONTAINER_KEY="PROD_API_CONTAINER_NAME"
    DEFAULT_PORT="8080"
    ;;
esac

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE"
  exit 1
fi

env_value() {
  local key="$1"
  awk -F= -v wanted="$key" '$1 == wanted { sub(/^[^=]*=/, ""); sub(/\r$/, ""); print; exit }' "$ENV_FILE"
}

env_value_or_default() {
  local key="$1"
  local default_value="$2"
  local value
  value="$(env_value "$key")"
  if [[ -z "$value" ]]; then
    printf '%s' "$default_value"
    return
  fi
  printf '%s' "$value"
}

config_value() {
  local key="$1"
  if [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ && -n "${!key:-}" ]]; then
    printf '%s' "${!key}"
    return
  fi
  env_value "$key"
}

docker_cmd() {
  MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' docker "$@"
}

require_docker_daemon() {
  if ! command -v docker >/dev/null 2>&1; then
    cat >&2 <<'EOF'
Docker is required to deploy and run the UDAYA PACS API container, but docker was not found.
Install Docker Engine on this server:
  sudo apt-get update
  sudo apt-get install -y ca-certificates curl gnupg
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  sudo chmod a+r /etc/apt/keyrings/docker.gpg
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo apt-get update
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  sudo systemctl enable --now docker
EOF
    exit 1
  fi

  if ! docker info >/dev/null 2>&1; then
    cat >&2 <<'EOF'
Docker is installed, but the Docker daemon is not running or /var/run/docker.sock is unavailable.
Start Docker on this server:
  sudo systemctl enable --now docker
  sudo systemctl status docker --no-pager
EOF
    exit 1
  fi
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

is_ipv4_address() {
  [[ "${1:-}" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]
}

resolve_ipv4_for_host() {
  local host="$1"
  local ip
  if is_ipv4_address "$host"; then
    printf '%s' "$host"
    return 0
  fi
  if command -v getent >/dev/null 2>&1; then
    ip="$(getent ahostsv4 "$host" 2>/dev/null | awk '$1 !~ /^(0|127|169\.254)\./ { print $1; exit }')"
    if [[ -n "$ip" ]]; then
      printf '%s' "$ip"
      return 0
    fi
  fi
  if command -v host >/dev/null 2>&1; then
    ip="$(host "$host" 2>/dev/null | awk '/has address/ && $NF !~ /^(0|127|169\.254)\./ { print $NF; exit }')"
    if [[ -n "$ip" ]]; then
      printf '%s' "$ip"
      return 0
    fi
  fi
  return 1
}

url_host() {
  local url="$1"
  local authority host
  url="${url#*://}"
  authority="${url%%/*}"
  authority="${authority%@*}"
  if [[ "$authority" == \[*\]* ]]; then
    host="${authority%%]*}"
    host="${host#[}"
  else
    host="${authority%%:*}"
  fi
  printf '%s' "$host"
}

api_public_hostname() {
  local value
  value="$(env_value "${TARGET^^}_API_PUBLIC_HOSTNAME")"
  value="${value:-$(env_value API_PUBLIC_HOSTNAME)}"
  if [[ -z "$value" ]]; then
    value="$(env_value "${TARGET^^}_API_AUTH_URL")"
    value="${value:-$(env_value API_AUTH_URL)}"
  fi
  value="$(url_host "$value")"
  printf '%s' "$value"
}

preferred_auto_bind_host() {
  local configured_host resolved_ip
  configured_host="$(api_public_hostname)"
  if [[ -n "$configured_host" && ! "$configured_host" =~ ^(localhost|127\.) ]]; then
    resolved_ip="$(resolve_ipv4_for_host "$configured_host" || true)"
    if [[ -n "$resolved_ip" ]]; then
      printf '%s' "$resolved_ip"
      return 0
    fi
  fi
  primary_ipv4
}

resolve_bind_host() {
  local value="${1:-}"
  value="$(printf '%s' "$value" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
  local lower
  lower="$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')"
  if [[ -z "$value" || "$lower" == "auto" ]]; then
    if [[ "$TARGET" == "local" ]]; then
      printf '127.0.0.1'
    else
      preferred_auto_bind_host
    fi
    return
  fi
  case "$lower" in
    \*|::|\[::\])
      if [[ "$TARGET" == "local" ]]; then
        printf '127.0.0.1'
      else
        preferred_auto_bind_host
      fi
      ;;
    *)
      printf '%s' "$value"
      ;;
  esac
}

health_host_for_bind() {
  local value="${1:-127.0.0.1}"
  local lower
  lower="$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')"
  if [[ "$lower" == "localhost" || "$lower" == "*" || "$lower" == "::" || "$lower" == "[::]" ]]; then
    printf '127.0.0.1'
  else
    printf '%s' "$value"
  fi
}

telegram_config_value() {
  local key value
  for key in "$@"; do
    value="$(config_value "$key")"
    if [[ -n "$value" ]]; then
      printf '%s' "$value"
      return
    fi
  done
}

telegram_enabled() {
  local enabled
  enabled="$(telegram_config_value DEPLOY_TELEGRAM_ENABLED TELEGRAM_DEPLOY_ENABLED "${TARGET^^}_TELEGRAM_DEPLOY_ENABLED")"
  enabled="$(printf '%s' "$enabled" | tr '[:upper:]' '[:lower:]')"
  [[ "$enabled" == "false" || "$enabled" == "0" || "$enabled" == "no" ]] && return 1
  [[ -n "$(telegram_bot_token)" && -n "$(telegram_chat_id)" ]]
}

telegram_bot_token() {
  telegram_config_value DEPLOY_TELEGRAM_API_TOKEN "${TARGET^^}_TELEGRAM_API_TOKEN"
}

telegram_chat_id() {
  telegram_config_value DEPLOY_TELEGRAM_CHAT_ID "${TARGET^^}_TELEGRAM_CHAT_ID"
}

sanitize_text() {
  sed -E 's/((TOKEN|PASSWORD|SECRET|API_KEY|PRIVATE_KEY|PUBLIC_KEY|telegram\.api\.token)[A-Za-z0-9_.-]*[=:])[[:space:]]*[^[:space:]]+/\1***REDACTED***/Ig'
}

json_string() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\r'/}"
  value="${value//$'\n'/\\n}"
  printf '"%s"' "$value"
}

write_deploy_report() {
  local status="$1"
  local summary="$2"
  local line="${3:-}"
  local report_container="${4:-${deploy_report_container_name:-${service_name:-}}}"
  local report_path logs ps_output docker_df uptime_output disk_output now host
  report_path="$(mktemp "/tmp/udaya_pacs_api_${TARGET}_${status}_deploy_XXXXXX.json")"
  now="$(date -Iseconds 2>/dev/null || date)"
  host="$(hostname 2>/dev/null || echo unknown)"
  logs="$(docker logs --tail 160 "$report_container" 2>&1 || true)"
  logs="$(printf '%s' "$logs" | sanitize_text)"
  ps_output="$(docker ps -a --filter "name=${report_container}" --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' 2>&1 || true)"
  ps_output="$(printf '%s' "$ps_output" | sanitize_text)"
  docker_df="$(docker system df 2>&1 || true)"
  uptime_output="$(uptime 2>&1 || true)"
  disk_output="$(df -h . 2>&1 || true)"
  cat > "$report_path" <<JSON
{
  "system": "UDAYA_PACS",
  "app": "UDAYA_PACS_API",
  "target": $(json_string "$TARGET"),
  "action": $(json_string "$ACTION"),
  "status": $(json_string "$status"),
  "summary": $(json_string "$summary"),
  "line": $(json_string "$line"),
  "datetime": $(json_string "$now"),
  "hostname": $(json_string "$host"),
  "container": $(json_string "${report_container:-unknown}"),
  "image": $(json_string "${image_name:-unknown}"),
  "healthUrl": $(json_string "$(public_api_health_url)"),
  "dockerPs": $(json_string "$ps_output"),
  "dockerSystemDf": $(json_string "$docker_df"),
  "uptime": $(json_string "$uptime_output"),
  "disk": $(json_string "$disk_output"),
  "deployError": $(json_string "${deploy_report_extra:-}"),
  "containerLogsTail": $(json_string "$logs")
}
JSON
  printf '%s' "$report_path"
}

send_telegram_message() {
  local text="$1"
  local token chat
  telegram_enabled || return 0
  token="$(telegram_bot_token)"
  chat="$(telegram_chat_id)"
  curl -fsS --max-time 20 -X POST "https://api.telegram.org/bot${token}/sendMessage" \
    -F "chat_id=${chat}" \
    -F "text=${text}" >/dev/null 2>&1 || echo "Telegram deploy message failed." >&2
}

send_telegram_document() {
  local file_path="$1"
  local caption="$2"
  local token chat
  telegram_enabled || return 0
  [[ -f "$file_path" ]] || return 0
  token="$(telegram_bot_token)"
  chat="$(telegram_chat_id)"
  curl -fsS --max-time 30 -X POST "https://api.telegram.org/bot${token}/sendDocument" \
    -F "chat_id=${chat}" \
    -F "caption=${caption}" \
    -F "document=@${file_path};type=application/json" >/dev/null 2>&1 || echo "Telegram deploy report failed." >&2
}

notify_deploy() {
  local status="$1"
  local summary="$2"
  local line="${3:-}"
  local report_container="${4:-${deploy_report_container_name:-${service_name:-}}}"
  local report_path message
  [[ "$ACTION" == "deploy" ]] || return 0
  message="UDAYA_PACS_API deploy ${status}
Target: ${TARGET}
Container: ${report_container:-unknown}
Image: ${image_name:-unknown}
Host: $(hostname 2>/dev/null || echo unknown)
Time: $(date -Iseconds 2>/dev/null || date)
Summary: ${summary}"
  send_telegram_message "$message"
  if [[ "$status" != "SUCCESS" ]]; then
    report_path="$(write_deploy_report "$status" "$summary" "$line" "$report_container")"
    send_telegram_document "$report_path" "UDAYA_PACS_API deploy ${status} report (${TARGET})"
    rm -f "$report_path"
  fi
}

deploy_error_notified=false
on_deploy_error() {
  local line="$1"
  local command="$2"
  if [[ "$ACTION" == "deploy" && "$deploy_error_notified" != "true" ]]; then
    deploy_error_notified=true
    notify_deploy "FAILED" "Unexpected deploy error at line ${line}: ${command}" "$line"
  fi
}

trap 'on_deploy_error "$LINENO" "$BASH_COMMAND"' ERR

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

is_windows_shell() {
  case "$(uname -s 2>/dev/null || true)" in
    MINGW*|MSYS*|CYGWIN*) return 0 ;;
    *) return 1 ;;
  esac
}

is_missing_or_placeholder_value() {
  local value="$1"
  local compact="${value//[[:space:]]/}"
  local normalized
  normalized="$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')"
  if [[ -z "$compact" ]]; then
    return 0
  fi
  if [[ "$normalized" =~ example\.com ]]; then
    return 0
  fi
  if [[ "$normalized" =~ ^(change|change_me|change_me_.*)$ ]]; then
    return 0
  fi
  if [[ "$normalized" =~ ^replace_with_.*$ ]]; then
    return 0
  fi
  if [[ "$normalized" =~ (qa-db-host|prod-db-host) ]]; then
    return 0
  fi
  return 1
}

is_invalid_key_path_value() {
  local value="$1"
  if [[ -z "${value// }" ]]; then
    return 0
  fi
  if [[ "$value" =~ ^[A-Za-z]: ]]; then
    return 0
  fi
  if [[ "$value" == *"\\"* ]]; then
    return 0
  fi
  if [[ "$value" =~ src/main/resources/key ]]; then
    return 0
  fi
  if [[ "$value" =~ ^\./ && ! -d "$PROJECT_ROOT/${value#./}" ]]; then
    return 0
  fi
  return 1
}

generate_hex_secret() {
  local bytes="${1:-32}"
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex "$bytes"
  else
    od -An -N "$bytes" -tx1 /dev/urandom | tr -d ' \n'
  fi
}

generate_base64_secret() {
  local bytes="${1:-32}"
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 "$bytes" | tr -d '\r\n'
  else
    od -An -N "$bytes" -tx1 /dev/urandom | tr -d ' \n'
  fi
}

default_key_path() {
  if [[ -n "${DEPLOY_KEY_PATH:-}" ]]; then
    echo "$DEPLOY_KEY_PATH"
    return
  fi
  if [[ "$TARGET" == "local" ]] || is_windows_shell; then
    echo "$PROJECT_ROOT/runtime/key"
  else
    echo "/var/ut-key"
  fi
}

ensure_security_env_values() {
  local refresh_key_name="${TARGET^^}_SECURITY_JWT_REFRESH_TOKEN_ENCRYPTION_KEY"
  local refresh_key_value key_id_value pacs_result_api_key key_path private_uri public_uri

  key_path="$(env_value KEY_PATH)"
  if is_invalid_key_path_value "$key_path"; then
    key_path="$(default_key_path)"
    set_env_value "$ENV_FILE" "KEY_PATH" "$key_path"
    echo "Corrected KEY_PATH in $ENV_FILE"
  fi

  private_uri="$(env_value SECURITY_JWT_PRIVATE_KEY)"
  if [[ "$private_uri" != "file:/app/config/key/private_key.pem" ]]; then
    set_env_value "$ENV_FILE" "SECURITY_JWT_PRIVATE_KEY" "file:/app/config/key/private_key.pem"
    echo "Corrected SECURITY_JWT_PRIVATE_KEY in $ENV_FILE"
  fi

  public_uri="$(env_value SECURITY_JWT_PUBLIC_KEY)"
  if [[ "$public_uri" != "file:/app/config/key/public_key.pem" ]]; then
    set_env_value "$ENV_FILE" "SECURITY_JWT_PUBLIC_KEY" "file:/app/config/key/public_key.pem"
    echo "Corrected SECURITY_JWT_PUBLIC_KEY in $ENV_FILE"
  fi

  refresh_key_value="$(env_value "$refresh_key_name")"
  if is_missing_or_placeholder_value "$refresh_key_value"; then
    refresh_key_value="$(generate_base64_secret 32)"
    set_env_value "$ENV_FILE" "$refresh_key_name" "$refresh_key_value"
    echo "Generated $refresh_key_name in $ENV_FILE"
  fi

  key_id_value="$(env_value SECURITY_JWT_KEY_ID)"
  if is_missing_or_placeholder_value "$key_id_value"; then
    key_id_value="${TARGET}-kid-$(generate_hex_secret 12)"
    set_env_value "$ENV_FILE" "SECURITY_JWT_KEY_ID" "$key_id_value"
    echo "Generated SECURITY_JWT_KEY_ID in $ENV_FILE"
  fi

  pacs_result_api_key="$(env_value PACS_RESULT_API_KEY)"
  if is_missing_or_placeholder_value "$pacs_result_api_key"; then
    pacs_result_api_key="$(generate_hex_secret 32)"
    set_env_value "$ENV_FILE" "PACS_RESULT_API_KEY" "$pacs_result_api_key"
    echo "Generated PACS_RESULT_API_KEY in $ENV_FILE"
    echo "Use the same PACS_RESULT_API_KEY in the Viewer .env.viewer file."
  fi
}

ensure_jwt_key_files() {
  local key_path private_file public_file

  key_path="$(env_value KEY_PATH)"
  key_path="$(resolve_host_path "$key_path" "$(default_key_path)")"
  private_file="$key_path/private_key.pem"
  public_file="$key_path/public_key.pem"

  if ! command -v openssl >/dev/null 2>&1; then
    echo "openssl is required to create JWT key files." >&2
    exit 1
  fi

  mkdir -p "$key_path"
  if [[ ! -s "$private_file" || ! -s "$public_file" ]]; then
    rm -f "$private_file" "$public_file"
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$private_file" >/dev/null 2>&1
    openssl rsa -in "$private_file" -pubout -out "$public_file" >/dev/null 2>&1
    echo "Generated JWT key files in $key_path"
  fi

  chmod 750 "$key_path" 2>/dev/null || true
  chmod 600 "$private_file" 2>/dev/null || true
  chmod 644 "$public_file" 2>/dev/null || true
  if ! is_windows_shell && [[ "$(id -u)" == "0" ]]; then
    chown 10001:10001 "$key_path" "$private_file" "$public_file" 2>/dev/null || true
  fi
}

health_port="$(env_value "$PORT_KEY")"
health_port="${health_port:-$DEFAULT_PORT}"
service_name="$(env_value "$CONTAINER_KEY")"
service_name="${service_name:-udaya_pacs_${TARGET}_api}"
image_name="$(env_value APP_IMAGE)"
image_name="${image_name:-udaya_pacs_${TARGET}_api:latest}"
candidate_port="$(env_value "${TARGET^^}_API_CANDIDATE_PORT")"
candidate_port="${candidate_port:-$(env_value API_CANDIDATE_PORT)}"
if [[ -z "$candidate_port" ]]; then
  if [[ "$health_port" =~ ^[0-9]+$ ]]; then
    candidate_port="$((health_port + 1000))"
  else
    candidate_port="9080"
  fi
fi
api_bind_host="$(env_value "${TARGET^^}_API_BIND_HOST")"
api_bind_host="${api_bind_host:-$(env_value API_BIND_HOST)}"
api_bind_host="$(resolve_bind_host "$api_bind_host")"
api_public_host="$(api_public_hostname)"
api_public_host="${api_public_host:-$(health_host_for_bind "$api_bind_host")}"
redis_container_key="${TARGET^^}_REDIS_CONTAINER_NAME"
redis_password_key="${TARGET^^}_REDIS_PASSWORD"
redis_host_port_key="${TARGET^^}_REDIS_HOST_PORT"
redis_network_key="${TARGET^^}_REDIS_NETWORK_NAME"
redis_container_name="$(env_value "$redis_container_key")"
redis_container_name="${redis_container_name:-udaya_pacs_${TARGET}_redis}"
redis_host_port="$(env_value "$redis_host_port_key")"
if [[ -z "$redis_host_port" ]]; then
  case "$TARGET" in
    local) redis_host_port="6379" ;;
    qa) redis_host_port="6380" ;;
    prod) redis_host_port="6381" ;;
  esac
fi
redis_network_name="$(env_value "$redis_network_key")"
redis_network_name="${redis_network_name:-udaya_pacs_${TARGET}_network}"
redis_image="$(env_value_or_default REDIS_IMAGE redis:7-alpine)"
redis_port="$(env_value_or_default REDIS_PORT 6379)"

image_exists() {
  docker_cmd image inspect "$image_name" >/dev/null 2>&1
}

load_bundle_image() {
  local image_repo image_tar
  image_repo="${image_name%%:*}"

  for image_tar in \
    "$PROJECT_ROOT/${image_repo}.tar" \
    "$PROJECT_ROOT/$(basename "$PROJECT_ROOT").tar"; do
    if [[ -f "$image_tar" ]]; then
      echo "Loading Docker image from $(basename "$image_tar")..."
      docker_cmd load -i "$image_tar"
      if image_exists; then
        return 0
      fi
      echo "Loaded image tar, but expected image is still missing: $image_name" >&2
      return 1
    fi
  done

  if image_exists; then
    return 0
  fi

  echo "Docker image is missing: $image_name" >&2
  echo "Expected bundle image tar in $PROJECT_ROOT, for example ${image_repo}.tar." >&2
  return 1
}

compose() {
  docker_cmd compose --env-file "$ENV_FILE" --profile "$TARGET" -f docker-compose.yml "$@"
}

docker_host_alias_args() {
  local aliases alias name value lower_value datasource_host datasource_ip
  aliases="$(env_value API_HOST_ALIASES)"
  aliases="${aliases//,/ }"
  for alias in $aliases; do
    if [[ -n "$alias" ]]; then
      name="${alias%%:*}"
      value="${alias#*:}"
      lower_value="$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')"
      if [[ "$name" != "$alias" && ( "$lower_value" == "auto" || "$lower_value" == "primary" || "$lower_value" == "primary-ip" ) ]]; then
        alias="${name}:$(primary_ipv4)"
      fi
      printf '%s\0%s\0' "--add-host" "$alias"
    fi
  done

  if [[ "$TARGET" != "local" ]]; then
    datasource_host="$(datasource_host || true)"
    if [[ -n "$datasource_host" ]] && ! is_ipv4_address "$datasource_host" && ! host_alias_is_configured "$datasource_host"; then
      datasource_ip="$(resolve_ipv4_for_host "$datasource_host" || true)"
      if [[ -n "$datasource_ip" ]]; then
        printf '%s\0%s:%s\0' "--add-host" "$datasource_host" "$datasource_ip"
      fi
    fi
  fi
}

resolve_host_alias_for_connection() {
  local host="$1"
  local aliases alias name value
  aliases="$(env_value API_HOST_ALIASES)"
  aliases="${aliases//,/ }"
  for alias in $aliases; do
    name="${alias%%:*}"
    value="${alias#*:}"
    if [[ "$name" == "$host" && "$value" != "$alias" && -n "$value" ]]; then
      case "$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')" in
        auto|primary|primary-ip)
          value="$(primary_ipv4)"
          ;;
      esac
      printf '%s' "$value"
      return 0
    fi
  done
  printf '%s' "$host"
}

host_alias_is_configured() {
  local host="$1"
  local aliases alias name
  aliases="$(env_value API_HOST_ALIASES)"
  aliases="${aliases//,/ }"
  for alias in $aliases; do
    name="${alias%%:*}"
    if [[ "$name" == "$host" ]]; then
      return 0
    fi
  done
  return 1
}

datasource_host() {
  local url endpoint
  url="$(target_env_value SPRING_DATASOURCE_URL SPRING_DATASOURCE_URL)"
  [[ "$url" == jdbc:postgresql://* ]] || return 1
  endpoint="${url#jdbc:postgresql://}"
  endpoint="${endpoint%%/*}"
  endpoint="${endpoint%%,*}"
  if [[ "$endpoint" == \[*\]* ]]; then
    endpoint="${endpoint#\[}"
    endpoint="${endpoint%%\]*}"
  else
    endpoint="${endpoint%%:*}"
  fi
  printf '%s' "$endpoint"
}

endpoint_gate() {
  echo "Running API endpoint/security unit gate..."
  bash scripts/test-gate.sh --target "$TARGET" --tag latest --context stack-build
}

log_local_docker_ps_snapshot() {
  if [[ "$TARGET" != "local" || ( "$ACTION" != "up" && "$ACTION" != "deploy" ) ]]; then
    return 0
  fi
  if [[ "${UDAYA_PACS_DOCKER_PS_LOGGED:-}" == "1" ]]; then
    return 0
  fi

  echo
  echo "Docker containers before local API ${ACTION}:"
  docker ps
  export UDAYA_PACS_DOCKER_PS_LOGGED=1
}

target_env_value() {
  local suffix="$1"
  local fallback_key="${2:-}"
  local value
  value="$(env_value "${TARGET^^}_${suffix}")"
  if [[ -n "$value" ]]; then
    echo "$value"
    return
  fi
  if [[ -n "$fallback_key" ]]; then
    env_value "$fallback_key"
  fi
}

api_base_url() {
  local value
  value="$(target_env_value API_AUTH_URL API_AUTH_URL)"
  if [[ -z "$value" ]]; then
    local base_host
    if [[ "$TARGET" == "local" ]]; then
      base_host="127.0.0.1"
    else
      base_host="$api_public_host"
    fi
    value="http://${base_host}:${health_port}/pacsApi"
  fi
  printf '%s' "${value%/}"
}

public_api_health_url() {
  printf '%s/actuator/health' "$(api_base_url)"
}

validate_target_network_config() {
  local api_url origins blocked_docker_host_alias
  [[ "$TARGET" == "local" ]] && return 0
  api_url="$(api_base_url)"
  origins="$(env_value CORS_ALLOWED_ORIGINS)"
  blocked_docker_host_alias="$(printf '%s' "host"".docker"".internal")"
  if [[ -z "$origins" ]]; then
    echo "CORS_ALLOWED_ORIGINS is required for $TARGET." >&2
    return 1
  fi
  if [[ "$api_url" == *"localhost"* || "$api_url" == *"127.0.0.1"* || "$api_url" == *"$blocked_docker_host_alias"* || "$api_url" == *".example.lan"* ]]; then
    echo "${TARGET^^}_API_AUTH_URL must use the deployed server hostname or DNS name: $api_url" >&2
    return 1
  fi
  if [[ "$origins" == *"localhost"* || "$origins" == *"127.0.0.1"* || "$origins" == *"$blocked_docker_host_alias"* || "$origins" == *".example.lan"* ]]; then
    echo "CORS_ALLOWED_ORIGINS must use deployed server hostnames or DNS names for $TARGET." >&2
    return 1
  fi
}

database_preflight() {
  local url endpoint host port
  [[ "$TARGET" == "local" ]] && return 0
  url="$(target_env_value SPRING_DATASOURCE_URL SPRING_DATASOURCE_URL)"
  if [[ "$url" != jdbc:postgresql://* ]]; then
    echo "${TARGET^^}_SPRING_DATASOURCE_URL must be a jdbc:postgresql:// URL." >&2
    return 1
  fi
  endpoint="${url#jdbc:postgresql://}"
  endpoint="${endpoint%%/*}"
  host="${endpoint%%:*}"
  if [[ "$endpoint" == *:* ]]; then
    port="${endpoint##*:}"
  else
    port="5432"
  fi
  connect_host="$(resolve_host_alias_for_connection "$host")"
  if timeout 5 bash -c ":</dev/tcp/${connect_host}/${port}" 2>/dev/null; then
    echo "Database endpoint reachable: ${host}:${port}"
    return 0
  fi
  echo "Database endpoint unreachable from PACS server: ${host}:${port}" >&2
  return 1
}

database_container_preflight() {
  local url endpoint host port
  local host_alias_args=()
  [[ "$TARGET" == "local" ]] && return 0
  url="$(target_env_value SPRING_DATASOURCE_URL SPRING_DATASOURCE_URL)"
  endpoint="${url#jdbc:postgresql://}"
  endpoint="${endpoint%%/*}"
  host="${endpoint%%:*}"
  if [[ "$endpoint" == *:* ]]; then
    port="${endpoint##*:}"
  else
    port="5432"
  fi
  mapfile -d '' -t host_alias_args < <(docker_host_alias_args)
  if docker_cmd run --rm \
    --network "$redis_network_name" \
    "${host_alias_args[@]}" \
    --entrypoint bash \
    "$image_name" \
    -c "timeout 5 bash -c ':</dev/tcp/${host}/${port}'" >/dev/null 2>&1; then
    echo "Database endpoint reachable from API Docker network: ${host}:${port}"
    return 0
  fi
  echo "Database endpoint unreachable from API Docker network: ${host}:${port}" >&2
  echo "Check routing/firewall from the PACS server Docker bridge to the database server." >&2
  return 1
}

resolve_host_path() {
  local path_value="$1"
  local default_value="$2"
  local value="${path_value:-$default_value}"
  if [[ "$value" == /* ]]; then
    echo "$value"
  else
    if command -v realpath >/dev/null 2>&1; then
      realpath -m "$PROJECT_ROOT/$value"
    else
      (cd "$PROJECT_ROOT" && cd "$(dirname "$value")" && printf '%s/%s\n' "$(pwd)" "$(basename "$value")")
    fi
  fi
}

container_exists() {
  docker_cmd container inspect "$1" >/dev/null 2>&1
}

remove_container_if_exists() {
  local name="$1"
  local attempt
  if container_exists "$name"; then
    docker_cmd rm -f "$name" >/dev/null
    for ((attempt = 1; attempt <= 30; attempt++)); do
      if ! container_exists "$name"; then
        return 0
      fi
      sleep 0.2
    done
    echo "Container still exists after remove: $name" >&2
    return 1
  fi
}

remove_legacy_live_alias_if_needed() {
  for name in udaya_pacs_api_local udaya_pacs_api; do
    if [[ "$name" != "$service_name" ]]; then
      remove_container_if_exists "$name"
    fi
  done
}

ensure_redis_env_values() {
  local key value changed=false
  local defaults=(
    "APP_CACHE_PROVIDER=redis"
    "APP_CACHE_TTL_SECONDS=600"
    "APP_CACHE_MAX_ENTRIES=5000"
    "APP_CACHE_REDIS_KEY_PREFIX=udaya_pacs_${TARGET}"
    "APP_CACHE_REDIS_HEALTH_ENABLED=true"
    "REDIS_IMAGE=redis:7-alpine"
    "REDIS_PORT=6379"
    "REDIS_DATABASE=0"
    "REDIS_TIMEOUT=2s"
    "REDIS_MAXMEMORY=512mb"
    "REDIS_MAXMEMORY_POLICY=allkeys-lru"
    "${redis_container_key}=${redis_container_name}"
    "${redis_host_port_key}=${redis_host_port}"
    "${redis_network_key}=${redis_network_name}"
  )
  for pair in "${defaults[@]}"; do
    key="${pair%%=*}"
    value="${pair#*=}"
    if [[ -z "$(env_value "$key")" ]]; then
      set_env_value "$ENV_FILE" "$key" "$value"
      changed=true
    fi
  done

  value="$(env_value "$redis_password_key")"
  if is_missing_or_placeholder_value "$value"; then
    set_env_value "$ENV_FILE" "$redis_password_key" "$(generate_hex_secret 36)"
    changed=true
    echo "Generated $redis_password_key in $ENV_FILE"
  fi
  if [[ "$changed" == "true" ]]; then
    echo "Redis cache settings are ready in $ENV_FILE"
  fi
}

ensure_docker_network() {
  local name="$1"
  local output status
  if ! docker_cmd network inspect "$name" >/dev/null 2>&1; then
    set +e
    output="$(docker_cmd network create "$name" 2>&1)"
    status=$?
    set -e
    if [[ "$status" -eq 0 ]]; then
      return 0
    fi
    if [[ "$output" == *"already exists"* ]] && docker_cmd network inspect "$name" >/dev/null 2>&1; then
      return 0
    fi
    printf '%s\n' "$output" >&2
    return "$status"
  fi
}

redis_health() {
  local attempts="${1:-30}"
  local delay="${2:-1}"
  local password attempt
  password="$(env_value "$redis_password_key")"
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if docker_cmd exec "$redis_container_name" redis-cli --no-auth-warning -a "$password" ping 2>/dev/null | grep -q PONG; then
      return 0
    fi
    sleep "$delay"
  done
  return 1
}

ensure_redis_container() {
  local password maxmemory maxmemory_policy
  ensure_redis_env_values
  ensure_docker_network "$redis_network_name"
  if container_exists "$redis_container_name" && redis_health 3 1; then
    return 0
  fi
  remove_container_if_exists "$redis_container_name"
  password="$(env_value "$redis_password_key")"
  maxmemory="$(env_value_or_default REDIS_MAXMEMORY 512mb)"
  maxmemory_policy="$(env_value_or_default REDIS_MAXMEMORY_POLICY allkeys-lru)"
  docker_cmd run -d \
    --name "$redis_container_name" \
    --network "$redis_network_name" \
    -p "127.0.0.1:${redis_host_port}:6379" \
    --restart unless-stopped \
    -v "${redis_container_name}_data:/data" \
    "$redis_image" \
    redis-server --requirepass "$password" --appendonly yes --maxmemory "$maxmemory" --maxmemory-policy "$maxmemory_policy" >/dev/null
  if ! redis_health 30 1; then
    echo "Redis health check failed for $redis_container_name" >&2
    return 1
  fi
  echo "Redis OK: $redis_container_name on 127.0.0.1:${redis_host_port}"
}

normalize_hospital_image_folders() {
  local image_path normalizer
  image_path="$(resolve_host_path "$(env_value HOSPITAL_IMAGE_HOST_PATH)" "./runtime-image")"
  mkdir -p "$image_path"

  normalizer="$SCRIPT_DIR/normalize-hospital-image-folders.sh"
  if [[ -f "$normalizer" ]]; then
    bash "$normalizer" "$image_path" || echo "Hospital image folder normalization skipped." >&2
  fi
}

start_api_container() {
  local name="$1"
  local host_port="$2"
  local restart_policy="${3:-no}"
  local public_bind="${4:-false}"
  local image_override="${5:-$image_name}"
  local key_path
  local image_path
  local image_container_path
  local dicom_upload_mount
  local base_image_archive_host_path
  local base_image_archive_container_path
  local port_spec

  remove_container_if_exists "$name"

  key_path="$(resolve_host_path "$(env_value KEY_PATH)" "./src/main/resources/key")"
  image_path="$(resolve_host_path "$(env_value HOSPITAL_IMAGE_HOST_PATH)" "./runtime-image")"
  image_container_path="$(env_value_or_default HOSPITAL_IMAGE_ROOT_PATH /var/ut-image)"
  mkdir -p "$image_path"
  if command -v chown >/dev/null 2>&1; then
    chown -R 10001:10001 "$image_path" 2>/dev/null || true
  fi
  chmod -R u+rwX "$image_path" 2>/dev/null || true
  if [[ "$TARGET" == "local" ]]; then
    local compose_project upload_volume
    compose_project="$(env_value_or_default API_COMPOSE_PROJECT_NAME udaya_pacs_api)"
    upload_volume="${compose_project}_udaya_pacs_local_dicom_upload_temp"
    docker_cmd volume create "$upload_volume" >/dev/null
    docker_cmd run --rm --user 0 \
      -v "${upload_volume}:/var/ut-dicom-upload-temp" \
      --entrypoint sh \
      "$image_override" \
      -c "chown -R 10001:10001 /var/ut-dicom-upload-temp" >/dev/null
    dicom_upload_mount="${upload_volume}:/var/ut-dicom-upload-temp"
  else
    local dicom_upload_temp_path
    dicom_upload_temp_path="$(resolve_host_path "$(env_value PACS_DICOM_UPLOAD_TEMP_HOST_PATH)" "../runtime-dicom-upload-temp")"
    mkdir -p "$dicom_upload_temp_path"
    if command -v chown >/dev/null 2>&1; then
      chown -R 10001:10001 "$dicom_upload_temp_path" 2>/dev/null || true
    fi
    chmod -R u+rwX "$dicom_upload_temp_path" 2>/dev/null || true
    dicom_upload_mount="${dicom_upload_temp_path}:/var/ut-dicom-upload-temp"
  fi
  ensure_docker_network "$redis_network_name"
  if [[ "$public_bind" == "true" ]]; then
    port_spec="${api_bind_host}:${host_port}:8080"
  else
    port_spec="127.0.0.1:${host_port}:8080"
  fi
  base_image_archive_host_path="$(resolve_host_path "$(env_value PACS_DICOM_SERVER_BASE_IMAGE_ARCHIVE_HOST_PATH)" "./dicom-server-images/dicom_server_base.tar")"
  base_image_archive_container_path="$(env_value_or_default PACS_DICOM_SERVER_BASE_IMAGE_ARCHIVE_CONTAINER_PATH /app/dicom-server-images/dicom_server_base.tar)"

  local env_pairs=(
    "SPRING_PROFILES_ACTIVE=$TARGET"
    "APP_CACHE_PROVIDER=$(env_value_or_default APP_CACHE_PROVIDER redis)"
    "APP_CACHE_TTL_SECONDS=$(env_value_or_default APP_CACHE_TTL_SECONDS 600)"
    "APP_CACHE_MAX_ENTRIES=$(env_value_or_default APP_CACHE_MAX_ENTRIES 5000)"
    "APP_CACHE_REDIS_KEY_PREFIX=$(env_value_or_default APP_CACHE_REDIS_KEY_PREFIX "udaya_pacs_${TARGET}")"
    "APP_CACHE_REDIS_HEALTH_ENABLED=$(env_value_or_default APP_CACHE_REDIS_HEALTH_ENABLED true)"
    "REDIS_HOST=$redis_container_name"
    "REDIS_PORT=$redis_port"
    "REDIS_PASSWORD=$(target_env_value REDIS_PASSWORD REDIS_PASSWORD)"
    "REDIS_DATABASE=$(env_value_or_default REDIS_DATABASE 0)"
    "REDIS_TIMEOUT=$(env_value_or_default REDIS_TIMEOUT 2s)"
    "PACS_CACHE_SCHEDULER_ENABLED=$(env_value_or_default PACS_CACHE_SCHEDULER_ENABLED true)"
    "PACS_PARTITION_SCHEDULER_ENABLED=$(env_value_or_default PACS_PARTITION_SCHEDULER_ENABLED true)"
    "SPRING_DATASOURCE_URL=$(target_env_value SPRING_DATASOURCE_URL SPRING_DATASOURCE_URL)"
    "SPRING_DATASOURCE_USERNAME=$(target_env_value SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_USERNAME)"
    "SPRING_DATASOURCE_PASSWORD=$(target_env_value SPRING_DATASOURCE_PASSWORD SPRING_DATASOURCE_PASSWORD)"
    "SECURITY_JWT_REFRESH_TOKEN_ENCRYPTION_KEY=$(target_env_value SECURITY_JWT_REFRESH_TOKEN_ENCRYPTION_KEY SECURITY_JWT_REFRESH_TOKEN_ENCRYPTION_KEY)"
    "SECURITY_JWT_PRIVATE_KEY=$(env_value SECURITY_JWT_PRIVATE_KEY)"
    "SECURITY_JWT_PUBLIC_KEY=$(env_value SECURITY_JWT_PUBLIC_KEY)"
    "SECURITY_JWT_KEY_ID=$(env_value SECURITY_JWT_KEY_ID)"
    "HOSPITAL_IMAGE_ROOT_PATH=$(env_value HOSPITAL_IMAGE_ROOT_PATH)"
    "PACS_DICOM_UPLOAD_TEMP_DIR=$(env_value_or_default PACS_DICOM_UPLOAD_TEMP_DIR /var/ut-dicom-upload-temp)"
    "SPRING_SERVLET_MULTIPART_LOCATION=$(env_value_or_default SPRING_SERVLET_MULTIPART_LOCATION /var/ut-dicom-upload-temp)"
    "PACS_DICOM_UPLOAD_INSTANCE_PARALLELISM=$(env_value_or_default PACS_DICOM_UPLOAD_INSTANCE_PARALLELISM 24)"
    "PACS_DICOM_UPLOAD_MAX_CONCURRENT_PROCESSING_JOBS=$(env_value_or_default PACS_DICOM_UPLOAD_MAX_CONCURRENT_PROCESSING_JOBS 1)"
    "PACS_DICOM_UPLOAD_INSTANCE_MAX_ATTEMPTS=$(env_value_or_default PACS_DICOM_UPLOAD_INSTANCE_MAX_ATTEMPTS 8)"
    "PACS_DICOM_UPLOAD_INSTANCE_RETRY_BACKOFF_MS=$(env_value_or_default PACS_DICOM_UPLOAD_INSTANCE_RETRY_BACKOFF_MS 500)"
    "PACS_DICOM_UPLOAD_IN_MEMORY_ENTRY_MAX_BYTES=$(env_value_or_default PACS_DICOM_UPLOAD_IN_MEMORY_ENTRY_MAX_BYTES 1048576)"
    "DICOM_SERVER_HEALTH_TIMEOUT_MS=$(env_value_or_default DICOM_SERVER_HEALTH_TIMEOUT_MS 5000)"
    "DICOM_SERVER_HEALTH_OFFLINE_FAILURE_THRESHOLD=$(env_value_or_default DICOM_SERVER_HEALTH_OFFLINE_FAILURE_THRESHOLD 3)"
    "DICOM_SERVER_HEALTH_OFFLINE_GRACE_MS=$(env_value_or_default DICOM_SERVER_HEALTH_OFFLINE_GRACE_MS 180000)"
    "PACS_RESULT_STATIC_AUTH_ENABLED=$(env_value PACS_RESULT_STATIC_AUTH_ENABLED)"
    "PACS_RESULT_API_KEY=$(env_value PACS_RESULT_API_KEY)"
    "PACS_RESULT_UPLOAD_ROOT=$(env_value_or_default PACS_RESULT_UPLOAD_ROOT /var/ut-image)"
    "PACS_RESULT_MAX_IMAGE_BYTES=$(env_value_or_default PACS_RESULT_MAX_IMAGE_BYTES 10485760)"
    "PACS_DICOM_SERVER_PACKAGE_INCLUDE_BASE_IMAGE=$(env_value_or_default PACS_DICOM_SERVER_PACKAGE_INCLUDE_BASE_IMAGE false)"
    "PACS_DICOM_SERVER_PACKAGE_BASE_IMAGE_ARCHIVE_PATH=$(env_value_or_default PACS_DICOM_SERVER_PACKAGE_BASE_IMAGE_ARCHIVE_PATH "$base_image_archive_container_path")"
    "PACS_DICOM_SERVER_PACKAGE_MAX_EMBEDDED_BASE_IMAGE_BYTES=$(env_value_or_default PACS_DICOM_SERVER_PACKAGE_MAX_EMBEDDED_BASE_IMAGE_BYTES 67108864)"
    "SPRING_MAIL_USERNAME=$(target_env_value SPRING_MAIL_USERNAME)"
    "SPRING_MAIL_PASSWORD=$(target_env_value SPRING_MAIL_PASSWORD)"
    "API_AUTH_URL=$(target_env_value API_AUTH_URL API_AUTH_URL)"
    "SPRINGDOC_SERVER_URL=$(target_env_value SPRINGDOC_SERVER_URL SPRINGDOC_SERVER_URL)"
    "CORS_ALLOWED_ORIGINS=$(env_value CORS_ALLOWED_ORIGINS)"
    "CORS_ALLOW_CREDENTIALS=$(env_value CORS_ALLOW_CREDENTIALS)"
    "APP_SECURITY_CLIENT_ALLOW_PATHS=$(env_value APP_SECURITY_CLIENT_ALLOW_PATHS)"
    "APP_SECURITY_CLIENT_ALLOW_CLIENT_IDS=$(env_value APP_SECURITY_CLIENT_ALLOW_CLIENT_IDS)"
    "APP_SECURITY_DICOM_UPLOAD_MAX_TRANSPORT_REQUEST_BYTES=$(env_value_or_default APP_SECURITY_DICOM_UPLOAD_MAX_TRANSPORT_REQUEST_BYTES 4362076160)"
    "SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=$(env_value_or_default SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE 4GB)"
    "SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=$(env_value_or_default SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE 4160MB)"
    "TELEGRAM_CHAT_ID=$(target_env_value TELEGRAM_CHAT_ID)"
    "TELEGRAM_API_TOKEN=$(target_env_value TELEGRAM_API_TOKEN)"
    "TZ=$(env_value TZ)"
  )

  local host_alias_args=()
  mapfile -d '' -t host_alias_args < <(docker_host_alias_args)
  local args=(run -d --name "$name" "${host_alias_args[@]}" -p "$port_spec")
  if [[ "$public_bind" == "true" && "$api_bind_host" != "127.0.0.1" && "$api_bind_host" != "localhost" ]]; then
    args+=(-p "127.0.0.1:${host_port}:8080")
  fi
  args+=(--restart "$restart_policy" --init --read-only --cap-drop ALL --security-opt no-new-privileges:true --network "$redis_network_name" --tmpfs /tmp:size=64m,mode=1777 -v "${key_path}:/app/config/key:ro" -v "${image_path}:${image_container_path}" -v "$dicom_upload_mount")
  if [[ -f "$base_image_archive_host_path" ]]; then
    args+=(-v "${base_image_archive_host_path}:${base_image_archive_container_path}:ro")
  fi
  local local_api_docker_ip
  local_api_docker_ip="$(env_value LOCAL_API_DOCKER_IP)"
  if [[ "$TARGET" == "local" && "$public_bind" == "true" && -n "$local_api_docker_ip" ]]; then
    args+=(--ip "$local_api_docker_ip")
  fi
  local pair
  for pair in "${env_pairs[@]}"; do
    if [[ "$pair" != *= ]]; then
      args+=(-e "$pair")
    fi
  done
  args+=("$image_override")
  docker_cmd "${args[@]}" >/dev/null
}

api_health() {
  local attempts="${1:-60}"
  local delay="${2:-1}"
  local url="http://127.0.0.1:${health_port}/pacsApi/actuator/health"
  local attempt
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if curl -4 -fsS "$url" >/dev/null 2>&1; then
      echo "API OK: $url"
      return 0
    fi
    sleep "$delay"
  done
  echo "API health check failed: $url" >&2
  return 1
}

public_api_health() {
  local attempts="${1:-5}"
  local delay="${2:-1}"
  local url
  local attempt
  url="$(public_api_health_url)"
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if curl -4 -fsS "$url" >/dev/null 2>&1; then
      echo "API OK: $url"
      return 0
    fi
    sleep "$delay"
  done
  echo "API health check failed: $url" >&2
  return 1
}

show_container_failure() {
  local name="$1"
  local logs
  echo "Container diagnostics: $name" >&2
  docker_cmd ps -a --filter "name=^/${name}$" \
    --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}' >&2 || true
  echo "Last 200 container log lines:" >&2
  logs="$(docker_cmd logs --tail 200 "$name" 2>&1 || true)"
  printf '%s\n' "$logs" | sanitize_text >&2 || true
  if [[ "$logs" == *"password authentication failed for user"* ]]; then
    cat >&2 <<'EOF'

Detected DB password authentication failure.
On the DB server, run this from the DB deploy folder:
  cd /var/www/udaya_pacs_qa_db
  sudo bash ./scripts/deploy-db.sh

Then redeploy the API:
  cd /var/www/udaya_pacs_qa_api
  sudo bash ./scripts/stack.sh qa deploy --no-build
EOF
  fi
}

case "$ACTION" in
  db-backup|db-migrate|db-validate|db-refresh-cache|db-partition-maintenance)
    ;;
  *)
    require_docker_daemon
    ;;
esac

log_local_docker_ps_snapshot

case "$ACTION" in
  db-backup)
    bash "$SCRIPT_DIR/db-admin.sh" backup "$TARGET"
    ;;
  db-migrate)
    db_args=(migrate "$TARGET")
    [[ "$BUILD" == "true" ]] && db_args+=(--build)
    [[ "$NO_BUILD" == "true" ]] && db_args+=(--no-build)
    bash "$SCRIPT_DIR/db-admin.sh" "${db_args[@]}"
    ;;
  db-validate)
    bash "$SCRIPT_DIR/db-admin.sh" validate "$TARGET"
    ;;
  db-refresh-cache)
    bash "$SCRIPT_DIR/db-admin.sh" refresh-cache "$TARGET"
    ;;
  db-partition-maintenance)
    bash "$SCRIPT_DIR/db-admin.sh" partition-maintenance "$TARGET"
    ;;
  up)
    args=()
    [[ "$BUILD" == "true" ]] && args+=(--build)
    [[ "$NO_BUILD" == "true" ]] && args+=(--no-build)
    "$0" "$TARGET" deploy "${args[@]}"
    ;;
  down)
    remove_container_if_exists "$service_name"
    remove_container_if_exists "${service_name}_tmp"
    remove_container_if_exists "$redis_container_name"
    while IFS= read -r rollback_name; do
      [[ -n "$rollback_name" ]] && remove_container_if_exists "$rollback_name"
    done < <(docker_cmd ps -a --format '{{.Names}}' | grep "^${service_name}_rollback_" || true)
    ;;
  restart)
    "$0" "$TARGET" down
    "$0" "$TARGET" up --no-build
    ;;
  deploy)
    ensure_security_env_values
    ensure_jwt_key_files
    validate_target_network_config
    database_preflight
    ensure_redis_container
    normalize_hospital_image_folders
    tmp_name="${service_name}_tmp"
    if [[ "$BUILD" == "true" && "$NO_BUILD" == "false" ]]; then
      endpoint_gate
      compose build
    else
      load_bundle_image
    fi
    database_container_preflight
    echo "Testing API tmp container $tmp_name on 127.0.0.1:${candidate_port}..."
    start_api_container "$tmp_name" "$candidate_port"
    saved_port="$health_port"
    health_port="$candidate_port"
    if ! api_health; then
      show_container_failure "$tmp_name"
      health_port="$saved_port"
      notify_deploy "FAILED" "Temporary API container failed its health check." "tmp-health" "$tmp_name"
      deploy_error_notified=true
      exit 1
    fi
    health_port="$saved_port"
    echo "Tmp is healthy. Promoting $service_name..."
    remove_legacy_live_alias_if_needed
    backup_name="${service_name}_rollback_$(date +%Y%m%d%H%M%S)"
    had_live=false
    if container_exists "$service_name"; then
      had_live=true
      docker_cmd stop "$service_name" >/dev/null 2>&1 || true
      docker_cmd rename "$service_name" "$backup_name"
    fi

    promotion_error_log="$(mktemp "/tmp/udaya_pacs_api_${TARGET}_promotion_XXXXXX.log")"
    if start_api_container "$service_name" "$health_port" "unless-stopped" "true" "$image_name" 2>"$promotion_error_log" && api_health 60 1; then
      if ! public_api_health 10 1; then
        echo "Warning: API is healthy on 127.0.0.1:${health_port}, but the configured public health URL did not answer yet: $(public_api_health_url)" >&2
      fi
      rm -f "$promotion_error_log"
      remove_container_if_exists "$tmp_name"
      if [[ "$had_live" == "true" ]]; then
        remove_container_if_exists "$backup_name"
      fi
      echo "API deployed: live=${service_name}, tmp removed, url=$(public_api_health_url)"
      notify_deploy "SUCCESS" "API deployed and health check passed."
      exit 0
    fi

    deploy_report_extra="$(cat "$promotion_error_log" 2>/dev/null || true)"
    rm -f "$promotion_error_log"
    show_container_failure "$service_name"
    echo "Promotion failed. Attempting rollback to previous API container." >&2
    notify_deploy "FAILED" "API promotion failed. Rollback was attempted." "promotion" "$service_name"
    deploy_error_notified=true
    remove_container_if_exists "$service_name"
    if [[ "$had_live" == "true" ]]; then
      docker_cmd rename "$backup_name" "$service_name"
      docker_cmd start "$service_name" >/dev/null
      public_api_health 30 1
    fi
    remove_container_if_exists "$tmp_name"
    exit 1
    ;;
  logs)
    docker_cmd logs -f --tail 200 "$service_name"
    ;;
  ps)
    compose ps
    ;;
  health)
    if ! redis_health 5 1; then
      echo "Redis health check failed: $redis_container_name" >&2
      exit 1
    fi
    echo "Redis OK: $redis_container_name"
    public_api_health 5
    ;;
  *)
    echo "Unknown action: $ACTION"
    echo "Usage: bash scripts/stack.sh <local|qa|prod> <up|down|restart|deploy|logs|ps|health|db-backup|db-migrate|db-validate|db-refresh-cache|db-partition-maintenance> [--build|--no-build]"
    exit 1
    ;;
esac
