# Scale test

Use only a disposable PostgreSQL database whose name contains `load`, `perf`,
`bench`, or `migration_test`.

```bash
psql -v row_count=50000 -v patient_count=10000 -v worklist_count=50000 -v result_count=10000 -v log_count=100000 -v event_count=100000 -f prepare_scale_lab.sql
psql -f benchmark_scale_queries.sql
psql -f final_acceptance_queries.sql
```

Stage 2:

```bash
psql -v row_count=1000000 -v patient_count=100000 -v worklist_count=1000000 -v result_count=500000 -v log_count=5000000 -v event_count=5000000 -f prepare_scale_lab.sql
psql -f benchmark_scale_queries.sql
psql -f final_acceptance_queries.sql
```

The larger runs require substantial disk, WAL, and maintenance time; run them
only on production-equivalent infrastructure.

Capture:

- load duration and rows/second;
- database, table, and index bytes;
- WAL bytes;
- query p50/p95/p99 over at least 1,000 iterations;
- shared blocks read/hit, temporary bytes, and rows removed by filter;
- concurrent insert p95 while list/search traffic is running.

Acceptance targets:

- normal list p95 below 300 ms and p99 below 1 s;
- exact lookup p95 below 100 ms;
- worklist insert p95 below 150 ms;
- idempotent callback p95 below 300 ms;
- hospital/date study search p95 below 500 ms.
