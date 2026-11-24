package br.com.contabilizapharma.relatorios.admin;

import br.com.contabilizapharma.relatorios.config.AppProperties;
import br.com.contabilizapharma.relatorios.core.auditoria.Auditoria;
import br.com.contabilizapharma.relatorios.core.erro.ErroNegocio;
import br.com.contabilizapharma.relatorios.core.seguranca.PoliticaSenha;
import br.com.contabilizapharma.relatorios.core.seguranca.SessoesUsuario;
import br.com.contabilizapharma.relatorios.email.NotificacaoEmail;
import br.com.contabilizapharma.relatorios.empresa.EmpresaRepository;
import br.com.contabilizapharma.relatorios.usuario.Perfil;
import br.com.contabilizapharma.relatorios.usuario.Usuario;
import br.com.contabilizapharma.relatorios.usuario.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** US008 (criar conta), US009 (redefinir senha), US010 (bloquear/desativar/reativar), US011 (listar). */
@Service
public class UsuarioAdminService {

    private static final Set<String> SITUACOES = Set.of("ativo", "senha_pendente", "bloqueado", "inativo");
    /** Bloqueio manual sem prazo: vale até o administrador desbloquear. */
    private static final LocalDateTime BLOQUEIO_INDEFINIDO = LocalDateTime.of(9999, 12, 31, 0, 0);

    private final UsuarioRepository usuarios;
    private final EmpresaRepository empresas;
    private final PasswordEncoder encoder;
    private final PoliticaSenha politica;
    private final SessoesUsuario sessoes;
    private final Auditoria auditoria;
    private final NotificacaoEmail notificacao;
    private final AppProperties props;
    private final JdbcTemplate jdbc;

    public UsuarioAdminService(UsuarioRepository usuarios, EmpresaRepository empresas, PasswordEncoder encoder,
                               PoliticaSenha politica, SessoesUsuario sessoes, Auditoria auditoria,
                               NotificacaoEmail notificacao, AppProperties props, JdbcTemplate jdbc) {
        this.usuarios = usuarios;
        this.empresas = empresas;
        this.encoder = encoder;
        this.politica = politica;
        this.sessoes = sessoes;
        this.auditoria = auditoria;
        this.notificacao = notificacao;
        this.props = props;
        this.jdbc = jdbc;
    }

    private static final String SELECT_USUARIO = """
            SELECT u.id, u.nome, u.email, u.perfil::text AS perfil, u.empresa_id, e.razao_social,
                   u.ativo, u.senha_temporaria, u.senha_expira_em, u.bloqueado_ate, u.ultimo_acesso, u.criado_em,
                   CASE WHEN NOT u.ativo THEN 'inativo'
                        WHEN u.bloqueado_ate > now() THEN 'bloqueado'
                        WHEN u.senha_temporaria THEN 'senha_pendente'
                        ELSE 'ativo' END AS situacao
              FROM usuario u LEFT JOIN empresa e ON e.id = u.empresa_id
            """;

    private static final RowMapper<AdminDtos.UsuarioSaida> MAPA = (rs, i) -> new AdminDtos.UsuarioSaida(
            rs.getInt("id"), rs.getString("nome"), rs.getString("email"), rs.getString("perfil"),
            (Integer) rs.getObject("empresa_id"), rs.getString("razao_social"), rs.getString("situacao"),
            rs.getBoolean("senha_temporaria"), ldt(rs.getTimestamp("senha_expira_em")),
            ldt(rs.getTimestamp("bloqueado_ate")), ldt(rs.getTimestamp("ultimo_acesso")),
            ldt(rs.getTimestamp("criado_em")));

    @Transactional(readOnly = true)
    public List<AdminDtos.UsuarioSaida> listar(String busca, String situacao) {
        String termo = busca == null ? "" : busca.trim();
        String digitos = termo.replaceAll("\\D", "");
        String sit = situacao == null ? "" : situacao.trim();
        if (!sit.isEmpty() && !SITUACOES.contains(sit)) {
            throw ErroNegocio.invalido("Situação inválida.");
        }
        return jdbc.query("SELECT * FROM (" + SELECT_USUARIO + """
                 WHERE ? = ''
                    OR u.nome ILIKE ? ESCAPE '\\' OR u.email ILIKE ? ESCAPE '\\'
                    OR e.razao_social ILIKE ? ESCAPE '\\'
                    OR (length(?) >= 8 AND EXISTS (SELECT 1 FROM estabelecimento s
                                                    WHERE s.empresa_id = u.empresa_id AND s.cnpj LIKE ?))
                ) x WHERE (? = '' OR x.situacao = ?)
                 ORDER BY x.nome LIMIT 300
                """, MAPA, termo, like(termo), like(termo), like(termo), digitos, digitos + "%", sit, sit);
    }

    @Transactional(readOnly = true)
    public AdminDtos.UsuarioSaida detalhar(Integer id) {
        return jdbc.query(SELECT_USUARIO + " WHERE u.id = ?", MAPA, id).stream().findFirst()
                .orElseThrow(() -> ErroNegocio.naoEncontrado("Usuário não encontrado."));
    }

    @Transactional
    public AdminDtos.SenhaTemporariaSaida criar(AdminDtos.UsuarioEntrada in, Integer adminId, String ip) {
        String email = in.email().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByEmail(email)) {
            throw ErroNegocio.conflito("Já existe uma conta com este e-mail.");
        }
        Integer empresaId = null;
        if (in.perfil() == Perfil.cliente) {
            if (in.empresaId() == null) {
                throw ErroNegocio.invalido("Escolha a empresa do cliente.");
            }
            var empresa = empresas.findById(in.empresaId())
                    .orElseThrow(() -> ErroNegocio.invalido("Empresa não encontrada."));
            if (!empresa.isAtiva()) {
                throw ErroNegocio.invalido("A empresa está inativa. Reative-a antes de criar contas.");
            }
            empresaId = empresa.getId();
        }
        String temporaria = politica.gerarTemporaria();
        LocalDateTime expira = LocalDateTime.now().plus(props.senha().validadeTemporaria());
        Usuario u = new Usuario(in.nome().trim(), email, encoder.encode(temporaria), in.perfil(), empresaId);
        u.definirSenhaTemporaria(u.getSenhaHash(), expira);
        u = usuarios.saveAndFlush(u);
        auditoria.registrar(adminId, "criar_conta", "usuario:" + u.getId(), ip);
        boolean enviado = notificacao.contaCriada(u.getNome(), u.getEmail(), temporaria, expira);
        return new AdminDtos.SenhaTemporariaSaida(detalhar(u.getId()), temporaria, expira, enviado);
    }

    @Transactional
    public AdminDtos.SenhaTemporariaSaida redefinirSenha(Integer id, Integer adminId, String ip) {
        Usuario u = buscar(id);
        String temporaria = politica.gerarTemporaria();
        LocalDateTime expira = LocalDateTime.now().plus(props.senha().validadeTemporaria());
        u.definirSenhaTemporaria(encoder.encode(temporaria), expira);   // invalida a anterior
        usuarios.saveAndFlush(u);
        sessoes.encerrarTodas(id);
        auditoria.registrar(adminId, "redefinir_senha", "usuario:" + id, ip);
        boolean enviado = notificacao.senhaRedefinida(u.getNome(), u.getEmail(), temporaria, expira);
        return new AdminDtos.SenhaTemporariaSaida(detalhar(id), temporaria, expira, enviado);
    }

    @Transactional
    public AdminDtos.UsuarioSaida bloquear(Integer id, Integer adminId, String ip) {
        Usuario u = buscarOutro(id, adminId);
        u.setBloqueadoAte(BLOQUEIO_INDEFINIDO);
        usuarios.saveAndFlush(u);
        sessoes.encerrarTodas(id);
        auditoria.registrar(adminId, "bloquear", "usuario:" + id, ip);
        return detalhar(id);
    }

    @Transactional
    public AdminDtos.UsuarioSaida desbloquear(Integer id, Integer adminId, String ip) {
        Usuario u = buscar(id);
        u.setBloqueadoAte(null);
        u.zerarTentativas();
        usuarios.saveAndFlush(u);
        auditoria.registrar(adminId, "desbloquear", "usuario:" + id, ip);
        return detalhar(id);
    }

    @Transactional
    public AdminDtos.UsuarioSaida definirAtivo(Integer id, boolean ativo, Integer adminId, String ip) {
        Usuario u = ativo ? buscar(id) : buscarOutro(id, adminId);
        if (!ativo && u.getPerfil() == Perfil.administrador
                && usuarios.countByPerfilAndAtivoTrue(Perfil.administrador) <= 1) {
            throw ErroNegocio.invalido("Não é possível desativar o último administrador ativo.");
        }
        u.setAtivo(ativo);
        usuarios.saveAndFlush(u);
        if (!ativo) {
            sessoes.encerrarTodas(id);
        }
        auditoria.registrar(adminId, ativo ? "reativar" : "desativar", "usuario:" + id, ip);
        return detalhar(id);
    }

    private Usuario buscar(Integer id) {
        return usuarios.findById(id).orElseThrow(() -> ErroNegocio.naoEncontrado("Usuário não encontrado."));
    }

    private Usuario buscarOutro(Integer id, Integer adminId) {
        if (id.equals(adminId)) {
            throw ErroNegocio.invalido("Você não pode bloquear ou desativar a própria conta.");
        }
        return buscar(id);
    }

    private static String like(String termo) {
        return "%" + EmpresaAdminService.escaparLike(termo) + "%";
    }

    private static LocalDateTime ldt(java.sql.Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
