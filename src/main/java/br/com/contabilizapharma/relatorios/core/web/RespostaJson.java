package br.com.contabilizapharma.relatorios.core.web;

import br.com.contabilizapharma.relatorios.core.erro.TratadorErros;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Escreve Problem Details direto na resposta (usado pelos filtros, fora dos controllers). */
@Component
public class RespostaJson {

    private final ObjectMapper mapper;

    public RespostaJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void problema(HttpServletResponse res, HttpStatus status, String titulo, String detalhe)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), TratadorErros.problema(status, titulo, detalhe));
    }
}
