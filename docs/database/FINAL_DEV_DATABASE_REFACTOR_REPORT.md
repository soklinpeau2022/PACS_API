# Final DEV Database Refactor Report

This report closes the DEV performance prompt with the concrete repository changes added after V192:

- `V193__final_dev_schema_hardening.sql`
- `V194__final_hot_table_index_cleanup.sql`
- `V195__add_monthly_partition_maintenance.sql`
- `V196__hospital_scope_callbacks_and_trim_final_indexes.sql`
- `V197__correct_partition_retention_rules.sql`
- `V198__quiet_partition_child_index_maintenance.sql`
- large-list mapper/service keyset changes
- retention review CTE projection and keyset paging cleanup
- reset, verification, and benchmark SQL under `tools/sql/`

## Reset And Extensions

`tools/sql/dev-reset/final_optimized_dev_schema.sql` drops and recreates `public` for disposable DEV databases, grants schema access, and creates:

- `pgcrypto`
- `pg_trgm`

`FlywayMigrationConfig` also runs the same extension DDL through `spring.flyway.init-sql`, so clean installs have both extensions before older migrations use `gen_random_uuid()` or `gin_trgm_ops`.

## Index Cleanup

V194 creates the replacement indexes first, then drops legacy overlap.

### pacs_worklists

Kept or added:

- primary key
- `uq_pacs_worklists_public_id`
- `ux_pacs_worklists_id_hospital`
- `ux_pacs_worklists_id_hospital`; V196 keeps the FK-backed index object and renames it to the canonical name
- `ux_pacs_worklists_hospital_visit_code_lower`
- `idx_pacs_worklists_hospital_status_created`
- `idx_pacs_worklists_hospital_status_scheduled`
- `idx_pacs_worklists_hospital_patient`
- `idx_pacs_worklists_hospital_modality_status`
- `idx_pacs_worklists_hospital_route_status`

Removed:

| Index | Reason | Replacement |
|---|---|---|
| `idx_pacs_worklists_active_hospital_status_schedule_id_desc` | overlapping active/schedule shape | `idx_pacs_worklists_hospital_status_scheduled` |
| `idx_pacs_worklists_active_patient_modality` | overlapping patient/modality shape | patient and modality indexes above |
| `idx_pacs_worklists_hospital_status_id_desc` | list now sorts by `(created_at,id)` | `idx_pacs_worklists_hospital_status_created` |
| `idx_pacs_worklists_operational_hospital_id_desc` | broad queue index | targeted status/patient indexes |
| `idx_pacs_worklists_operational_hospital_schedule` | schedule overlap | `idx_pacs_worklists_hospital_status_scheduled` |
| `idx_worklist_hospital_id_desc` | broad hospital/id list | targeted status-created index |
| `idx_pacs_worklists_hospital_patient_status_id_desc` | left-prefix overlap | `idx_pacs_worklists_hospital_patient` |
| `idx_pacs_worklists_hospital_status_scheduled_id_desc` | replaced with final name/order | `idx_pacs_worklists_hospital_status_scheduled` |
| `idx_pacs_worklists_hospital_modality_status_created` | replaced with final route-free shape | `idx_pacs_worklists_hospital_modality_status` |
| `idx_worklists_hospital_modality_status_id_desc` | duplicate modality/status shape | `idx_pacs_worklists_hospital_modality_status` |
| `idx_pacs_worklists_image_received_at` | no list screen filters only by image timestamp | none |
| `idx_pacs_worklists_study_description_trgm` | heavy fuzzy index removed from hot table | exact/list indexes |
| `idx_pacs_worklists_visit_code_trgm` | exact visit-code lookup is hospital-scoped | `ux_pacs_worklists_hospital_visit_code_lower` |
| `idx_pacs_worklists_lower_visit_code` | unsafe global lookup | `ux_pacs_worklists_hospital_visit_code_lower` |
| `idx_pacs_worklists_dicom_route` | duplicate of final route/status index | `idx_pacs_worklists_hospital_route_status` |
| `idx_pacs_worklists_hospital_study_id` | low-value compatibility pointer index | canonical link table and FK indexes |
| `idx_pacs_worklists_route_hospital_modality` | route-first shape not used by list APIs | `idx_pacs_worklists_hospital_route_status` |

### pacs_studies

Kept or added:

- primary key
- `uq_pacs_studies_public_id`
- `ux_pacs_studies_id_hospital`
- `ux_pacs_studies_hospital_study_instance_uid`
- `idx_pacs_studies_hospital_study_date`
- `idx_pacs_studies_hospital_received`
- `idx_pacs_studies_hospital_patient_date`
- `idx_pacs_studies_hospital_accession`
- `idx_pacs_studies_hospital_dicom_server_study`
- `idx_pacs_studies_hospital_modality_date`

Removed:

| Index | Reason | Replacement |
|---|---|---|
| `idx_studies_hospital_active_id_desc` | broad active list | date/received indexes |
| `idx_studies_hospital_status_id_desc` | status-only overlap | status handled by filtered date paths |
| `idx_pacs_studies_hospital_status_study_date_id_desc` | overlapping date/status shape | `idx_pacs_studies_hospital_study_date` |
| `idx_pacs_studies_hospital_study_date_id_desc` | replaced with final name | `idx_pacs_studies_hospital_study_date` |
| `idx_pacs_studies_hospital_lower_accession_active` | exact accession index is enough | `idx_pacs_studies_hospital_accession` |
| `idx_pacs_studies_hospital_patient` | patient/date covers patient list | `idx_pacs_studies_hospital_patient_date` |
| `idx_pacs_studies_status` | global status scan | hospital-scoped indexes |
| `idx_pacs_studies_study_uid` | global UID lookup | hospital-scoped unique UID |
| `idx_pacs_studies_hospital_instance_count_active` | no live dashboard/indexed filter uses it | daily stats |
| `idx_pacs_studies_hospital_lower_institution_active` | heavy low-value search index | none |
| `idx_pacs_studies_hospital_dicom_server` | replaced with study lookup | `idx_pacs_studies_hospital_dicom_server_study` |
| `idx_pacs_studies_hospital_dicom_server_study_id` | replaced with final name | `idx_pacs_studies_hospital_dicom_server_study` |
| `idx_pacs_studies_hospital_modality_active` | replaced with modality/date | `idx_pacs_studies_hospital_modality_date` |
| `idx_pacs_studies_accession_trgm` | hot-table fuzzy index removed | exact accession index |
| `idx_pacs_studies_description_trgm` | hot-table fuzzy index removed | none |
| `idx_pacs_studies_modality_trgm` | modality is relational | modality/date index |
| `idx_pacs_studies_hospital_notification_received` | overlaps hospital received-date list index | `idx_pacs_studies_hospital_received` |
| `idx_pacs_studies_hospital_uploaded_source` | source upload path is not a list filter | none |

### pacs_results And Images

Kept or added:

- `ux_pacs_results_public_id`
- `ux_pacs_results_id_hospital`
- `ux_pacs_results_hospital_study_active`
- `ux_pacs_results_hospital_worklist_active`
- `idx_pacs_results_hospital_patient_created`
- `idx_pacs_results_hospital_status_created`
- `idx_pacs_result_images_hospital_result_active`
- `idx_pacs_result_images_hospital_study_active`
- `idx_pacs_result_images_hospital_worklist_active`

Removed duplicate or broad indexes:

- `ux_pacs_results_hospital_modality_study_active`
- `ux_pacs_results_hospital_modality_worklist_active`
- `ux_pacs_results_hospital_modality_queue_active`
- `idx_pacs_results_patient`
- `idx_pacs_results_patient_code`
- `idx_pacs_result_images_result_active`
- `idx_pacs_result_images_hospital_modality`

## FK Cleanup

V193 keeps composite hospital-safe relationships and drops redundant simple FKs:

- `pacs_worklists(patient_id,hospital_id) -> patients(id,hospital_id)`
- `pacs_studies(patient_id,hospital_id) -> patients(id,hospital_id)`
- `pacs_results(study_id,hospital_id) -> pacs_studies(id,hospital_id)`
- `pacs_results(worklist_id,hospital_id) -> pacs_worklists(id,hospital_id)`
- `pacs_results(patient_id,hospital_id) -> patients(id,hospital_id)`
- `pacs_result_images(result_id,hospital_id) -> pacs_results(id,hospital_id)`
- `pacs_result_images(study_id,hospital_id) -> pacs_studies(id,hospital_id)`
- `pacs_result_images(worklist_id,hospital_id) -> pacs_worklists(id,hospital_id)`
- `pacs_viewer_states` composite study/worklist/patient FKs from V188 remain
- `pacs_worklist_study_links` composite study/worklist FKs from V188 remain

Simple FKs removed include the old patient/study/worklist/image/viewer-state references that could allow cross-hospital linkage without the paired hospital key.

## Constraints

V193 adds explicit final checks for:

- `pacs_worklists.status`: `1=WAITING`, `2=IN_PROGRESS`, `3=CANCELLED`, `4=FAILED`
- `pacs_results.status`: `IMAGE_RECEIVED`, `DRAFT`, `PRELIMINARY`, `FINAL`, `CANCELLED`
- `pacs_results` must reference at least one parent (`study_id` or `worklist_id`)
- `pacs_result_images.hospital_id` and `result_id` are `NOT NULL`
- `pacs_result_images.image_path` must be relative, not a URL or absolute local path
- image file sizes and sort order checks from V188 remain
- viewer payload size and SHA checks from V171 remain
- retention days/value/unit checks
- DICOM and machine port ranges
- OAuth2 token lifetime positivity

PACS result completion now writes `FINAL`. Legacy `COMPLETED` rows are migrated to `FINAL`, while Java read logic still treats old `COMPLETED` as completed if it appears in an older environment.

## Partitioning

Native partitions are implemented for high-growth technical and audit tables:

| Table | Partition key | Granularity | Retention |
|---|---|---|---|
| `system_activities` | `created` | month | fixed 12 months |
| `user_logs` | `created` | month | fixed 12 months |
| `dicom_server_callback_log` | `received_at` | month | fixed 12 months |
| `pacs_realtime_notification_events` | `created_at` | month | fixed 12 months |
| `pacs_worklist_histories` | `created` | year | policy-based |
| `study_retention_delete_requests` | `created_at` | year | policy-based |

V195 adds the first maintenance layer. V197 corrects the retention model and adds:

- `partition_maintenance_configs`
- `create_future_partitions()`
- `drop_expired_fixed_partitions()`
- `cleanup_policy_based_retention_data(p_dry_run)`
- `drop_policy_partitions_if_fully_expired(p_dry_run)`
- `run_partition_maintenance()`
- optional `pg_cron` schedule/unschedule helper functions
- Spring Boot monthly scheduler with a PostgreSQL advisory lock

Only fixed-retention technical log partitions from `pg_inherits` whose names match `parent_YYYYMM` are eligible for automatic age-based dropping. Parent tables, default partitions, non-monthly child tables, and policy-based medical/audit tables are never dropped by the fixed 12-month rule.

Clinical table partitioning for `pacs_studies` and `pacs_worklists` is documented as a DEV reset target, not an automatic Flyway conversion, because those tables have many incoming FKs and ON CONFLICT contracts. Converting them in-place belongs in a controlled shadow-table reset, not normal application startup.

V205 removes the obsolete `pacs_worklists` and `pacs_studies` config rows and
the duplicate `future_months` column. The maintenance table now contains only
the six native partitioned parents and uses `future_partitions` for lookahead.

`dicom_server_callback_log` and `pacs_realtime_notification_events` use child-local dedupe indexes with targetless `ON CONFLICT DO NOTHING` inserts. `pacs_worklist_histories` and `study_retention_delete_requests` have `purge_after` metadata and policy-based dry-run cleanup.

## API Query Performance

Changed large list APIs:

- Worklist list now orders by `q.created_at DESC, q.id DESC`, accepts `(lastCreatedAt,lastWorklistId)`, and emits no OFFSET.
- Study list accepts `(lastStudySortAt,lastStudyId)`, preserves `lastStudyId` fallback, and emits no OFFSET.
- Patient, system activity, and user-log lists emit `LIMIT` only; callers use `lastPatientId`, `lastActivityId`, and `lastUserLogId` for next windows.
- Study retention review no longer runs a live CTE count, projects explicit columns instead of `SELECT *`, and accepts `(lastStatus,lastExpiresAt,lastStudyId)` for next windows.
- These services no longer run live `COUNT(*)` for list pagination.
- Dashboard studies count defaults to `pacs_daily_stats` (`dashboard.studies-count-source=summary`).
- Viewer-state metadata endpoint excludes heavy JSONB payload columns.
- Callback/event log insert remains idempotent with partition-local dedupe and `ON CONFLICT DO NOTHING`.
- Unknown-hospital callback logs are routed to `dicom_server_unmatched_callback_log`; `dicom_server_callback_log.hospital_id` is `NOT NULL`.

## Verification

Run:

```bash
psql -d emr_pacs_dev -f tools/sql/final-verification/final_dev_database_verification.sql
psql -d emr_pacs_dev -f tools/sql/partition-maintenance/validate_partition_maintenance.sql
psql -d emr_pacs_dev -f tools/sql/partition-maintenance/test_partition_maintenance.sql
```

Then run scale tests:

```bash
psql -d pacs_perf -f tools/sql/deep-database-refactor/loadtest/prepare_scale_lab.sql
psql -d pacs_perf -f tools/sql/deep-database-refactor/loadtest/final_acceptance_queries.sql
```
