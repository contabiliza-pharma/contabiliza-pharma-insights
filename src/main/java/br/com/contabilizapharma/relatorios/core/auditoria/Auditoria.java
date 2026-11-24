package br.com.contabilizapharma.relatorios.core.auditoria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Trilha de auditoria de negócio (tabela log_acao, só inserção — Guia §7.2).
 * Nunca registrar senhas, tokens ou conteúdo de arquivos.
 */
@Component
public class Auditoria {

    private static final Logger log = LoggerFactory.getLogger(Auditoria.class);
    private static final int TAMANHO_ALVO = 150;
    private final JdbcTemplate jdbc;

    public Auditoria(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrar(Integer usuarioId, String acao, String alvo, String ip) {
        String alvoCurto = alvo == null ? null : alvo.substring(0, Math.min(alvo.length(), TAMANHO_ALVO));
        jdbc.update("INSERT INTO log_acao (usuario_id, acao, alvo, ip) VALUES (?, ?, ?, ?)",
                usuarioId, acao, alvoCurto, ip);
        log.info("auditoria acao={} usuario_id={} alvo={}", acao, usuarioId, alvoCurto);
    }

    /** E-mail mascarado para logs: f***@dominio.com.br. */
    public static String mascararEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int arroba = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(arroba);
    }
}
