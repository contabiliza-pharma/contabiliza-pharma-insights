package br.com.contabilizapharma.relatorios.relatorios;

import br.com.contabilizapharma.relatorios.core.auditoria.Auditoria;
import br.com.contabilizapharma.relatorios.core.erro.ErroNegocio;
import br.com.contabilizapharma.relatorios.core.seguranca.ContextoUsuario;
import br.com.contabilizapharma.relatorios.core.seguranca.UsuarioAutenticado;
import br.com.contabilizapharma.relatorios.empresa.Empresa;
import br.com.contabilizapharma.relatorios.empresa.EmpresaRepository;
import br.com.contabilizapharma.relatorios.processamento.ProcessamentoClient;
import jakarta.servlet.http.HttpServletRequest;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Painel do cliente (EP04). O id da empresa NUNCA vem do cliente: é o da sessão (RNF02, Guia §6.11).
 * O administrador pode visualizar o painel de uma empresa informando ?empresaId= (registrado na auditoria).
 */
@RestController
@RequestMapping("/api/v1/relatorios")
@PreAuthorize("hasAnyRole('CLIENTE', 'ADMINISTRADOR')")
public class RelatorioController {

    private static final Set<String> TIPOS = Set.of("faturamento", "folha", "icms");
    static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ProcessamentoClient processamento;
    private final EmpresaRepository empresas;
    private final Auditoria auditoria;

    public RelatorioController(ProcessamentoClient processamento, EmpresaRepository empresas, Auditoria auditoria) {
        this.processamento = processamento;
        this.empresas = empresas;
        this.auditoria = auditoria;
    }

    @GetMapping("/painel")
    public ResponseEntity<String> painel(@RequestParam(required = false) Integer empresaId, HttpServletRequest req) {
        Empresa e = empresaPermitida(empresaId, req);
        return json(processamento.getJson("/internal/relatorios/painel", Map.of("empresa_id", e.getId())));
    }

    @GetMapping("/{tipo}/resumo")
    public ResponseEntity<String> resumo(@PathVariable String tipo, @RequestParam(required = false) Integer empresaId,
                                         HttpServletRequest req) {
        validarTipo(tipo);
        Empresa e = empresaPermitida(empresaId, req);
        return json(processamento.getJson("/internal/relatorios/" + tipo + "/resumo", Map.of("empresa_id", e.getId())));
    }

    /** RNF09 / Guia §5.6: attachment, nome sem espaços nem acentos e MIME correto. */
    @GetMapping("/{tipo}/xlsx")
    public ResponseEntity<byte[]> xlsx(@PathVariable String tipo, @RequestParam(required = false) Integer empresaId,
                                       HttpServletRequest req) {
        validarTipo(tipo);
        UsuarioAutenticado u = ContextoUsuario.atual();
        Empresa e = empresaPermitida(empresaId, req);
        ResponseEntity<byte[]> r = processamento.getArquivo("/internal/relatorios/" + tipo + "/xlsx",
                Map.of("empresa_id", e.getId()));
        if (!r.getStatusCode().is2xxSuccessful()) {
            throw new ErroNegocio(HttpStatus.valueOf(r.getStatusCode().value() == 404 ? 404 : 502),
                    "Relatório indisponível", r.getStatusCode().value() == 404
                    ? "Ainda não há dados para este relatório."
                    : "Não foi possível preparar o arquivo. Tente de novo.");
        }
        auditoria.registrar(u.id(), "exportar_relatorio", tipo + " empresa:" + e.getId(), req.getRemoteAddr());
        String nome = tipo + "_" + slug(e.getRazaoSocial()) + "_" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(MIME_XLSX))
                .body(r.getBody());
    }

    private Empresa empresaPermitida(Integer empresaIdInformado, HttpServletRequest req) {
        UsuarioAutenticado u = ContextoUsuario.atual();
        if (u.ehCliente()) {
            if (empresaIdInformado != null && !empresaIdInformado.equals(u.empresaId())) {
                // Tentativa de acessar outra empresa: 404 e auditoria (Guia §6.11)
                auditoria.registrar(u.id(), "acesso_negado", "empresa:" + empresaIdInformado, req.getRemoteAddr());
                throw ErroNegocio.naoEncontrado("Relatório não encontrado.");
            }
            Empresa e = empresas.findById(u.empresaId())
                    .orElseThrow(() -> ErroNegocio.naoEncontrado("Relatório não encontrado."));
            if (!e.isAtiva()) {
                throw ErroNegocio.proibido("O acesso desta empresa está suspenso. Fale com a contabilidade.");
            }
            return e;
        }
        if (empresaIdInformado == null) {
            throw ErroNegocio.invalido("Escolha a empresa para visualizar o painel.");
        }
        Empresa e = empresas.findById(empresaIdInformado)
                .orElseThrow(() -> ErroNegocio.naoEncontrado("Empresa não encontrada."));
        auditoria.registrar(u.id(), "visualizar_painel_cliente", "empresa:" + e.getId(), req.getRemoteAddr());
        return e;
    }

    private static void validarTipo(String tipo) {
        if (!TIPOS.contains(tipo)) {
            throw ErroNegocio.naoEncontrado("Relatório inexistente.");
        }
    }

    private static ResponseEntity<String> json(ProcessamentoClient.Resposta r) {
        if (r.status() >= 500) {
            throw new ErroNegocio(HttpStatus.BAD_GATEWAY, "Falha ao carregar",
                    "Não foi possível carregar o relatório. Tente de novo.");
        }
        return ResponseEntity.status(r.status())
                .contentType(r.sucesso() ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_PROBLEM_JSON)
                .body(r.corpo());
    }

    static String slug(String texto) {
        String s = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        return s.isEmpty() ? "empresa" : s.substring(0, Math.min(s.length(), 40));
    }
}
