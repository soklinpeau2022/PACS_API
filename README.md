# UDAYA_PACS_API

Spring Boot API for UDAYA_PACS. The current deploy flow is intentionally small:

- `scripts/package-deploy.sh` builds an API deploy bundle.
- `scripts/package-db-deploy.sh` builds a DB deploy bundle.
- `scripts/stack.sh` starts, health-checks, promotes, stops, and logs containers.
- `scripts/test-gate.sh` is used internally by `package-deploy.sh` unless the skip flag is passed.

Older API-only wrapper scripts were removed. Use `stack` for local, QA, and prod.

## Env Files

Keep only these API env files in the project:

```text
.env.local          local runtime values
.env.qa             QA runtime values
.env.prod           production runtime values
.env.db             local/DB runtime values
.env.local.example  local API template
.env.qa.example     QA API template
.env.prod.example   production API template
.env.db.example     DB template
```

Do not commit real secrets. The `*.example` files are templates only.

Required API key paths:

```env
KEY_PATH=/var/ut-key
SECURITY_JWT_PRIVATE_KEY=file:/app/config/key/private_key.pem
SECURITY_JWT_PUBLIC_KEY=file:/app/config/key/public_key.pem
```

`scripts/stack.sh` auto-corrects bad key env values and creates missing `private_key.pem` / `public_key.pem` during deploy.

## Redis Cache

Redis is enabled for local, QA, and prod through the API stack scripts. The scripts start a target-specific Redis container before the API tmp container, attach both containers to the same Docker network, health-check Redis, then health-check and promote the API.

Runtime keys:

```env
APP_CACHE_PROVIDER=redis
APP_CACHE_TTL_SECONDS=600
APP_CACHE_MAX_ENTRIES=5000
APP_CACHE_REDIS_HEALTH_ENABLED=true
REDIS_IMAGE=redis:7-alpine
REDIS_PORT=6379
REDIS_DATABASE=0
REDIS_TIMEOUT=2s
REDIS_MAXMEMORY=512mb
REDIS_MAXMEMORY_POLICY=allkeys-lru
```

Each target also has its own Redis container, host port, network, and password key:

```env
LOCAL_REDIS_CONTAINER_NAME=udaya_pacs_local_redis
LOCAL_REDIS_HOST_PORT=6379
LOCAL_REDIS_NETWORK_NAME=udaya_pacs_local_network
LOCAL_REDIS_PASSWORD=<strong password>

QA_REDIS_CONTAINER_NAME=udaya_pacs_qa_redis
QA_REDIS_HOST_PORT=6380
QA_REDIS_NETWORK_NAME=udaya_pacs_qa_network
QA_REDIS_PASSWORD=<strong password>

PROD_REDIS_CONTAINER_NAME=udaya_pacs_prod_redis
PROD_REDIS_HOST_PORT=6381
PROD_REDIS_NETWORK_NAME=udaya_pacs_prod_network
PROD_REDIS_PASSWORD=<strong password>
```

If a target password is missing or still a placeholder, `stack.sh` generates a strong password and writes it to the target env file. Do not commit real Redis passwords.

## Deploy Telegram Notifications

`stack.sh` sends Telegram notifications for `deploy` success and failure when these values are set in the runtime env file or server environment:

```env
DEPLOY_TELEGRAM_ENABLED=true
DEPLOY_TELEGRAM_CHAT_ID=
DEPLOY_TELEGRAM_API_TOKEN=
```

Target-specific keys are also supported, for example `QA_TELEGRAM_CHAT_ID` and `QA_TELEGRAM_API_TOKEN`. Failure notifications attach a JSON report with deploy status, host, container, image, Docker status, disk/uptime summary, and recent container logs with sensitive fields redacted.

## Build Deploy Bundle

From the project root with Bash:

```bash
cd PACS_API
bash ./scripts/package-deploy.sh --target qa --skip-tests --skip-pentest
bash ./scripts/package-db-deploy.sh --target qa --no-data
```

macOS/Linux:

```bash
cd /path/to/PACS_API
bash ./scripts/package-deploy.sh --target qa --skip-tests --skip-pentest
bash ./scripts/package-db-deploy.sh --target qa --no-data
```

Outputs:

```text
dist/udaya_pacs_qa_api.zip
dist/udaya_pacs_qa_db.zip
```

## Deploy API On Server

```bash
sudo mkdir -p /var/www/udaya_pacs_qa_api /home/Images /var/ut-key
sudo unzip -o /tmp/udaya_pacs_qa_api.zip -d /var/www/udaya_pacs_qa_api
cd /var/www/udaya_pacs_qa_api

sudo docker load -i ./udaya_pacs_qa_api.tar
sudo nano .env.qa

sudo bash ./scripts/stack.sh qa deploy --no-build
sudo bash ./scripts/stack.sh qa health
```

`deploy` starts `udaya_pacs_qa_api_tmp`, health-checks it, promotes it to `udaya_pacs_qa_api`, and removes tmp/rollback containers after success.

The same command also keeps `udaya_pacs_qa_redis` running for API cache. `down` removes the API and Redis containers but keeps the Redis Docker volume.

## Deploy DB On Server

```bash
sudo mkdir -p /var/www/udaya_pacs_qa_db
sudo unzip -o /tmp/udaya_pacs_qa_db.zip -d /var/www/udaya_pacs_qa_db
cd /var/www/udaya_pacs_qa_db

if [ -f ./postgres-18.tar ]; then sudo docker load -i ./postgres-18.tar; fi
sudo cp .env.db.example .env.db
sudo nano .env.db
sudo bash ./scripts/deploy-db.sh
sudo docker inspect udaya_pacs_qa_db --format '{{.State.Health.Status}}'
```

## Local Commands

```bash
cd PACS_API
bash ./scripts/stack.sh local deploy --no-build
bash ./scripts/stack.sh local health
```

Git Bash:

```bash
bash ./scripts/stack.sh local deploy --no-build
bash ./scripts/stack.sh local health
```

## Useful Checks

```bash
curl -fsS http://localhost:8080/pacsApi/actuator/health
docker logs --tail 200 udaya_pacs_qa_api
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
```

## Partition Maintenance

Flyway V197+ uses `partition_maintenance_configs` for fixed technical-log retention and policy-aware audit retention:

```sql
SELECT run_partition_maintenance();
```

Flyway V205 removes the obsolete `future_months` column and keeps only
`future_partitions`. The config table contains exactly the six native
log/history parents; `pacs_worklists` and `pacs_studies` remain unpartitioned
source-of-truth tables.

Flyway V196 adds the missing clinical config rows, trims final duplicate
indexes, and moves unknown-hospital DICOM callbacks into
`dicom_server_unmatched_callback_log` so `dicom_server_callback_log` remains
hospital-scoped.

Fixed technical/event logs use monthly partitions with 12-month auto-drop.
Worklist histories and study retention delete requests use yearly partitions
and only clean up rows with policy metadata such as `purge_after`.

Spring Boot runs partition maintenance monthly with an advisory lock. To
validate partitions:

```bash
psql -d emr_pacs_db -f tools/sql/partition-maintenance/validate_partition_maintenance.sql
```

Full notes: [PARTITION_MAINTENANCE.md](docs/database/PARTITION_MAINTENANCE.md).

## PACS Week Cache

Flyway V199+ adds rebuildable 7-day list caches:

```sql
SELECT refresh_pacs_week_cache();
SELECT cleanup_pacs_week_cache();
```

Default Worklist/Study list screens read `pacs_worklists_week_cache` and
`pacs_studies_week_cache`. Detail, exact search, old date ranges, retention,
audit, and export flows continue to read the main `pacs_worklists` and
`pacs_studies` tables.

Spring Boot refreshes the cache weekly and cleans old rows daily with an
advisory lock. Validate and benchmark it with:

```bash
psql -d emr_pacs_db -f tools/sql/week-cache/validate_pacs_week_cache.sql
psql -d emr_pacs_db -f tools/sql/week-cache/explain_pacs_week_cache.sql
```

Full notes: [PACS_WEEK_CACHE.md](docs/database/PACS_WEEK_CACHE.md).

## Docker Database Operations

```bash
bash ./scripts/stack.sh local db-backup
bash ./scripts/stack.sh local db-migrate --build
bash ./scripts/stack.sh local db-validate
bash ./scripts/stack.sh local db-refresh-cache
bash ./scripts/stack.sh local db-partition-maintenance
```

Linux uses the same actions through `bash scripts/stack.sh`. See
[DB_DOCKER_MIGRATION_GUIDE.md](docs/DB_DOCKER_MIGRATION_GUIDE.md).

## Complete Schema Export

Use PostgreSQL `pg_dump`, not a generic table-DDL exporter:

```bash
bash ./tools/export_complete_schema.sh --output-path ./dist/emr_pacs_db_schema.sql
```

The export is restored into a temporary database and validated before it is
accepted. See
[COMPLETE_SCHEMA_EXPORT.md](docs/database/COMPLETE_SCHEMA_EXPORT.md).
