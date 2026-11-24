package br.com.contabilizapharma.relatorios.core.erro;

import br.com.contabilizapharma.relatorios.core.seguranca.ErroLimiteTaxa;
import br.com.contabilizapharma.relatorios.core.web.CorrelacaoFilter;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Converte toda exceção em Problem Details (RFC 9457), sem vazar detalhes internos (Guia §6.17). */
@RestControllerAdvice
public class TratadorErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorErros.class);

    @ExceptionHandler(ErroNegocio.class)
    ProblemDetail negocio(ErroNegocio e) {
        return problema(e.status(), e.titulo(), e.getMessage());
    }

    @ExceptionHandler(ErroLimiteTaxa.class)
    ResponseEntity<ProblemDetail> limite(ErroLimiteTaxa e) {
        return ResponseEntity.status(e.status())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.segundosParaTentar()))
                .body(problema(e.status(), e.titulo(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacao(MethodArgumentNotValidException e) {
        ProblemDetail p = problema(HttpStatus.UNPROCESSABLE_ENTITY, "Requisição inválida",
                "Confira os campos destacados.");
        p.setProperty("campos", e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(),
                        f -> f.getDefaultMessage() == null ? "Valor inválido." : f.getDefaultMessage(),
                        (a, b) -> a)));
        return p;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, ConstraintViolationException.class})
    ProblemDetail malformada(Exception e) {
        return problema(HttpStatus.BAD_REQUEST, "Requisição inválida",
                "O conteúdo enviado não está no formato esperado.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail arquivoGrande(MaxUploadSizeExceededException e) {
        return problema(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo muito grande",
                "O arquivo passa do limite permitido. Envie um arquivo menor.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concorrencia(OptimisticLockingFailureException e) {
        return problema(HttpStatus.CONFLICT, "Registro alterado",
                "Outra pessoa alterou este registro. Recarregue a página e tente de novo.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integridade(DataIntegrityViolationException e) {
        log.warn("violacao_integridade", e);
        return problema(HttpStatus.CONFLICT, "Conflito",
                "Os dados conflitam com um registro existente. Confira e tente de novo.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail negado(AccessDeniedException e) {
        return problema(HttpStatus.FORBIDDEN, "Acesso negado", "Você não tem permissão para esta ação.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail rotaInexistente(NoResourceFoundException e) {
        log.warn("rota_inexistente metodo={} caminho=/{}", e.getHttpMethod(), e.getResourcePath());
        return problema(HttpStatus.NOT_FOUND, "Não encontrado", "O recurso solicitado não existe.");
    }

    /** Rota existe, mas não para este método (ex.: GET num endereço que só aceita POST). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> metodoNaoPermitido(HttpRequestMethodNotSupportedException e) {
        log.warn("metodo_nao_permitido metodo={} aceitos={}", e.getMethod(), e.getSupportedHttpMethods());
        ResponseEntity.BodyBuilder r = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (e.getSupportedHttpMethods() != null) {
            r.allow(e.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new));
        }
        return r.body(problema(HttpStatus.METHOD_NOT_ALLOWED, "Método não permitido",
                "Este endereço não aceita " + e.getMethod() + ". Use a tela do sistema para esta ação."));
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception e) {
        log.error("erro_nao_tratado", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Algo deu errado do nosso lado. Tente de novo em instantes; se persistir, informe o código à equipe.");
    }

    public static ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detalhe);
        p.setTitle(titulo);
        p.setProperty("correlation_id", MDC.get(CorrelacaoFilter.MDC_CHAVE));
        return p;
    }
}
