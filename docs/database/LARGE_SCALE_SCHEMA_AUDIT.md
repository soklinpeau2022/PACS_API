# PACS/EMR large-scale PostgreSQL audit

Audit date: 2026-06-18  
Scope: PACS API migrations, PostgreSQL catalog, MyBatis query patterns, and selected backend services.

## Executive summary

The schema already has strong hospital-aware relationships, public UUIDs, status checks, current-viewer-state uniqueness, and useful cursor indexes. The largest scaling risk is not a missing-index problem; it is accumulated overlapping indexes, mixed time columns, unbounded log growth, and future partition conversion complexity.

Phase 1 is implemented by `V185__scope_identifiers_and_remove_redundant_indexes.sql`:

- patient UID and worklist visit-code case-insensitive uniqueness are now hospital-scoped;
- global visit-code lookup remains indexed but no longer enforces global uniqueness;
- confirmed duplicate/covered indexes are removed online;
- generated visit-code collision checks are hospital-scoped;
- authenticated DICOM callbacks use the callback server's hospital when resolving worklists.

The migration is non-transactional and uses `CREATE/DROP INDEX CONCURRENTLY`. Run the pre-validation script before deployment and the post-validation script afterward.

## Evidence from the audited database

The local database snapshot is useful for detecting structural problems but is not production-sized. PostgreSQL statistics are reset-dependent.

| Table | Estimated live rows | Total | Table | Indexes | Finding |
|---|---:|---:|---:|---:|---|
| `system_activities` | 130 | 345 MB | 94 MB | 250 MB | Severe bloat/index accumulation signal |
| `patients` | 10 | 191 MB | 48 kB | 191 MB | Severe index bloat after prior bulk data activity |
| `pacs_studies` | 14 | 1.1 MB | 16 kB | 1.1 MB | Too many indexes for the current row count |
| `pacs_worklists` | 0 | 232 kB | 0 | 224 kB | Multiple historical overlapping indexes |

Examples confirmed from `pg_indexes`:

- `refresh_tokens(token_hash)` duplicated its unique constraint index.
- `revoked_tokens(jti)` duplicated its unique constraint index.
- `role_module_details(role_id, module_detail_id)` duplicated the unique constraint.
- `countries(name)`, `oauth2_clients(client_id)`, and `pacs_patient_sequences(hospital_id, sequence_year)` duplicated unique indexes.
- `hospital_dicom_servers` had two identical `(hospital_id, is_active, id DESC)` indexes.
- `pacs_studies` had two normal hospital/study-UID indexes in addition to the hospital-scoped unique index.
- `pacs_viewer_states(hospital_id, accession_number)` was a left prefix of the actual state-type lookup index with the same predicate.
- simple `system_activities` filter indexes were covered by wider indexes that also support the mapper's date/order pattern.

After V185, measure `pg_stat_user_indexes` for at least one normal business cycle before removing additional overlapping indexes.

## What is already good

- `pacs_studies` has `UNIQUE (hospital_id, study_instance_uid)` and the upsert uses that key.
- `pacs_worklists`, studies, patients, routes, viewer states, and results have hospital-aware joins.
- public API identifiers use UUID indexes and internal numeric IDs remain efficient join keys.
- worklist/study status values have database checks.
- result-image data is stored on disk; the DB stores a relative path and the API builds the public URL.
- `pacs_viewer_states` stores only a current active row per scope, tracks `payload_size_bytes`/SHA-256, rejects payloads above 10 MiB, and clears JSON on soft delete.
- result API keys are hashes; DICOM server passwords and API-key hashes are ignored by JSON serialization.
- realtime notification replay uses `(hospital_id, id)`, a good cursor pattern.

## Main risks at 1M-100M rows

### Search expressions prevent normal index use

Study and worklist date filters sort/filter on `COALESCE(...)` expressions. Simple indexes on `study_date`, `scheduled_date`, or `created_at` cannot fully satisfy those expressions. At scale, introduce one canonical, non-null operational timestamp:

- studies: `effective_received_at`;
- worklists: `effective_scheduled_at`.

Backfill it in bounded batches, write it for new rows, then index `(hospital_id, effective_* DESC, id DESC)`. Do not add more single-column date indexes around the current expression.

### Offset pagination

Some list endpoints still use `OFFSET`. Deep pages become progressively slower. Worklists already support a last-ID cursor; studies and audit logs should gain equivalent keyset cursors.

### Count queries

Exact `COUNT(*)` with broad `ILIKE '%...%'` filters will be expensive at 100M rows even with trigram indexes. Use bounded date ranges, delayed/estimated counts, or a separate search service for unconstrained cross-field search.

### Audit timestamps

`created`, `created_at`, `modified`, and `modified_at` are still mixed and actively used by MyBatis. A direct rename/drop would break API behavior and could rewrite large tables.

Safe sequence:

1. add nullable `created_at`/`updated_at` columns without defaults that rewrite old rows;
2. dual-write from the API or a temporary trigger;
3. backfill by primary-key ranges;
4. change reads and indexes;
5. validate equality and null counts;
6. remove legacy columns in a later release.

### Callback log hospital scope

`dicom_server_callback_log` currently lacks `hospital_id` and `dicom_server_id`. Add them nullable, populate new rows from the machine-client claim, backfill resolvable history in chunks, then add `(hospital_id, received_at DESC, id DESC)`. Do this separately because the callback service and tests must change together.

### Result-image denormalization

Current image queries use globally unique `result_id`/`image_public_id`; adding four copied foreign keys would add write consistency work without helping current queries. Keep the current normalized design until a real study/worklist image-search endpoint exists. If one is added, denormalize `hospital_id`, `study_id`, `worklist_id`, and `modality_id` with a synchronization trigger or explicit API write path, then backfill in chunks.

### JSONB viewer state

The current 10 MiB ceiling prevents unlimited rows but is still too large for frequent updates. For 3D segmentation:

- target a smaller inline threshold (for example 1-2 MiB);
- place labelmaps/surfaces in object storage;
- store URI/key, media type, byte size, and SHA-256 in PostgreSQL;
- keep measurements, annotations, and small metadata inline;
- partition and retain a separate version table only if history is required.

## Prioritized plan

### Phase 1 - safe cleanup (implemented)

- Run `validate_pre_migration.sql`.
- Deploy V185 during a monitored low-traffic window.
- Run `validate_post_migration.sql`.
- Reindex bloated retained indexes individually with `REINDEX INDEX CONCURRENTLY`.
- Do not use `VACUUM FULL` on hot production tables.

### Phase 2 - query-driven indexing

- Capture `pg_stat_statements` for at least 7-14 days.
- Run `explain_core_queries.sql` on production-like staging data.
- Add a canonical operational timestamp for study/worklist lists.
- Convert study/audit lists from offset to keyset pagination.
- Add a patient/study index only when an observed plan proves it is needed; avoid another broad index bundle.

Candidate study target after canonical timestamp:

```sql
(hospital_id, effective_received_at DESC, id DESC) WHERE is_active = 1
(hospital_id, patient_id, effective_received_at DESC, id DESC) WHERE is_active = 1
(hospital_id, modality_id, effective_received_at DESC, id DESC) WHERE is_active = 1
(hospital_id, status, effective_received_at DESC, id DESC) WHERE is_active = 1
```

Candidate worklist target:

```sql
(hospital_id, status, effective_scheduled_at DESC, id DESC)
(hospital_id, patient_id, id DESC)
(hospital_id, modality_id, status, id DESC)
(hospital_id, dicom_route_id, status, id DESC)
(hospital_id, study_id) WHERE study_id IS NOT NULL
```

### Phase 3 - monthly partitioning

Do not directly alter the current core tables. PostgreSQL requires every unique/primary key on a partitioned table to include the partition key, while many foreign keys currently reference numeric IDs. Use shadow tables:

1. create `<table>_v2 PARTITION BY RANGE(canonical_timestamp)`;
2. create monthly partitions plus a default quarantine partition;
3. add partition-local hospital/cursor indexes;
4. dual-write or use logical replication;
5. backfill by month and ID ranges;
6. compare counts, min/max IDs, checksums, and orphan counts;
7. pause writes briefly, sync the tail, swap names, and recreate FKs;
8. retain the old table read-only through the rollback window.

Start with:

1. `system_activities`;
2. `user_logs`;
3. `dicom_server_callback_log`;
4. `pacs_realtime_notification_events`;
5. `pacs_worklist_histories`;
6. `study_retention_delete_requests`.

Partition `pacs_studies`/`pacs_worklists` last because they are central FK targets. Use received/created timestamps, not nullable DICOM dates.

`partitioning_helpers.sql` supplies future-month partition creation for shadow parents.

### Phase 4 - archive and retention

Run `retention_cleanup_batches.sql` repeatedly from a scheduler:

- archive system activities and user logs older than six months;
- archive successful callbacks older than 90 days;
- delete notification events after the replay window (default 14 days);
- preserve failed callbacks longer than successful callbacks.

Each invocation moves/deletes one bounded batch with `FOR UPDATE SKIP LOCKED`.

### Phase 5 - operational verification

- Enable `pg_stat_statements`.
- Track p50/p95/p99 latency, rows read vs returned, temp bytes, WAL, and index bytes.
- Alert on invalid indexes and partitions missing for the next two months.
- Run `ANALYZE` after large backfills.
- Compare insert/update throughput before and after index cleanup.

## Migration and rollback notes

V185 creates replacement indexes before dropping old uniqueness/indexes. Because it is non-transactional, a failed deployment can leave a partially completed migration; every statement is idempotent. Fix the cause and rerun it.

Rollback is in `rollback_v185.sql`. It recreates removed indexes concurrently. Restoring global uniqueness is only possible if no cross-hospital duplicates were created after V185; the rollback script reports those conflicts first.

## Verification assets

- `detect_duplicate_and_overlapping_indexes.sql`: exact duplicates, prefix overlaps, size/usage.
- `validate_pre_migration.sql`: duplicate keys, URL paths, viewer payloads, invalid constraints/indexes.
- `validate_post_migration.sql`: required indexes and uniqueness policy.
- `explain_core_queries.sql`: mapper-shaped `EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)`.
- `retention_cleanup_batches.sql`: bounded archive/delete batches.
- `partitioning_helpers.sql`: future monthly partitions for staged shadow tables.
- `rollback_v185.sql`: online rollback.
