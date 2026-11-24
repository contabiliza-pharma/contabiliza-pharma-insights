package br.com.contabilizapharma.relatorios.importacao;

import br.com.contabilizapharma.relatorios.core.seguranca.ContextoUsuario;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** US013, US014, US015, US025, US026 — um arquivo por requisição; a tela envia em sequência e mostra o progresso. */
@RestController
@RequestMapping("/api/v1/importacoes")
@PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COLABORADOR')")
public class ImportacaoController {

    private final ImportacaoService service;

    public ImportacaoController(ImportacaoService service) {
        this.service = service;
    }

    @PostMapping(value = "/{tipo}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> importar(@PathVariable String tipo,
                                           @RequestPart("arquivo") MultipartFile arquivo,
                                           @RequestParam(defaultValue = "false") boolean substituir,
                                           HttpServletRequest req) {
        return service.importar(tipo, arquivo, substituir, ContextoUsuario.atual(), req.getRemoteAddr());
    }

    @GetMapping
    public List<Map<String, Object>> historico(@RequestParam(defaultValue = "50") int limite) {
        return service.historico(limite);
    }
}
