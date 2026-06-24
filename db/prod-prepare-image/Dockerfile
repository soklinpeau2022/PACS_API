FROM postgres:18

LABEL org.opencontainers.image.title="UDAYA PACS prod-prepare audit DB"
LABEL org.opencontainers.image.description="PostgreSQL image initialized from a cleaned PACS prod-prepare export for TablePlus audit."

ARG INIT_SQL
COPY ${INIT_SQL} /docker-entrypoint-initdb.d/01_emr_pacs_db_prod_prepare.sql
COPY prod-prepare-image/create_tableplus_readonly.sh /docker-entrypoint-initdb.d/02_create_tableplus_readonly.sh
