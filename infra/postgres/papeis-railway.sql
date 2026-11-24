-- Cria os papéis do banco no Postgres do Railway (lá o script 01-papeis.sh do Docker não roda).
-- Rode UMA vez, conectado como o superusuário do Railway (postgres), ANTES do 1º deploy da API:
--
--   psql "<DATABASE_PUBLIC_URL do serviço Postgres no Railway>" \
--        -v migrator_senha='<DB_MIGRATOR_PASSWORD>' -v app_senha='<DB_APP_PASSWORD>' \
--        -f infra/postgres/papeis-railway.sql
--
-- As senhas são TEXTO PURO (as mesmas cadastradas nas variáveis da API e do processamento).

CREATE ROLE app_migrator LOGIN PASSWORD :'migrator_senha';
CREATE ROLE app_rw LOGIN PASSWORD :'app_senha';
GRANT CONNECT ON DATABASE :"DBNAME" TO app_migrator, app_rw;
ALTER SCHEMA public OWNER TO app_migrator;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
-- As permissões de app_rw nas tabelas são dadas pela migração V3 (Flyway), na 1ª subida da API.
