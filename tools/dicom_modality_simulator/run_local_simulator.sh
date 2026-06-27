#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  cat <<'EOF'
Run local DICOM modality simulator

Usage:
  bash ./run_local_simulator.sh [simulator options]

Common env values:
  SIM_API_BASE_URL=http://utpac.lan:8080/pacsApi
  SIM_USERNAME=<api-user>
  SIM_PASSWORD=<api-password>
  SIM_DICOM_SERVER_USERNAME=<dicom-server-user>
  SIM_DICOM_SERVER_PASSWORD=<dicom-server-password>
  SIM_MODALITY_REGISTRATION_HOST=<this-machine-hostname-or-ip>

Examples:
  export SIM_API_BASE_URL=http://utpac.lan:8080/pacsApi
  export SIM_USERNAME=<api-user>
  export SIM_PASSWORD=<api-password>
  bash ./run_local_simulator.sh

For Python-level options after dependencies are installed:
  .venv/bin/python modality_simulator.py --help
EOF
  exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ -x ".venv/bin/python" ]]; then
  PYTHON_BIN=".venv/bin/python"
elif [[ -x ".venv/Scripts/python.exe" ]]; then
  PYTHON_BIN=".venv/Scripts/python.exe"
else
  python -m venv .venv
  if [[ -x ".venv/bin/python" ]]; then
    PYTHON_BIN=".venv/bin/python"
  else
    PYTHON_BIN=".venv/Scripts/python.exe"
  fi
fi

"$PYTHON_BIN" -m pip install -r requirements.txt
exec "$PYTHON_BIN" modality_simulator.py "$@"
