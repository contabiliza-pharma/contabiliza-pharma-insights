package br.com.contabilizapharma.relatorios.email;

import br.com.contabilizapharma.relatorios.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class EmailConfig {

    @Bean
    public EmailSender emailSender(AppProperties props, ObjectProvider<JavaMailSender> mail) {
        AppProperties.Email cfg = props.email();
        String modo = cfg.modo() == null ? "desligado" : cfg.modo().trim().toLowerCase();
        return switch (modo) {
            case "smtp" -> new EmailSmtp(exigir(mail.getIfAvailable()), exigirTexto(cfg.remetente(), "EMAIL_REMETENTE"),
                    cfg.nomeRemetente());
            case "gmail-api" -> new EmailGmailApi(
                    exigirTexto(cfg.gmail().credenciaisArquivo(), "GOOGLE_SERVICE_ACCOUNT_FILE"),
                    cfg.gmail().usuarioDelegado(),
                    exigirTexto(cfg.remetente(), "EMAIL_REMETENTE"), cfg.nomeRemetente());
            case "desligado" -> new EmailDesligado();
            default -> throw new IllegalStateException("EMAIL_MODO inválido: use desligado, smtp ou gmail-api.");
        };
    }

    private static JavaMailSender exigir(JavaMailSender mail) {
        if (mail == null) {
            throw new IllegalStateException("EMAIL_MODO=smtp exige SMTP_HOST configurado.");
        }
        return mail;
    }

    private static String exigirTexto(String valor, String variavel) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Defina a variável " + variavel + " para enviar e-mails.");
        }
        return valor;
    }
}
