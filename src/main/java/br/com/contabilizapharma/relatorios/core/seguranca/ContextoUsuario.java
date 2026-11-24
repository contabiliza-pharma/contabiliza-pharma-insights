package br.com.contabilizapharma.relatorios.core.seguranca;

import br.com.contabilizapharma.relatorios.core.erro.ErroNegocio;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class ContextoUsuario {

    private ContextoUsuario() {}

    public static UsuarioAutenticado atual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado u) {
            return u;
        }
        throw new ErroNegocio(HttpStatus.UNAUTHORIZED, "Não autenticado", "Entre novamente para continuar.");
    }
}
