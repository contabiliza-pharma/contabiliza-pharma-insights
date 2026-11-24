package br.com.contabilizapharma.relatorios.core.seguranca;

import br.com.contabilizapharma.relatorios.config.AppProperties;
import br.com.contabilizapharma.relatorios.core.web.RespostaJson;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Regras de sessão que o Spring não cobre sozinho:
 *  - expiração absoluta de 12 h (Guia §6.12); a de 30 min por inatividade é do servlet;
 *  - com senha temporária, só as rotas de troca de senha respondem (US002, Guia §6.11).
 */
public class SessaoFilter extends OncePerRequestFilter {

    private static final Set<String> ROTAS_SENHA_TEMPORARIA = Set.of(
            "/api/v1/auth/me", "/api/v1/auth/trocar-senha", "/api/v1/auth/logout", "/api/v1/auth/csrf");

    private final AppProperties props;
    private final RespostaJson resposta;

    public SessaoFilter(AppProperties props, RespostaJson resposta) {
        this.props = props;
        this.resposta = resposta;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u) {
            if (u.loginEm().plus(props.sessao().duracaoAbsoluta()).isBefore(Instant.now())) {
                HttpSession s = req.getSession(false);
                if (s != null) {
                    s.invalidate();
                }
                SecurityContextHolder.clearContext();
                resposta.problema(res, HttpStatus.UNAUTHORIZED, "Sessão expirada",
                        "Sua sessão expirou. Entre novamente.");
                return;
            }
            if (u.senhaTemporaria() && !ROTAS_SENHA_TEMPORARIA.contains(req.getRequestURI())) {
                resposta.problema(res, HttpStatus.FORBIDDEN, "Troca de senha obrigatória",
                        "Crie sua nova senha para continuar.");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
