package br.com.contabilizapharma.relatorios.auth;

import br.com.contabilizapharma.relatorios.core.auditoria.Auditoria;
import br.com.contabilizapharma.relatorios.core.seguranca.ContextoUsuario;
import br.com.contabilizapharma.relatorios.core.seguranca.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/** Rotas finas: validam a entrada, chamam o serviço e cuidam só da sessão HTTP. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;
    private final SecurityContextRepository contextos;
    private final Auditoria auditoria;

    public AuthController(AuthService service, SecurityContextRepository contextos, Auditoria auditoria) {
        this.service = service;
        this.contextos = contextos;
        this.auditoria = auditoria;
    }

    /** Entrega o token anti-CSRF ligado à sessão (Guia §8.6). */
    @GetMapping("/csrf")
    public AuthDtos.CsrfSaida csrf(CsrfToken token) {
        return new AuthDtos.CsrfSaida(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/login")
    public AuthDtos.UsuarioSaida login(@Valid @RequestBody AuthDtos.LoginEntrada entrada,
                                       HttpServletRequest req, HttpServletResponse res) {
        UsuarioAutenticado u = service.autenticar(entrada.email(), entrada.senha(), req.getRemoteAddr());
        req.getSession(true);
        req.changeSessionId();                      // nova sessão no login (Guia §6.12)
        salvarNaSessao(u, req, res);
        return service.descrever(u);
    }

    @GetMapping("/me")
    public AuthDtos.UsuarioSaida me() {
        return service.descrever(ContextoUsuario.atual());
    }

    @PostMapping("/trocar-senha")
    public AuthDtos.UsuarioSaida trocarSenha(@Valid @RequestBody AuthDtos.TrocarSenhaEntrada entrada,
                                             HttpServletRequest req, HttpServletResponse res) {
        UsuarioAutenticado atualizado = service.trocarSenha(ContextoUsuario.atual(), entrada, req.getRemoteAddr());
        req.changeSessionId();
        salvarNaSessao(atualizado, req, res);
        return service.descrever(atualizado);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req) {
        UsuarioAutenticado u = ContextoUsuario.atual();
        HttpSession s = req.getSession(false);
        if (s != null) {
            s.invalidate();
        }
        SecurityContextHolder.clearContext();
        auditoria.registrar(u.id(), "logout", null, req.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    private void salvarNaSessao(UsuarioAutenticado u, HttpServletRequest req, HttpServletResponse res) {
        var auth = new UsernamePasswordAuthenticationToken(u, null,
                List.of(new SimpleGrantedAuthority(u.perfil().papel())));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextos.saveContext(ctx, req, res);
    }
}
