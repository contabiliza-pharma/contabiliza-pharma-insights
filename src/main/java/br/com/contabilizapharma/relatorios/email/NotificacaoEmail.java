package br.com.contabilizapharma.relatorios.email;

import br.com.contabilizapharma.relatorios.config.AppProperties;
import br.com.contabilizapharma.relatorios.core.auditoria.Auditoria;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * E-mails de conta. O envio é assíncrono e uma falha nunca desfaz a operação do administrador.
 * A senha temporária só vai no e-mail se EMAIL_ENVIAR_SENHA_TEMPORARIA=true
 * (canal oficial ainda pendente — ESCOPO §12.2); caso contrário o e-mail só avisa que a conta existe.
 */
@Service
public class NotificacaoEmail {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoEmail.class);
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final EmailSender sender;
    private final AppProperties.Email cfg;
    private final TaskExecutor executor;

    public NotificacaoEmail(EmailSender sender, AppProperties props,
                            @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.sender = sender;
        this.cfg = props.email();
        this.executor = executor;
    }

    /** @return true se o e-mail foi colocado na fila de envio. */
    public boolean contaCriada(String nome, String email, String senhaTemporaria, LocalDateTime expira) {
        return enfileirar(montar(email, "Seu acesso à Plataforma de Relatórios ContabilizaPharma",
                "Sua conta na Plataforma de Relatórios da ContabilizaPharma foi criada.", nome, senhaTemporaria, expira));
    }

    public boolean senhaRedefinida(String nome, String email, String senhaTemporaria, LocalDateTime expira) {
        return enfileirar(montar(email, "Nova senha temporária — Plataforma de Relatórios ContabilizaPharma",
                "A senha da sua conta na Plataforma de Relatórios foi redefinida.", nome, senhaTemporaria, expira));
    }

    private boolean enfileirar(MensagemEmail m) {
        if (!sender.ativo()) {
            return false;
        }
        executor.execute(() -> {
            try {
                sender.enviar(m);
                log.info("email_enviado para={}", Auditoria.mascararEmail(m.para()));
            } catch (Exception e) {
                log.error("email_falhou para={} erro={}", Auditoria.mascararEmail(m.para()), e.getClass().getSimpleName());
            }
        });
        return true;
    }

    private MensagemEmail montar(String para, String assunto, String abertura, String nome, String senha,
                                 LocalDateTime expira) {
        boolean comSenha = cfg.enviarSenhaTemporaria();
        String url = cfg.urlPlataforma();
        String prazo = expira.format(DATA_HORA);
        String instrucao = comSenha
                ? "Sua senha temporária é: " + senha + "\nEla vale até " + prazo + " e só pode ser usada uma vez. "
                  + "No primeiro acesso você vai criar a sua própria senha."
                : "A senha temporária será repassada pela contabilidade. Ela vale até " + prazo + ".";
        String texto = "Olá, " + nome + ".\n\n" + abertura + "\n\n" + instrucao + "\n\nAcesse: " + url
                + "\n\nSe você não esperava este e-mail, fale com a contabilidade.\n\nContabilizaPharma";

        String e = HtmlUtils.htmlEscape(nome);
        String senhaHtml = comSenha
                ? "<p>Sua senha temporária é:</p><p style=\"font:600 20px monospace;letter-spacing:2px;color:#002068\">"
                  + HtmlUtils.htmlEscape(senha) + "</p><p>Ela vale até <b>" + prazo
                  + "</b> e só pode ser usada uma vez. No primeiro acesso você vai criar a sua própria senha.</p>"
                : "<p>A senha temporária será repassada pela contabilidade. Ela vale até <b>" + prazo + "</b>.</p>";
        String html = "<div style=\"font-family:Arial,sans-serif;font-size:16px;color:#1b1f2a;max-width:520px\">"
                + "<p style=\"color:#002068;font-weight:700;font-size:18px\">ContabilizaPharma</p>"
                + "<p>Olá, " + e + ".</p><p>" + HtmlUtils.htmlEscape(abertura) + "</p>" + senhaHtml
                + "<p><a href=\"" + HtmlUtils.htmlEscape(url) + "\" style=\"display:inline-block;background:#002068;"
                + "color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none\">Acessar a plataforma</a></p>"
                + "<p style=\"color:#5b6275;font-size:14px\">Se você não esperava este e-mail, fale com a contabilidade.</p></div>";
        return new MensagemEmail(para, assunto, texto, html);
    }
}
