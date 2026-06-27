#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Run API endpoint/security test gate

Usage:
  bash ./scripts/test-gate.sh --target <local|qa|prod> [--tag <tag>] [--context <name>]

Options:
  --target <target>  Required. local, qa, or prod.
  --tag <tag>        Build/image tag shown in notifications. Default: latest.
  --context <name>   Context label shown in notifications. Default: build.

Examples:
  bash ./scripts/test-gate.sh --target qa
  bash ./scripts/test-gate.sh --target prod --tag release-2026-06-25 --context package
EOF
}

TARGET=""
TAG="latest"
CONTEXT="build"

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
    --tag)
      TAG="${2:-}"
      shift 2
      ;;
    --context)
      CONTEXT="${2:-}"
      shift 2
      ;;
    *)
      echo "Unknown option: $1"
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$TARGET" || ! "$TARGET" =~ ^(local|qa|prod)$ ]]; then
  echo "Invalid --target (use local|qa|prod)"
  usage
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

read_env_value() {
  local file_path="$1"
  local key="$2"
  [[ -f "$file_path" ]] || { echo ""; return; }
  local line
  line="$(grep -E "^${key}=" "$file_path" | head -n 1 || true)"
  [[ -n "$line" ]] || { echo ""; return; }
  echo "${line#*=}"
}

resolve_telegram_creds() {
  local env_file=""
  case "$TARGET" in
    qa) env_file=".env.qa" ;;
    prod) env_file=".env.prod" ;;
    local)
      if [[ -f ".env.local" ]]; then env_file=".env.local"; else env_file=".env"; fi
      ;;
  esac

  local token=""
  local chat_id=""
  case "$TARGET" in
    qa)
      token="$(read_env_value "$env_file" "QA_TELEGRAM_API_TOKEN")"
      chat_id="$(read_env_value "$env_file" "QA_TELEGRAM_CHAT_ID")"
      ;;
    prod)
      token="$(read_env_value "$env_file" "PROD_TELEGRAM_API_TOKEN")"
      chat_id="$(read_env_value "$env_file" "PROD_TELEGRAM_CHAT_ID")"
      ;;
    local)
      token="$(read_env_value "$env_file" "LOCAL_TELEGRAM_API_TOKEN")"
      chat_id="$(read_env_value "$env_file" "LOCAL_TELEGRAM_CHAT_ID")"
      ;;
  esac

  echo "$token|$chat_id"
}

send_telegram_alert() {
  local status="$1"
  local details="$2"
  local creds
  creds="$(resolve_telegram_creds)"
  local token="${creds%%|*}"
  local chat_id="${creds##*|}"

  if [[ -z "${token// }" || -z "${chat_id// }" ]]; then
    return
  fi

  local msg
  msg="<b>UDAYA_PACS_API Gate ${status}</b>%0A<b>Target:</b> ${TARGET}%0A<b>Tag:</b> ${TAG}%0A<b>Context:</b> ${CONTEXT}%0A<b>Details:</b> ${details}"
  curl -fsS -X POST "https://api.telegram.org/bot${token}/sendMessage" \
    -d "chat_id=${chat_id}" \
    -d "parse_mode=HTML" \
    --data-urlencode "text=${msg}" >/dev/null || true
}

TESTS="EndpointPentestSmokeTest,SecurityThreatDetectionFilterTest,SecurityRateLimitFilterTest,GlobalRequestSizeLimitFilterTest,DicomUploadServiceImplTest,AuthServiceRefreshTokenTest,UserMapperXmlHardeningTest,RefreshTokenCryptoServiceTest,EndpointContractCoverageTest,ApiConstantsCoverageTest,MigrationSafetyPolicyTest,GlobalExceptionHandlerTest,SystemErrorAlertServiceTest,SecurityIncidentReporterTest,MyBatisSqlInjectionGuardInterceptorTest,ModulePermissionFilterTest,ActiveHospitalFilterTest,RevokedTokenFilterTest,RequestPayloadGuardTest,SqlSanitizerHelperTest"

echo "Running endpoint/security gate tests..."
if ./mvnw -q clean "-Dtest=${TESTS}" test; then
  send_telegram_alert "PASSED" "Endpoint+security test gate passed"
  echo "Gate passed."
else
  send_telegram_alert "FAILED" "Endpoint+security test gate failed. Build blocked."
  echo "Gate failed. Build blocked." >&2
  exit 1
fi
