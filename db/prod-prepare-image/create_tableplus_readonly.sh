#!/usr/bin/env bash
set -e

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  cat <<'EOF'
Create or update TablePlus read-only audit user

Usage:
  bash ./create_tableplus_readonly.sh

Required env from PostgreSQL entrypoint:
  POSTGRES_USER
  POSTGRES_DB

Optional env:
  PACS_TABLEPLUS_AUDIT_PASSWORD=<readonly-password>
EOF
  exit 0
fi

audit_password="${PACS_TABLEPLUS_AUDIT_PASSWORD:-PacsAudit_2026_ReadOnly}"

case "$audit_password" in
  *"'"*)
    echo "PACS_TABLEPLUS_AUDIT_PASSWORD must not contain single quotes." >&2
    exit 1
    ;;
esac

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'pacs_tableplus_audit') THEN
    CREATE ROLE pacs_tableplus_audit LOGIN PASSWORD '${audit_password}';
  ELSE
    ALTER ROLE pacs_tableplus_audit LOGIN PASSWORD '${audit_password}';
  END IF;
END
\$\$;

GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO pacs_tableplus_audit;
GRANT USAGE ON SCHEMA public TO pacs_tableplus_audit;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO pacs_tableplus_audit;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO pacs_tableplus_audit;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO pacs_tableplus_audit;
ALTER ROLE pacs_tableplus_audit SET default_transaction_read_only = on;
EOSQL
