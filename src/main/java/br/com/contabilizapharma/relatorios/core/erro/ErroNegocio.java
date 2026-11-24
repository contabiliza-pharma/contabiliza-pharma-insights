package br.com.contabilizapharma.relatorios.core.erro;

import org.springframework.http.HttpStatus;

/**
 * Erro esperado de regra de negócio. O "detail" é mostrado ao usuário,
 * então deve dizer o que houve e o que fazer (heurística 9), sem detalhes internos.
 */
public class ErroNegocio extends RuntimeException {

    private final HttpStatus status;
    private final String titulo;

    public ErroNegocio(HttpStatus status, String titulo, String detalhe) {
        super(detalhe);
        this.status = status;
        this.titulo = titulo;
    }

    public HttpStatus status() { return status; }
    public String titulo() { return titulo; }

    public static ErroNegocio naoEncontrado(String detalhe) {
        return new ErroNegocio(HttpStatus.NOT_FOUND, "Não encontrado", detalhe);
    }

    public static ErroNegocio conflito(String detalhe) {
        return new ErroNegocio(HttpStatus.CONFLICT, "Conflito", detalhe);
    }

    public static ErroNegocio invalido(String detalhe) {
        return new ErroNegocio(HttpStatus.UNPROCESSABLE_ENTITY, "Requisição inválida", detalhe);
    }

    public static ErroNegocio proibido(String detalhe) {
        return new ErroNegocio(HttpStatus.FORBIDDEN, "Acesso negado", detalhe);
    }
}
