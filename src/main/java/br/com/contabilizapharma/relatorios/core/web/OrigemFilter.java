package br.com.contabilizapharma.relatorios.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Defesa adicional contra CSRF (Guia §8.6): em métodos que alteram estado, se o navegador
 * enviar Origin, ele precisa estar na lista de origens permitidas.
 */
public class OrigemFilter extends OncePerRequestFilter {

    private static final Set<String> SEGUROS = Set.of("GET", "HEAD", "OPTIONS");
    private final List<String> permitidas;
    private final RespostaJson resposta;

    public OrigemFilter(List<String> permitidas, RespostaJson resposta) {
        this.permitidas = permitidas;
        this.resposta = resposta;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String origem = req.getHeader("Origin");
        if (!SEGUROS.contains(req.getMethod()) && origem != null && !"null".equals(origem)
                && !permitidas.contains(origem)) {
            resposta.problema(res, HttpStatus.FORBIDDEN, "Acesso negado", "Origem da requisição não permitida.");
            return;
        }
        chain.doFilter(req, res);
    }
}
