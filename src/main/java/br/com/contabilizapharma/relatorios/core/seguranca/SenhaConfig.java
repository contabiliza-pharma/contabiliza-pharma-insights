package br.com.contabilizapharma.relatorios.core.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Argon2id (RNF01, Guia §6.13). Os MESMOS parâmetros são usados pelo comando de geração de hash
 * (scripts/gerar-hash-senha.*) e pelo microsserviço Python (argon2-cffi), que gravam a mesma
 * string PHC: $argon2id$v=19$m=65536,t=3,p=4$<sal>$<hash>.
 */
@Configuration
public class SenhaConfig {

    public static final int TAMANHO_SAL = 16;
    public static final int TAMANHO_HASH = 32;
    public static final int PARALELISMO = 4;
    public static final int MEMORIA_KIB = 65_536;
    public static final int ITERACOES = 3;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(TAMANHO_SAL, TAMANHO_HASH, PARALELISMO, MEMORIA_KIB, ITERACOES);
    }
}
