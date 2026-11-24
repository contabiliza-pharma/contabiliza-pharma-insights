-- V4: vínculo N:N entre usuário e empresa.
-- Um cliente pode ter várias empresas (várias raízes de CNPJ) e uma empresa pode ter vários usuários.
-- Colaboradores (ex.: coordenador do fiscal) também podem ser vinculados para ver os relatórios.
-- Administrador vê todas as empresas e não precisa de vínculo.

CREATE TABLE usuario_empresa (
    usuario_id     integer   NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    empresa_id     integer   NOT NULL REFERENCES empresa (id) ON DELETE CASCADE,
    vinculado_em   timestamp NOT NULL DEFAULT now(),
    vinculado_por  integer   REFERENCES usuario (id),
    PRIMARY KEY (usuario_id, empresa_id)
);
CREATE INDEX ix_usuario_empresa_empresa ON usuario_empresa (empresa_id);

-- Leva os vínculos que já existiam (usuario.empresa_id) para a nova tabela
INSERT INTO usuario_empresa (usuario_id, empresa_id)
SELECT id, empresa_id FROM usuario WHERE empresa_id IS NOT NULL;

ALTER TABLE usuario DROP CONSTRAINT ck_usuario_empresa_por_perfil;
ALTER TABLE usuario DROP COLUMN empresa_id;

-- Mesmo privilégio mínimo da V3 para a nova tabela
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${app_role}')
       AND '${app_role}' <> current_user THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON usuario_empresa TO ${app_role}';
    END IF;
END
$$;
