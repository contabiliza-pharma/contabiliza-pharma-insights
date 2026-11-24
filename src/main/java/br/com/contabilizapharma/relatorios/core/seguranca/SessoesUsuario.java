package br.com.contabilizapharma.relatorios.core.seguranca;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Encerra todas as sessões abertas de um usuário (desativar conta, redefinir senha — US010). */
@Component
public class SessoesUsuario {

    private final FindByIndexNameSessionRepository<? extends Session> sessoes;

    public SessoesUsuario(FindByIndexNameSessionRepository<? extends Session> sessoes) {
        this.sessoes = sessoes;
    }

    public int encerrarTodas(Integer usuarioId) {
        var abertas = sessoes.findByPrincipalName(String.valueOf(usuarioId));
        abertas.keySet().forEach(sessoes::deleteById);
        return abertas.size();
    }
}
