package br.com.contabilizapharma.relatorios.email;

import br.com.contabilizapharma.relatorios.core.auditoria.Auditoria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailDesligado implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(EmailDesligado.class);

    @Override
    public void enviar(MensagemEmail m) {
        log.info("email_desligado assunto=\"{}\" para={}", m.assunto(), Auditoria.mascararEmail(m.para()));
    }

    @Override
    public boolean ativo() {
        return false;
    }
}
