package br.com.contabilizapharma.relatorios.email;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.client.RestClient;

/**
 * Envio pela Gmail API do Google Workspace com conta de serviço (chave JSON) e delegação em
 * todo o domínio (escopo https://www.googleapis.com/auth/gmail.send). A conta de serviço
 * "age como" o usuário delegado (ex.: nao-responda@contabilizapharma.com.br).
 * Passo a passo no README, seção "E-mail (Google Workspace)".
 */
public class EmailGmailApi implements EmailSender {

    private static final String ESCOPO = "https://www.googleapis.com/auth/gmail.send";
    private static final String URL_ENVIO = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private final GoogleCredentials credenciais;
    private final String remetente;
    private final String nomeRemetente;
    private final RestClient http = RestClient.create();

    public EmailGmailApi(String arquivoCredenciais, String usuarioDelegado, String remetente, String nomeRemetente) {
        try (InputStream in = new FileInputStream(arquivoCredenciais)) {
            this.credenciais = ServiceAccountCredentials.fromStream(in)
                    .createScoped(List.of(ESCOPO))
                    .createDelegated(usuarioDelegado == null || usuarioDelegado.isBlank() ? remetente : usuarioDelegado);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível ler a chave da conta de serviço do Google "
                    + "(GOOGLE_SERVICE_ACCOUNT_FILE).", e);
        }
        this.remetente = remetente;
        this.nomeRemetente = nomeRemetente;
    }

    @Override
    public void enviar(MensagemEmail m) throws Exception {
        credenciais.refreshIfExpired();
        String token = credenciais.getAccessToken().getTokenValue();

        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
        MimeMessageHelper h = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
        h.setFrom(new InternetAddress(remetente, nomeRemetente, StandardCharsets.UTF_8.name()));
        h.setTo(m.para());
        h.setSubject(m.assunto());
        h.setText(m.texto(), m.html());
        ByteArrayOutputStream bruto = new ByteArrayOutputStream();
        msg.writeTo(bruto);

        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bruto.toByteArray());
        http.post().uri(URL_ENVIO)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("raw", raw))
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public boolean ativo() {
        return true;
    }
}
