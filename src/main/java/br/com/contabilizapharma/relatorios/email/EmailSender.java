package br.com.contabilizapharma.relatorios.email;

/**
 * Envio de e-mail. Implementações (escolhidas por EMAIL_MODO):
 *  - desligado  → {@link EmailDesligado} (padrão; só registra no log que haveria envio)
 *  - smtp       → {@link EmailSmtp} (Google Workspace: smtp-relay.gmail.com ou smtp.gmail.com com senha de app)
 *  - gmail-api  → {@link EmailGmailApi} (Google Workspace: conta de serviço + delegação em todo o domínio)
 */
public interface EmailSender {
    void enviar(MensagemEmail mensagem) throws Exception;

    boolean ativo();
}
