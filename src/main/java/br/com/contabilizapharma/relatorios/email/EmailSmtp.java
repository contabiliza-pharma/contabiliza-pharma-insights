package br.com.contabilizapharma.relatorios.email;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

public class EmailSmtp implements EmailSender {

    private final JavaMailSender mail;
    private final String remetente;
    private final String nomeRemetente;

    public EmailSmtp(JavaMailSender mail, String remetente, String nomeRemetente) {
        this.mail = mail;
        this.remetente = remetente;
        this.nomeRemetente = nomeRemetente;
    }

    @Override
    public void enviar(MensagemEmail m) throws Exception {
        MimeMessage msg = mail.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
        h.setFrom(new InternetAddress(remetente, nomeRemetente, StandardCharsets.UTF_8.name()));
        h.setTo(m.para());
        h.setSubject(m.assunto());
        h.setText(m.texto(), m.html());
        mail.send(msg);
    }

    @Override
    public boolean ativo() {
        return true;
    }
}
