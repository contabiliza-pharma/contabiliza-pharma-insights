package br.com.contabilizapharma.relatorios.auth;

import br.com.contabilizapharma.relatorios.config.AppProperties;
import br.com.contabilizapharma.relatorios.core.auditoria.Auditoria;
import br.com.contabilizapharma.relatorios.core.erro.ErroNegocio;
import br.com.contabilizapharma.relatorios.core.seguranca.ErroLimiteTaxa;
import br.com.contabilizapharma.relatorios.core.seguranca.LimitadorTaxa;
import br.com.contabilizapharma.relatorios.core.seguranca.PoliticaSenha;
import br.com.contabilizapharma.relatorios.core.seguranca.UsuarioAutenticado;
import br.com.contabilizapharma.relatorios.empresa.Cnpj;
import br.com.contabilizapharma.relatorios.empresa.EmpresaRepository;
import br.com.contabilizapharma.relatorios.empresa.EstabelecimentoRepository;
import br.com.contabilizapharma.relatorios.usuario.Usuario;
import br.com.contabilizapharma.relatorios.usuario.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Regras de acesso: US001 (login), US002 (troca obrigatória), US004 (bloqueio). */
@Service
public class AuthService {

    /** Mensagem única para e-mail inexistente, senha errada e conta inativa (Guia §6.13 e §6.17). */
    static final String CREDENCIAIS_INVALIDAS = "E-mail ou senha inválidos.";

    private final UsuarioRepository usuarios;
    private final EmpresaRepository empresas;
    private final EstabelecimentoRepository estabelecimentos;
    private final PasswordEncoder encoder;
    private final PoliticaSenha politica;
    private final LimitadorTaxa limitador;
    private final Auditoria auditoria;
    private final AppProperties props;
    private final TransactionTemplate transacao;
    private final String hashFicticio;

    public AuthService(UsuarioRepository usuarios, EmpresaRepository empresas,
                       EstabelecimentoRepository estabelecimentos, PasswordEncoder encoder,
                       PoliticaSenha politica, LimitadorTaxa limitador, Auditoria auditoria,
                       AppProperties props, TransactionTemplate transacao) {
        this.usuarios = usuarios;
        this.empresas = empresas;
        this.estabelecimentos = estabelecimentos;
        this.encoder = encoder;
        this.politica = politica;
        this.limitador = limitador;
        this.auditoria = auditoria;
        this.props = props;
        this.transacao = transacao;
        // Usado quando o e-mail não existe, para o tempo de resposta ser o mesmo
        this.hashFicticio = encoder.encode("senha-ficticia-para-tempo-constante");
    }

    public UsuarioAutenticado autenticar(String emailInformado, String senha, String ip) {
        String email = emailInformado.trim().toLowerCase(Locale.ROOT);
        String msgLimite = "Muitas tentativas de entrada. Aguarde alguns minutos e tente de novo.";
        limitador.consumir("login:" + ip + ":" + email, 5, Duration.ofMinutes(1), msgLimite);
        limitador.consumir("login:" + ip + ":" + email, 20, Duration.ofHours(1), msgLimite);

        Usuario u = usuarios.findByEmail(email).orElse(null);
        LocalDateTime agora = LocalDateTime.now();

        if (u == null || !u.isAtivo()) {
            encoder.matches(senha, hashFicticio);
            auditoria.registrar(u == null ? null : u.getId(), "falha_login", Auditoria.mascararEmail(email), ip);
            throw credenciaisInvalidas();
        }
        if (u.estaBloqueado(agora)) {
            auditoria.registrar(u.getId(), "login_bloqueado", null, ip);
            throw contaBloqueada(u.getBloqueadoAte(), agora);
        }
        if (!encoder.matches(senha, u.getSenhaHash())) {
            LocalDateTime bloqueadoAte = registrarFalha(u, ip);
            throw bloqueadoAte != null ? contaBloqueada(bloqueadoAte, agora) : credenciaisInvalidas();
        }
        if (u.senhaTemporariaExpirada(agora)) {
            auditoria.registrar(u.getId(), "senha_temporaria_expirada", null, ip);
            throw new ErroNegocio(HttpStatus.UNAUTHORIZED, "Senha temporária expirada",
                    "Sua senha temporária expirou. Peça uma nova à contabilidade.");
        }
        return transacao.execute(st -> {
            Usuario atual = usuarios.findById(u.getId()).orElseThrow();
            atual.registrarAcesso(agora);
            if (encoder.upgradeEncoding(atual.getSenhaHash())) {
                atual.setSenhaHash(encoder.encode(senha));   // rehash automático (Guia §6.13)
            }
            auditoria.registrar(atual.getId(), "login", null, ip);
            return UsuarioAutenticado.de(atual, Instant.now());
        });
    }

    /** Conta a falha; na 5ª seguida bloqueia por 15 min (US004). Devolve o fim do bloqueio, se houve. */
    private LocalDateTime registrarFalha(Usuario u, String ip) {
        return transacao.execute(st -> {
            short tentativas = usuarios.incrementarTentativas(u.getId());
            auditoria.registrar(u.getId(), "falha_login", null, ip);
            if (tentativas < props.senha().maxTentativas()) {
                return null;
            }
            LocalDateTime ate = LocalDateTime.now().plus(props.senha().bloqueio());
            usuarios.bloquearAte(u.getId(), ate);
            auditoria.registrar(u.getId(), "bloquear", "automatico: tentativas erradas", ip);
            return ate;
        });
    }

    @Transactional
    public UsuarioAutenticado trocarSenha(UsuarioAutenticado logado, AuthDtos.TrocarSenhaEntrada entrada, String ip) {
        limitador.consumir("troca-senha:" + logado.id(), 5, Duration.ofHours(1),
                "Muitas tentativas de troca de senha. Tente de novo mais tarde.");
        Usuario u = usuarios.findById(logado.id()).orElseThrow(this::credenciaisInvalidas);
        if (!encoder.matches(entrada.senhaAtual(), u.getSenhaHash())) {
            auditoria.registrar(u.getId(), "falha_trocar_senha", null, ip);
            throw ErroNegocio.invalido("A senha atual não confere.");
        }
        politica.validarNova(entrada.novaSenha(), entrada.confirmacao(), entrada.senhaAtual());
        u.definirSenhaDefinitiva(encoder.encode(entrada.novaSenha()));
        auditoria.registrar(u.getId(), "trocar_senha", null, ip);
        return logado.comSenhaDefinitiva();
    }

    @Transactional(readOnly = true)
    public AuthDtos.UsuarioSaida descrever(UsuarioAutenticado u) {
        AuthDtos.EmpresaResumo empresa = null;
        if (u.empresaId() != null) {
            empresa = empresas.findById(u.empresaId()).map(e -> {
                String cnpj = estabelecimentos.findByEmpresaIdOrderByMatrizDescCnpjAsc(e.getId()).stream()
                        .findFirst().map(est -> Cnpj.formatar(est.getCnpj())).orElse(null);
                return new AuthDtos.EmpresaResumo(e.getId(), e.getRazaoSocial(), cnpj);
            }).orElse(null);
        }
        return new AuthDtos.UsuarioSaida(u.id(), u.nome(), u.email(), u.perfil().name(), u.senhaTemporaria(), empresa);
    }

    private ErroNegocio credenciaisInvalidas() {
        return new ErroNegocio(HttpStatus.UNAUTHORIZED, "Não foi possível entrar", CREDENCIAIS_INVALIDAS);
    }

    private ErroNegocio contaBloqueada(LocalDateTime ate, LocalDateTime agora) {
        long minutos = Math.max(1, Duration.between(agora, ate).toMinutes() + 1);
        String detalhe = minutos > 24 * 60
                ? "Sua conta está bloqueada. Fale com a contabilidade."
                : "Sua conta foi bloqueada por tentativas erradas. Tente de novo em " + minutos + " minuto(s).";
        return new ErroLimiteTaxa(detalhe, minutos * 60);
    }
}
