package br.com.contabilizapharma.relatorios.core.seguranca;

import br.com.contabilizapharma.relatorios.core.erro.ErroNegocio;
import org.springframework.http.HttpStatus;

public class ErroLimiteTaxa extends ErroNegocio {
    private final long segundosParaTentar;

    public ErroLimiteTaxa(String detalhe, long segundosParaTentar) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas", detalhe);
        this.segundosParaTentar = segundosParaTentar;
    }

    public long segundosParaTentar() { return segundosParaTentar; }
}
