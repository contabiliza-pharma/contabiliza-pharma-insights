package br.com.contabilizapharma.relatorios.importacao;

import br.com.contabilizapharma.relatorios.core.auditoria.Auditoria;
import br.com.contabilizapharma.relatorios.core.erro.ErroNegocio;
import br.com.contabilizapharma.relatorios.core.seguranca.UsuarioAutenticado;
import br.com.contabilizapharma.relatorios.processamento.ProcessamentoClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * EP03 — importação. Aqui só ficam autorização, limites e auditoria; a leitura dos arquivos
 * (SPED, PGDAS-D, planilhas do Domínio Folha) e a gravação dos dados são do microsserviço Python.
 */
@Service
public class ImportacaoService {

    private static final long MB = 1024L * 1024L;

    /** Limites por tipo (Guia §6.9) e extensões aceitas. */
    enum Tipo {
        sped(50 * MB, Set.of("txt")),
        pgdas(10 * MB, Set.of("pdf")),
        folha(20 * MB, Set.of("xlsx", "xls", "csv")),
        encargos(20 * MB, Set.of("xlsx", "xls", "csv"));

        final long limite;
        final Set<String> extensoes;

        Tipo(long limite, Set<String> extensoes) {
            this.limite = limite;
            this.extensoes = extensoes;
        }
    }

    private final ProcessamentoClient processamento;
    private final Auditoria auditoria;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ImportacaoService(ProcessamentoClient processamento, Auditoria auditoria,
                             JdbcTemplate jdbc, ObjectMapper mapper) {
        this.processamento = processamento;
        this.auditoria = auditoria;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public ResponseEntity<String> importar(String tipoInformado, MultipartFile arquivo, boolean substituir,
                                           UsuarioAutenticado usuario, String ip) {
        Tipo tipo = tipo(tipoInformado);
        if (arquivo == null || arquivo.isEmpty()) {
            throw ErroNegocio.invalido("Selecione um arquivo.");
        }
        String nome = nomeSeguro(arquivo.getOriginalFilename());
        String ext = nome.contains(".") ? nome.substring(nome.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!tipo.extensoes.contains(ext)) {
            throw ErroNegocio.invalido("Formato não aceito para " + tipoInformado + ". Use: " + String.join(", ", tipo.extensoes) + ".");
        }
        if (arquivo.getSize() > tipo.limite) {
            throw new ErroNegocio(HttpStatus.PAYLOAD_TOO_LARGE, "Arquivo muito grande",
                    "O limite para este tipo é de " + tipo.limite / MB + " MB.");
        }
        // Sem limite de quantidade: o colaborador pode enviar o lote do mês inteiro de uma vez.
        // Arquivos repetidos não são regravados (hash SHA-256) e o Nginx limita a taxa por IP.

        ProcessamentoClient.Resposta r = processamento.importar(tipo.name(), arquivo.getResource(), nome,
                usuario.id(), substituir);
        if (r.status() >= 500) {
            auditoria.registrar(usuario.id(), "falha_importar", tipo.name() + ":" + nome, ip);
            throw new ErroNegocio(HttpStatus.BAD_GATEWAY, "Falha ao importar",
                    "Não foi possível processar o arquivo. Tente de novo; se persistir, informe a equipe.");
        }
        auditar(r, tipo, nome, usuario, ip);
        return ResponseEntity.status(r.status())
                .contentType(r.sucesso() ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_PROBLEM_JSON)
                .body(r.corpo());
    }

    public List<Map<String, Object>> historico(int limite) {
        return jdbc.queryForList("""
                SELECT i.id, i.tipo::text AS tipo, i.arquivo_nome, i.competencia, i.status::text AS status,
                       i.alertas, i.importado_em, u.nome AS importado_por,
                       s.cnpj, coalesce(s.nome, e.razao_social) AS estabelecimento
                  FROM importacao i
                  JOIN usuario u ON u.id = i.importado_por
                  LEFT JOIN estabelecimento s ON s.id = i.estabelecimento_id
                  LEFT JOIN empresa e ON e.id = s.empresa_id
                 ORDER BY i.importado_em DESC LIMIT ?
                """, Math.min(Math.max(limite, 1), 200));
    }

    private void auditar(ProcessamentoClient.Resposta r, Tipo tipo, String nome, UsuarioAutenticado u, String ip) {
        if (!r.sucesso()) {
            auditoria.registrar(u.id(), "importacao_recusada", tipo.name() + ":" + nome, ip);
            return;
        }
        try {
            JsonNode json = mapper.readTree(r.corpo());
            String alvo = "importacao:" + json.path("importacao_id").asText("?") + " " + tipo.name();
            auditoria.registrar(u.id(), json.path("duplicada").asBoolean(false) ? "importar_repetido" : "importar", alvo, ip);
            if (json.path("substituiu").asBoolean(false)) {
                auditoria.registrar(u.id(), "substituir_competencia", alvo, ip);
            }
        } catch (Exception e) {
            auditoria.registrar(u.id(), "importar", tipo.name() + ":" + nome, ip);
        }
    }

    private static Tipo tipo(String valor) {
        try {
            return Tipo.valueOf(valor);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ErroNegocio.naoEncontrado("Tipo de importação inexistente.");
        }
    }

    /** O nome original nunca vira caminho no disco (Guia §6.1); só é guardado para exibição. */
    static String nomeSeguro(String original) {
        String nome = original == null ? "arquivo" : original.replace('\\', '/');
        nome = nome.substring(nome.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N} ._()-]", "_");
        return nome.isBlank() ? "arquivo" : nome.substring(0, Math.min(nome.length(), 200));
    }
}
