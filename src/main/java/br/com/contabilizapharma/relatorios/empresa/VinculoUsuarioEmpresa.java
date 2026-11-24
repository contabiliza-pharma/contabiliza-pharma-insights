package br.com.contabilizapharma.relatorios.empresa;

import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Vínculo N:N entre usuário e empresa (tabela usuario_empresa).
 * Um usuário pode ver várias empresas (várias raízes de CNPJ) e uma empresa pode ter vários usuários.
 */
@Repository
public class VinculoUsuarioEmpresa {

    /** Empresa como o usuário a enxerga: id, razão social, CNPJ principal (matriz primeiro) e situação. */
    public record EmpresaVinculada(Integer id, String razaoSocial, String cnpj, boolean ativa, int estabelecimentos) {}

    /** Usuário vinculado a uma empresa (tela da empresa no painel administrativo). */
    public record UsuarioVinculado(Integer id, String nome, String email, String perfil, boolean ativo,
                                   int totalEmpresas) {}

    private static final String SELECT_EMPRESA = """
            SELECT e.id, e.razao_social, e.ativa,
                   (SELECT s.cnpj FROM estabelecimento s WHERE s.empresa_id = e.id
                     ORDER BY s.matriz DESC, s.cnpj LIMIT 1) AS cnpj,
                   (SELECT count(*) FROM estabelecimento s WHERE s.empresa_id = e.id) AS qtd
              FROM empresa e
            """;

    private final JdbcTemplate jdbc;

    public VinculoUsuarioEmpresa(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EmpresaVinculada> empresasDoUsuario(Integer usuarioId) {
        return jdbc.query(SELECT_EMPRESA + """
                 JOIN usuario_empresa ue ON ue.empresa_id = e.id
                WHERE ue.usuario_id = ?
                ORDER BY e.razao_social
                """, (rs, i) -> mapear(rs), usuarioId);
    }

    /** Todas as empresas (o administrador vê todas). */
    public List<EmpresaVinculada> todasAtivas() {
        return jdbc.query(SELECT_EMPRESA + " WHERE e.ativa ORDER BY e.razao_social", (rs, i) -> mapear(rs));
    }

    public boolean temAcesso(Integer usuarioId, Integer empresaId) {
        Boolean r = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM usuario_empresa WHERE usuario_id = ? AND empresa_id = ?)",
                Boolean.class, usuarioId, empresaId);
        return Boolean.TRUE.equals(r);
    }

    public int contarEmpresas(Integer usuarioId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM usuario_empresa WHERE usuario_id = ?",
                Integer.class, usuarioId);
        return n == null ? 0 : n;
    }

    public long contarUsuarios(Integer empresaId) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM usuario_empresa WHERE empresa_id = ?", Long.class, empresaId);
        return n == null ? 0 : n;
    }

    public List<UsuarioVinculado> usuariosDaEmpresa(Integer empresaId) {
        return jdbc.query("""
                SELECT u.id, u.nome, u.email, u.perfil::text AS perfil, u.ativo,
                       (SELECT count(*) FROM usuario_empresa x WHERE x.usuario_id = u.id) AS total
                  FROM usuario u JOIN usuario_empresa ue ON ue.usuario_id = u.id
                 WHERE ue.empresa_id = ?
                 ORDER BY u.nome
                """, (rs, i) -> new UsuarioVinculado(rs.getInt("id"), rs.getString("nome"), rs.getString("email"),
                rs.getString("perfil"), rs.getBoolean("ativo"), rs.getInt("total")), empresaId);
    }

    /** true se criou o vínculo; false se já existia. */
    public boolean vincular(Integer usuarioId, Integer empresaId, Integer porUsuarioId) {
        return jdbc.update("""
                INSERT INTO usuario_empresa (usuario_id, empresa_id, vinculado_por) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """, usuarioId, empresaId, porUsuarioId) > 0;
    }

    public boolean desvincular(Integer usuarioId, Integer empresaId) {
        return jdbc.update("DELETE FROM usuario_empresa WHERE usuario_id = ? AND empresa_id = ?",
                usuarioId, empresaId) > 0;
    }

    /** Substitui o conjunto de empresas do usuário (mantém a data dos vínculos que continuam). */
    public void definir(Integer usuarioId, Collection<Integer> empresaIds, Integer porUsuarioId) {
        Integer[] ids = empresaIds.toArray(Integer[]::new);
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "DELETE FROM usuario_empresa WHERE usuario_id = ? AND NOT (empresa_id = ANY (?))");
            ps.setInt(1, usuarioId);
            ps.setArray(2, con.createArrayOf("integer", ids));
            return ps;
        });
        for (Integer id : empresaIds) {
            vincular(usuarioId, id, porUsuarioId);
        }
    }

    private static EmpresaVinculada mapear(java.sql.ResultSet rs) throws java.sql.SQLException {
        String cnpj = rs.getString("cnpj");
        return new EmpresaVinculada(rs.getInt("id"), rs.getString("razao_social"),
                cnpj == null ? null : Cnpj.formatar(cnpj), rs.getBoolean("ativa"), rs.getInt("qtd"));
    }
}
