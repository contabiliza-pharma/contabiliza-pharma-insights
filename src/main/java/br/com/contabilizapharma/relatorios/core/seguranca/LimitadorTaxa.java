package br.com.contabilizapharma.relatorios.core.seguranca;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Rate limit de janela fixa no Redis (Guia §6.7). Também é reforçado no Nginx (limit_req). */
@Component
public class LimitadorTaxa {

    private static final Logger log = LoggerFactory.getLogger(LimitadorTaxa.class);
    private final StringRedisTemplate redis;

    public LimitadorTaxa(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Conta mais uma chamada; lança 429 se passar do limite na janela. */
    public void consumir(String chave, int limite, Duration janela, String mensagem) {
        String k = "relatorios:limite:" + chave + ":" + janela.toSeconds();
        Long atual = redis.opsForValue().increment(k);
        if (atual != null && atual == 1L) {
            redis.expire(k, janela);
        }
        if (atual != null && atual > limite) {
            Long ttl = redis.getExpire(k);
            log.warn("limite_taxa_excedido chave={}", chave.replaceAll("@.*", "@***"));
            throw new ErroLimiteTaxa(mensagem, ttl == null || ttl < 1 ? janela.toSeconds() : ttl);
        }
    }
}
