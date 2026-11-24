package br.com.contabilizapharma.relatorios.processamento;

import br.com.contabilizapharma.relatorios.config.AppProperties;
import br.com.contabilizapharma.relatorios.core.erro.ErroNegocio;
import br.com.contabilizapharma.relatorios.core.web.CorrelacaoFilter;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Cliente do microsserviço Python (extração, conversão e análise de dados).
 * Comunicação só na rede interna, com o token compartilhado PROCESSAMENTO_TOKEN.
 * A autorização por perfil e o isolamento por empresa são feitos AQUI, antes de chamar o serviço.
 */
@Component
public class ProcessamentoClient {

    private static final Logger log = LoggerFactory.getLogger(ProcessamentoClient.class);
    private static final String CABECALHO_TOKEN = "X-Token-Interno";
    private final RestClient http;

    public ProcessamentoClient(AppProperties props) {
        AppProperties.Processamento cfg = props.processamento();
        if (cfg.token() == null || cfg.token().length() < 32) {
            log.warn("PROCESSAMENTO_TOKEN ausente ou curto (mínimo 32 caracteres)");
        }
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                // HTTP/1.1 explícito: o JDK tentaria upgrade h2c, que o uvicorn recusa em requisições com corpo
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(5)).build());
        fabrica.setReadTimeout(cfg.timeout());
        this.http = RestClient.builder()
                .baseUrl(cfg.url())
                .requestFactory(fabrica)
                .defaultHeader(CABECALHO_TOKEN, cfg.token() == null ? "" : cfg.token())
                .build();
    }

    public record Resposta(int status, String corpo) {
        public boolean sucesso() { return status >= 200 && status < 300; }
    }

    public Resposta importar(String tipo, Resource arquivo, String nomeArquivo, Integer usuarioId, boolean substituir) {
        MultipartBodyBuilder corpo = new MultipartBodyBuilder();
        corpo.part("arquivo", arquivo).filename(nomeArquivo);
        corpo.part("usuario_id", String.valueOf(usuarioId));
        corpo.part("substituir", String.valueOf(substituir));
        return chamar(() -> http.post().uri("/internal/importacoes/{tipo}", tipo)
                .header(CorrelacaoFilter.CABECALHO, MDC.get(CorrelacaoFilter.MDC_CHAVE))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(corpo.build())
                .exchange((req, res) -> new Resposta(res.getStatusCode().value(), new String(res.getBody().readAllBytes()))));
    }

    public Resposta getJson(String caminho, Map<String, Object> parametros) {
        return chamar(() -> http.get().uri(b -> montar(b, caminho, parametros))
                .header(CorrelacaoFilter.CABECALHO, MDC.get(CorrelacaoFilter.MDC_CHAVE))
                .accept(MediaType.APPLICATION_JSON)
                .exchange((req, res) -> new Resposta(res.getStatusCode().value(), new String(res.getBody().readAllBytes()))));
    }

    public ResponseEntity<byte[]> getArquivo(String caminho, Map<String, Object> parametros) {
        return chamar(() -> http.get().uri(b -> montar(b, caminho, parametros))
                .header(CorrelacaoFilter.CABECALHO, MDC.get(CorrelacaoFilter.MDC_CHAVE))
                .exchange((req, res) -> {
                    byte[] bytes = res.getBody().readAllBytes();
                    HttpHeaders h = new HttpHeaders();
                    if (res.getHeaders().getContentType() != null) {
                        h.setContentType(res.getHeaders().getContentType());
                    }
                    return new ResponseEntity<>(bytes, h, res.getStatusCode());
                }));
    }

    private static java.net.URI montar(UriBuilder b, String caminho, Map<String, Object> parametros) {
        UriBuilder ub = b.path(caminho);
        parametros.forEach(ub::queryParam);
        return ub.build();
    }

    private interface Chamada<T> {
        T executar() throws IOException;
    }

    private <T> T chamar(Chamada<T> chamada) {
        try {
            return chamada.executar();
        } catch (ResourceAccessException | IOException e) {
            log.error("processamento_indisponivel erro={}", e.getClass().getSimpleName());
            throw new ErroNegocio(HttpStatus.SERVICE_UNAVAILABLE, "Serviço indisponível",
                    "O serviço de processamento não respondeu. Tente de novo em instantes.");
        }
    }
}
