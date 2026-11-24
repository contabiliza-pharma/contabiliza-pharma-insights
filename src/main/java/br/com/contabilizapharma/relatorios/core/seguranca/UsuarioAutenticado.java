package br.com.contabilizapharma.relatorios.core.seguranca;

import br.com.contabilizapharma.relatorios.usuario.Perfil;
import br.com.contabilizapharma.relatorios.usuario.Usuario;
import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;

/**
 * Usuário guardado na sessão (Redis). Não contém senha nem hash.
 * getName() devolve o id: é por ele que as sessões são indexadas e derrubadas (US010).
 */
public record UsuarioAutenticado(
        Integer id,
        String nome,
        String email,
        Perfil perfil,
        Integer empresaId,
        boolean senhaTemporaria,
        Instant loginEm) implements Principal, Serializable {

    public static UsuarioAutenticado de(Usuario u, Instant loginEm) {
        return new UsuarioAutenticado(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(),
                u.getEmpresaId(), u.isSenhaTemporaria(), loginEm);
    }

    public UsuarioAutenticado comSenhaDefinitiva() {
        return new UsuarioAutenticado(id, nome, email, perfil, empresaId, false, loginEm);
    }

    @Override
    public String getName() {
        return String.valueOf(id);
    }

    public boolean ehCliente() {
        return perfil == Perfil.cliente;
    }
}
