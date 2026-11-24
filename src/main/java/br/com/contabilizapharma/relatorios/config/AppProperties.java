package br.com.contabilizapharma.relatorios.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração da aplicação (prefixo "app" no application.yml). Valores vêm do ambiente. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String ambiente,
        List<String> origensPermitidas,
        Sessao sessao,
        Senha senha,
        Admin admin,
        Processamento processamento,
        Email email) {

    public boolean producao() {
        return "prod".equalsIgnoreCase(ambiente);
    }

    public record Sessao(Duration duracaoAbsoluta) {}

    public record Senha(Duration validadeTemporaria, int maxTentativas, Duration bloqueio) {}

    public record Admin(String nome, String email, String senhaHash) {}

    public record Processamento(String url, String token, Duration timeout) {}

    public record Email(
            String modo,
            String remetente,
            String nomeRemetente,
            String urlPlataforma,
            boolean enviarSenhaTemporaria,
            Gmail gmail) {}

    public record Gmail(String credenciaisArquivo, String usuarioDelegado) {}
}
