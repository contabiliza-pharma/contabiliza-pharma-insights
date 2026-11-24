package br.com.contabilizapharma.relatorios.core.idempotencia;

import br.com.contabilizapharma.relatorios.core.erro.ErroNegocio;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cabeçalho Idempotency-Key em POSTs de criação (Guia §6.14): repetir a mesma chave em 24 h
 * devolve a resposta guardada em vez de criar de novo. O corpo guardado passa por um
 * "sanitizador" para nunca persistir senhas temporárias.
 */
@Component
public class Idempotencia {

    public static final String CABECALHO = "Idempotency-Key";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public Idempotencia(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public <T> ResponseEntity<?> executar(String chave, Integer usuarioId, String rota,
                                          Supplier<ResponseEntity<T>> acao, Function<T, Object> sanitizador) {
        if (chave == null || chave.isBlank()) {
            return acao.get();
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(chave.trim());
        } catch (IllegalArgumentException e) {
            throw ErroNegocio.invalido("Idempotency-Key deve ser um UUID.");
        }
        List<ResponseEntity<String>> anteriores = jdbc.query("""
                SELECT resposta_status, resposta_corpo FROM chave_idempotencia
                 WHERE chave = ? AND usuario_id = ? AND rota = ? AND criado_em > now() - interval '24 hours'
                """, (rs, i) -> ResponseEntity.status(rs.getInt(1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotent-Replay", "true")
                        .body(rs.getString(2)), uuid, usuarioId, rota);
        if (!anteriores.isEmpty()) {
            return anteriores.get(0);
        }
        ResponseEntity<T> resposta = acao.get();
        try {
            String corpo = mapper.writeValueAsString(sanitizador.apply(resposta.getBody()));
            jdbc.update("""
                    INSERT INTO chave_idempotencia (chave, usuario_id, rota, resposta_status, resposta_corpo)
                    VALUES (?, ?, ?, ?, ?) ON CONFLICT (chave) DO NOTHING
                    """, uuid, usuarioId, rota, resposta.getStatusCode().value(), corpo);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return resposta;
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void limparAntigas() {
        jdbc.update("DELETE FROM chave_idempotencia WHERE criado_em < now() - interval '24 hours'");
    }

    public static ErroNegocio conflitoReplay() {
        return new ErroNegocio(HttpStatus.CONFLICT, "Já processado", "Esta solicitação já foi processada.");
    }
}
