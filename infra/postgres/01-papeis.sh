#!/bin/sh
# Roda só na primeira criação do volume. Privilégio mínimo (Guia §6.3):
#   app_migrator — dono das tabelas; usado só pelo Flyway na subida da API
#   app_rw       — usado pela API Java e pelo microsserviço Python; sem DDL (permissões na migração V3)
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v migrator="$DB_MIGRATOR_USER" -v migrator_senha="$DB_MIGRATOR_PASSWORD" \
  -v app="$DB_APP_USER" -v app_senha="$DB_APP_PASSWORD" -v banco="$POSTGRES_DB" <<'SQL'
CREATE ROLE :"migrator" LOGIN PASSWORD :'migrator_senha';
CREATE ROLE :"app" LOGIN PASSWORD :'app_senha';
ALTER DATABASE :"banco" OWNER TO :"migrator";
ALTER SCHEMA public OWNER TO :"migrator";
REVOKE ALL ON DATABASE :"banco" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"banco" TO :"migrator", :"app";
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SQL
