\pset pager off

-- 1. Exact duplicate indexes (same table, method, keys, expressions, predicate,
-- collation, and options). Uniqueness is kept in the signature so a normal
-- index covered by a unique index is reported in section 2 instead.
WITH index_catalog AS (
    SELECT
        ns.nspname AS schema_name,
        tbl.relname AS table_name,
        idx.relname AS index_name,
        am.amname AS access_method,
        pi.indisunique,
        pi.indisprimary,
        COALESCE(pg_get_expr(pi.indpred, pi.indrelid), '') AS predicate,
        COALESCE(pg_get_expr(pi.indexprs, pi.indrelid), '') AS expressions,
        pi.indkey::text AS key_numbers,
        pi.indclass::text AS operator_classes,
        pi.indcollation::text AS collations,
        pi.indoption::text AS options
    FROM pg_index pi
    JOIN pg_class idx ON idx.oid = pi.indexrelid
    JOIN pg_class tbl ON tbl.oid = pi.indrelid
    JOIN pg_namespace ns ON ns.oid = tbl.relnamespace
    JOIN pg_am am ON am.oid = idx.relam
    WHERE ns.nspname NOT IN ('pg_catalog', 'information_schema')
)
SELECT
    schema_name,
    table_name,
    array_agg(index_name ORDER BY index_name) AS duplicate_indexes,
    count(*) AS duplicate_count
FROM index_catalog
GROUP BY
    schema_name,
    table_name,
    access_method,
    indisunique,
    indisprimary,
    predicate,
    expressions,
    key_numbers,
    operator_classes,
    collations,
    options
HAVING count(*) > 1
ORDER BY 1, 2, 3;

-- 2. B-tree left-prefix overlap candidates. These require human review:
-- different predicates, ordering, INCLUDE columns, uniqueness, FK support, and
-- measured query plans can make both indexes useful.
WITH btree_indexes AS (
    SELECT
        ns.nspname AS schema_name,
        tbl.relname AS table_name,
        idx.relname AS index_name,
        pi.indisunique,
        pi.indisprimary,
        COALESCE(pg_get_expr(pi.indpred, pi.indrelid), '') AS predicate,
        ARRAY(
            SELECT pg_get_indexdef(pi.indexrelid, key_position, TRUE)
            FROM generate_series(1, pi.indnkeyatts) AS key_position
            ORDER BY key_position
        ) AS key_columns,
        pg_relation_size(idx.oid) AS index_bytes
    FROM pg_index pi
    JOIN pg_class idx ON idx.oid = pi.indexrelid
    JOIN pg_class tbl ON tbl.oid = pi.indrelid
    JOIN pg_namespace ns ON ns.oid = tbl.relnamespace
    JOIN pg_am am ON am.oid = idx.relam
    WHERE ns.nspname NOT IN ('pg_catalog', 'information_schema')
      AND am.amname = 'btree'
      AND NOT pi.indisprimary
)
SELECT
    narrower.schema_name,
    narrower.table_name,
    narrower.index_name AS narrower_index,
    covering.index_name AS covering_index,
    narrower.indisunique AS narrower_unique,
    covering.indisunique AS covering_unique,
    narrower.predicate AS narrower_predicate,
    covering.predicate AS covering_predicate,
    narrower.key_columns AS narrower_keys,
    covering.key_columns AS covering_keys,
    pg_size_pretty(narrower.index_bytes) AS narrower_size,
    pg_size_pretty(covering.index_bytes) AS covering_size
FROM btree_indexes narrower
JOIN btree_indexes covering
  ON covering.schema_name = narrower.schema_name
 AND covering.table_name = narrower.table_name
 AND covering.index_name <> narrower.index_name
 AND narrower.key_columns =
     covering.key_columns[1:array_length(narrower.key_columns, 1)]
WHERE array_length(narrower.key_columns, 1) <= array_length(covering.key_columns, 1)
  AND NOT (narrower.indisunique AND NOT covering.indisunique)
ORDER BY narrower.schema_name, narrower.table_name, narrower.index_name, covering.index_name;

-- 3. Size and usage snapshot. idx_scan is reset-dependent and must never be
-- the only reason to drop an index.
SELECT
    stat.schemaname,
    stat.relname AS table_name,
    stat.indexrelname AS index_name,
    stat.idx_scan,
    stat.idx_tup_read,
    stat.idx_tup_fetch,
    pg_size_pretty(pg_relation_size(stat.indexrelid)) AS index_size,
    pg_get_indexdef(stat.indexrelid) AS definition
FROM pg_stat_user_indexes stat
ORDER BY pg_relation_size(stat.indexrelid) DESC, stat.schemaname, stat.relname, stat.indexrelname;
