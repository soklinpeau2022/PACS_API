# PACS Performance Refactor — Phase V189+

Continuation of the staged work in `DEEP_DATABASE_REFACTOR.md` / `LARGE_SCALE_SCHEMA_AUDIT.md`
(migrations V185–V188). This phase closes the remaining gaps so the schema scales from
1M → 100M+ rows. It is delivered as **forward Flyway migrations V189–V198** plus application
changes — no destructive rebuild, so DEV/QA/prod stay on one migration history.

Database facts confirmed against the live local DB (`emr_pacs_db`, PostgreSQL 18): `pg_trgm`
and `pgcrypto` already installed; 0 NOT VALID constraints remaining locally; hot-table row
counts at audit time — `system_activities` 155k, `user_logs` 2k, others ≤ ~85.

---

## 1. Migrations

| Version | Purpose | Transactional |
|---|---|---|
| `V189__enable_pgcrypto_and_uuid_defaults.sql` | `CREATE EXTENSION IF NOT EXISTS pgcrypto`; switch every `public_id`/`result_public_id`/`image_public_id` **default** from `md5(random()…)` to `gen_random_uuid()`. Defaults only — existing UUIDs, columns and uniques untouched. | yes |
| `V190__validate_hospital_safe_constraints.sql` | Validate every remaining NOT VALID CHECK/FK (codifies `finalize_constraints.sql`). Idempotent: no-op where already validated (local DEV), validates on QA/prod. | yes |
| `V191__trim_redundant_hot_table_indexes.sql` | Drop only provably-redundant indexes (see report below). `DROP INDEX CONCURRENTLY` + `executeInTransaction=false`. | no |
| `V192__partition_append_only_log_tables.sql` | Convert 4 append-only tables to monthly RANGE partitioning via guarded in-place swap; ships `pacs_ensure_monthly_partition()`. | yes |
| `V193__final_dev_schema_hardening.sql` | Final FK/check/status hardening and result-image scope backfill. | yes |
| `V194__final_hot_table_index_cleanup.sql` | Final hot-table index cleanup. Non-transactional via `.conf` for concurrent index work. | no |
| `V195__add_monthly_partition_maintenance.sql` | Config table plus create/drop/wrapper functions for monthly partition maintenance, with optional pg_cron helpers. | yes |
| `V196__hospital_scope_callbacks_and_trim_final_indexes.sql` | Quarantine unknown callbacks, make callback hospital scope mandatory, remove final duplicate/overlap indexes, and seed all eight maintenance config rows. | yes |
| `V197__correct_partition_retention_rules.sql` | Split fixed 12-month technical-log retention from policy-based yearly medical/audit retention; partition callback/realtime logs and add policy cleanup dry-runs. | yes |
| `V198__quiet_partition_child_index_maintenance.sql` | Quiet repeated child-local dedupe index checks during scheduled maintenance. | yes |

### Index cleanup report (V191)

Driven by structure (the live DB has little traffic, so `idx_scan` is not yet a reliable
signal). Dropped only bare single-column or strict left-prefix indexes a retained wider index
already covers given the actual mapper predicates:

| Dropped | Reason | Covered by |
|---|---|---|
| `idx_patients_hospital` | bare `hospital_id` | every `(hospital_id, …)` index |
| `idx_patients_hospital_active` | strict prefix | `idx_patients_hospital_active_id_desc` |
| `idx_patients_hospital_id_desc` | list always filters `is_active=1` | `idx_patients_hospital_active_id_desc` |
| `idx_pacs_studies_hospital` | bare `hospital_id` | every `(hospital_id, …)` index |
| `idx_pacs_studies_hospital_date_id_desc` | non-partial dup | `idx_pacs_studies_hospital_study_date_id_desc` (WHERE `is_active=1`) |
| `idx_pacs_worklists_hospital_status` | `(hospital_id,status,created DESC)`; list orders by `id DESC` | `idx_pacs_worklists_hospital_status_id_desc` |

**Kept (intentionally):** all GIN trigram indexes — the mappers do `ILIKE '%…%'` substring
search on `visit_code`, `accession_number`, patient name, `study_description`, and activity
`action/module/description`. **Deferred to a `pg_stat_statements` pass:** the many study/worklist
partial operational indexes that each match a specific mapper predicate (e.g. the V104/V108
operational set) — remove only after 7–14 days of production zero-usage data, per the existing
philosophy. `pacs_studies` (24) and `pacs_worklists` (22) remain over-indexed by design until then.

---

## 2. Partitioning plan

| Table | Partition key | Notes |
|---|---|---|
| `system_activities` | `created` | PK → `(id, created)`; `public_id` unique folded to `(public_id, created)` |
| `user_logs` | `created` | as above |
| `dicom_server_callback_log` | `received_at` | monthly fixed 12-month retention; child-local dedupe indexes |
| `pacs_realtime_notification_events` | `created_at` | monthly fixed 12-month retention; child-local dedupe indexes |
| `pacs_worklist_histories` | `created` | yearly policy-based retention; no fixed 12-month auto-drop |
| `study_retention_delete_requests` | `created_at` | yearly policy-based retention; no fixed 12-month auto-drop |

Keys are the reliable **NOT NULL** time columns the mappers already order/filter by, so pruning
aligns with real queries. V197 keeps the fixed technical-log tables on current + previous 12
monthly partitions plus 3 future months. The policy/audit tables use yearly partitions and keep
future years ready without any blind age-based partition drop. Every managed parent keeps a
DEFAULT catch-all for out-of-range rows. The earlier V192 conversion is **guarded by a 2M-row
ceiling**: a table larger than the guard is skipped with a NOTICE and must use the online cutover
(below).

**Not managed by partition maintenance:** `pacs_worklists` and `pacs_studies`. V197 marks their
old config rows inactive because direct date partitioning conflicts with PostgreSQL's rule that
every unique key on a partitioned table include the partition key; the current API/FK/upsert
contract still needs global `public_id`, `(id,hospital_id)`, and study UID uniqueness.

**Partition maintenance:** V197 uses `run_partition_maintenance()`. The scheduler runs it monthly
with a PostgreSQL advisory lock. It creates future month/year partitions, drops only expired fixed
technical log partitions, dry-runs policy cleanup, never drops parent/default tables, and analyzes
partitioned parents after maintenance.

---

## 3. Query performance guide

- **Keyset pagination** is wired in `WorklistMapper.list`, `StudyMapper.list`,
  `PatientMapper.list`, `ActivityLogMapper.listActivityLog`, `UserLogMapper.listUserLog`,
  and the study-retention review CTE (`lastWorklistId` / `lastStudyId` / `lastPatientId` /
  `lastActivityId` / `lastUserLogId` / `lastStatus,lastExpiresAt,lastStudyId`). These large paths
  emit `LIMIT` without `OFFSET`. Shape:
  ```sql
  WHERE hospital_id = :h AND status = :s AND id < :lastId
  ORDER BY id DESC LIMIT :rows         -- index: (hospital_id, status, id DESC)
  ```
- Smaller admin/config/dropdown lists may still use page-based `OFFSET`; `PaginationHelper` caps
  SQL offset at `MAX_OFFSET` (200000) where those services use the shared helper.
- **List vs detail:** results/result-images/notification lists already select summary columns
  only. Viewer state adds a metadata-only path — `POST /pacs-result/pacs-result-viewer-state-meta`
  (`PacsResultService.findViewerStateMeta`) returns scope + `payload_size_bytes` + freshness
  WITHOUT the ~11 JSONB payload columns, for existence/size checks before downloading a large
  state. The full `…-viewer-state-find` is unchanged. **Follow-up:** viewer adopts meta for badges/preflight; large segmentations should spill to object storage with only a URI/SHA in the DB.

---

## 4. Dashboard summary

`pacs_daily_stats` + `pacs_refresh_daily_stats(date, hospitalId)` already exist (V186).
- `PacsLargeScaleMaintenanceScheduler` refreshes today every 10 min and yesterday nightly.
- The one **unbounded** dashboard count — total studies (`countStudies`, no date bound) — can now
  read `SUM(received_study_count)` from `pacs_daily_stats` via
  `dashboard.studies-count-source=summary` (default `summary` after V193+). The other counts are naturally
  bounded (active queue via partial indexes; today-counts via date filters) and stay live.
  Note the semantic shift: `summary` reports cumulative received studies (throughput), not the
  exact current active count — enable it once the scheduler has seeded data.

---

## 5. Scale lab + benchmarks

`tools/sql/deep-database-refactor/loadtest/` (only runs in a DB whose name matches
`load|perf|bench|migration_test`):
- `prepare_scale_lab.sql` now generates studies (`row_count`, 1M default), patients
  (`patient_count`, 1M), worklists (`worklist_count`, 5M) and a **partitioned** activity log
  (`log_count`, 10M) in schema `pacs_scale_lab`, mirroring the V191 index set + V192 partition design.
- `benchmark_scale_queries.sql` runs `EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS)` for: study list
  (keyset), worklist list (keyset), active-queue snapshot, patient search (exact uid / name prefix /
  trigram), study/patient lookup, and single-month partition pruning.

Run, e.g.:
```bash
createdb pacs_perf
psql -d pacs_perf -f tools/sql/deep-database-refactor/loadtest/prepare_scale_lab.sql \
     -v row_count=10000000 -v patient_count=1000000 -v worklist_count=5000000 -v log_count=10000000
psql -d pacs_perf -f tools/sql/deep-database-refactor/loadtest/benchmark_scale_queries.sql
```
Targets: exact lookup p95 < 100ms, list p95 < 300ms, heavy search p95 < 1000ms, callback insert p95 < 300ms.

---

## 6. Production migration note

DEV applies the migrations as written. For large QA/prod tables:
- **V191 index drops** already use `DROP INDEX CONCURRENTLY` (online).
- **V190** validates FKs/CHECKs online (`VALIDATE CONSTRAINT`, SHARE UPDATE EXCLUSIVE) — run off-peak.
- **V192 partitioning** does an in-transaction table swap + copy. The 2M-row guard SKIPS large
  prod tables; for those, do **not** run an in-place swap. Use the online shadow + cutover from
  `create_partition_shadows.sql` + `partitioning_helpers.sql`: build `_v2` partitioned shadows,
  dual-write / backfill by month, verify counts, then swap names in a short maintenance window.
- New FK/constraint adds on huge tables: add `NOT VALID`, then `VALIDATE CONSTRAINT`; backfill in
  bounded batches (`backfill_compatibility_batch.sql`, SKIP LOCKED).
- **No `DROP TABLE` in production** — the V192 swap drop is DEV/small-table only.
