# UDAYA_PACS_API

Spring Boot API for UDAYA_PACS. The current deploy flow is intentionally small:

- `scripts/package-deploy.*` builds an API deploy bundle.
- `scripts/package-db-deploy.*` builds a DB deploy bundle.
- `scripts/stack.*` starts, health-checks, promotes, stops, and logs containers.
- `scripts/test-gate.*` is used internally by `package-deploy` unless the skip flag is passed.

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

If a target password is missing or still a placeholder, `stack.ps1` / `stack.sh` generates a strong password and writes it to the target env file. Do not commit real Redis passwords.

## Deploy Telegram Notifications

`stack.sh` and `stack.ps1` send Telegram notifications for `deploy` success and failure when these values are set in the runtime env file or server environment:

```env
DEPLOY_TELEGRAM_ENABLED=true
DEPLOY_TELEGRAM_CHAT_ID=
DEPLOY_TELEGRAM_API_TOKEN=
```

Target-specific keys are also supported, for example `QA_TELEGRAM_CHAT_ID` and `QA_TELEGRAM_API_TOKEN`. Failure notifications attach a JSON report with deploy status, host, container, image, Docker status, disk/uptime summary, and recent container logs with sensitive fields redacted.

## Build Deploy Bundle

Windows:

```powershell
cd D:\Soklin\PACS_System\PACS_API
powershell -ExecutionPolicy Bypass -File .\scripts\package-deploy.ps1 -Target qa -SkipTests -SkipPentest
powershell -ExecutionPolicy Bypass -File .\scripts\package-db-deploy.ps1 -Target qa -NoData
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
sudo mkdir -p /var/www/udaya_pacs_qa_api /var/ut-image /var/ut-key
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
sudo docker compose --env-file .env.db up -d
sudo docker inspect udaya_pacs_qa_db --format '{{.State.Health.Status}}'
```

## Local Commands

```powershell
cd D:\Soklin\PACS_System\PACS_API
powershell -ExecutionPolicy Bypass -File .\scripts\stack.ps1 local deploy -NoBuild
powershell -ExecutionPolicy Bypass -File .\scripts\stack.ps1 local health
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
