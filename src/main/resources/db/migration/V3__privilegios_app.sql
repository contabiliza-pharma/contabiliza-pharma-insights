-- V3: privilégio mínimo (Guia §6.3).
-- O Flyway roda com o papel de migração (dono das tabelas). A API Java e o
-- microsserviço Python usam o papel de aplicação, sem DDL.
-- O nome do papel vem do placeholder ${app_role} (variável DB_APP_USER).
-- Se o papel não existir (ex.: ambiente de desenvolvimento com um único usuário), nada é feito.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_role}')
       AND '${app_role}' <> current_user THEN

        EXECUTE 'GRANT USAGE ON SCHEMA public TO ${app_role}';
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${app_role}';
        EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ${app_role}';

        -- Auditoria é só de inserção (Guia §7.2)
        EXECUTE 'REVOKE UPDATE, DELETE ON log_acao FROM ${app_role}';
        -- Catálogo de CFOP: a aplicação só lê e acrescenta
        EXECUTE 'REVOKE UPDATE, DELETE ON cfop FROM ${app_role}';
        -- Histórico do Flyway não é da aplicação
        EXECUTE 'REVOKE ALL ON flyway_schema_history FROM ${app_role}';

        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_role}';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO ${app_role}';
    END IF;
END
$$;
