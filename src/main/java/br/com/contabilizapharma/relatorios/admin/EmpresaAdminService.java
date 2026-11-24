package br.com.contabilizapharma.relatorios.admin;

import br.com.contabilizapharma.relatorios.core.auditoria.Auditoria;
import br.com.contabilizapharma.relatorios.core.erro.ErroNegocio;
import br.com.contabilizapharma.relatorios.empresa.*;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** US007 (cadastrar empresa pelo CNPJ) e US011 (listar e buscar). Filiais agrupadas pela raiz. */
@Service
public class EmpresaAdminService {

    private static final int LIMITE_LISTA = 200;
    private final EmpresaRepository empresas;
    private final EstabelecimentoRepository estabelecimentos;
    private final JdbcTemplate jdbc;
    private final Auditoria auditoria;

    public EmpresaAdminService(EmpresaRepository empresas, EstabelecimentoRepository estabelecimentos,
                               JdbcTemplate jdbc, Auditoria auditoria) {
        this.empresas = empresas;
        this.estabelecimentos = estabelecimentos;
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.EmpresaSaida> listar(String busca) {
        String termo = busca == null ? "" : busca.trim();
        String digitos = Cnpj.somenteDigitos(termo);
        List<Integer> ids = jdbc.queryForList("""
                SELECT e.id FROM empresa e
                 WHERE ? = ''
                    OR e.razao_social ILIKE ? ESCAPE '\\'
                    OR (? <> '' AND EXISTS (SELECT 1 FROM estabelecimento s
                                             WHERE s.empresa_id = e.id AND s.cnpj LIKE ?))
                 ORDER BY e.razao_social LIMIT ?
                """, Integer.class, termo, "%" + escaparLike(termo) + "%", digitos, digitos + "%", LIMITE_LISTA);
        return ids.stream().map(this::detalhar).toList();
    }

    @Transactional(readOnly = true)
    public AdminDtos.EmpresaSaida detalhar(Integer id) {
        Empresa e = empresas.findById(id).orElseThrow(() -> ErroNegocio.naoEncontrado("Empresa não encontrada."));
        List<AdminDtos.EstabelecimentoSaida> estabs = estabelecimentos.findByEmpresaIdOrderByMatrizDescCnpjAsc(id)
                .stream().map(EmpresaAdminService::saida).toList();
        Long qtdUsuarios = jdbc.queryForObject("SELECT count(*) FROM usuario WHERE empresa_id = ?", Long.class, id);
        return new AdminDtos.EmpresaSaida(e.getId(), e.getRaizCnpj(), e.getRazaoSocial(),
                e.getRegime() == null ? null : e.getRegime().name(), e.isAtiva(), e.getCriadoEm(), e.getVersao(),
                estabs, qtdUsuarios == null ? 0 : qtdUsuarios);
    }

    @Transactional
    public AdminDtos.EmpresaSaida criar(AdminDtos.EmpresaEntrada in, Integer adminId, String ip) {
        String cnpj = validarCnpjNovo(in.cnpj(), in.codigoDominio());
        String raiz = Cnpj.raiz(cnpj);
        if (empresas.findByRaizCnpj(raiz).isPresent()) {
            throw ErroNegocio.conflito("Já existe uma empresa com a raiz " + raiz
                    + ". Para uma filial, adicione um estabelecimento nessa empresa.");
        }
        Empresa e = empresas.save(new Empresa(raiz, in.razaoSocial().trim(), in.regime()));
        String nome = in.nomeEstabelecimento() == null || in.nomeEstabelecimento().isBlank()
                ? in.razaoSocial().trim() : in.nomeEstabelecimento().trim();
        estabelecimentos.save(new Estabelecimento(e.getId(), cnpj, nome, vazioParaNulo(in.ie()),
                uf(in.uf()), in.codigoDominio(), Cnpj.ehMatriz(cnpj)));
        auditoria.registrar(adminId, "criar_empresa", "empresa:" + e.getId(), ip);
        return detalhar(e.getId());
    }

    @Transactional
    public AdminDtos.EmpresaSaida atualizar(Integer id, AdminDtos.EmpresaAtualizacao in, Integer adminId, String ip) {
        Empresa e = empresas.findById(id).orElseThrow(() -> ErroNegocio.naoEncontrado("Empresa não encontrada."));
        conferirVersao(e.getVersao(), in.versao());
        e.setRazaoSocial(in.razaoSocial().trim());
        e.setRegime(in.regime());
        e.setAtiva(in.ativa());
        empresas.saveAndFlush(e);
        auditoria.registrar(adminId, "alterar_empresa", "empresa:" + id, ip);
        return detalhar(id);
    }

    @Transactional
    public AdminDtos.EmpresaSaida adicionarEstabelecimento(Integer empresaId, AdminDtos.EstabelecimentoEntrada in,
                                                           Integer adminId, String ip) {
        Empresa e = empresas.findById(empresaId)
                .orElseThrow(() -> ErroNegocio.naoEncontrado("Empresa não encontrada."));
        String cnpj = validarCnpjNovo(in.cnpj(), in.codigoDominio());
        if (!Cnpj.raiz(cnpj).equals(e.getRaizCnpj())) {
            throw ErroNegocio.invalido("O CNPJ não pertence a esta empresa (a raiz deve ser " + e.getRaizCnpj() + ").");
        }
        Estabelecimento s = estabelecimentos.save(new Estabelecimento(empresaId, cnpj,
                vazioParaNulo(in.nome()), vazioParaNulo(in.ie()), uf(in.uf()), in.codigoDominio(), Cnpj.ehMatriz(cnpj)));
        auditoria.registrar(adminId, "criar_estabelecimento", "estabelecimento:" + s.getId(), ip);
        return detalhar(empresaId);
    }

    @Transactional
    public AdminDtos.EmpresaSaida atualizarEstabelecimento(Integer empresaId, Integer estabId,
                                                           AdminDtos.EstabelecimentoAtualizacao in,
                                                           Integer adminId, String ip) {
        Estabelecimento s = estabelecimentos.findById(estabId)
                .filter(x -> x.getEmpresaId().equals(empresaId))
                .orElseThrow(() -> ErroNegocio.naoEncontrado("Estabelecimento não encontrado."));
        conferirVersao(s.getVersao(), in.versao());
        if (in.codigoDominio() != null && !in.codigoDominio().equals(s.getCodigoDominio())
                && estabelecimentos.existsByCodigoDominio(in.codigoDominio())) {
            throw ErroNegocio.conflito("O código do Domínio " + in.codigoDominio() + " já está em outro estabelecimento.");
        }
        s.setNome(vazioParaNulo(in.nome()));
        s.setIe(vazioParaNulo(in.ie()));
        s.setUf(uf(in.uf()));
        s.setCodigoDominio(in.codigoDominio());
        s.setAtivo(in.ativo());
        estabelecimentos.saveAndFlush(s);
        auditoria.registrar(adminId, "alterar_estabelecimento", "estabelecimento:" + estabId, ip);
        return detalhar(empresaId);
    }

    private String validarCnpjNovo(String informado, Integer codigoDominio) {
        String cnpj = Cnpj.somenteDigitos(informado);
        if (!Cnpj.ehValido(cnpj)) {
            throw ErroNegocio.invalido("CNPJ inválido. Confira os 14 dígitos.");
        }
        if (estabelecimentos.existsByCnpj(cnpj)) {
            throw ErroNegocio.conflito("Este CNPJ já está cadastrado.");
        }
        if (codigoDominio != null && estabelecimentos.existsByCodigoDominio(codigoDominio)) {
            throw ErroNegocio.conflito("O código do Domínio " + codigoDominio + " já está em outro estabelecimento.");
        }
        return cnpj;
    }

    /** Lock otimista (Guia §6.15): a versão enviada pela tela precisa ser a atual. */
    private static void conferirVersao(Integer atual, Integer enviada) {
        if (!atual.equals(enviada)) {
            throw new ObjectOptimisticLockingFailureException(Object.class, enviada);
        }
    }

    static AdminDtos.EstabelecimentoSaida saida(Estabelecimento s) {
        return new AdminDtos.EstabelecimentoSaida(s.getId(), s.getCnpj(), Cnpj.formatar(s.getCnpj()), s.getNome(),
                s.getIe(), s.getUf(), s.getCodigoDominio(), s.isMatriz(), s.isAtivo(), s.getVersao());
    }

    private static String uf(String uf) {
        return uf == null || uf.isBlank() ? null : uf.toUpperCase(Locale.ROOT);
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String escaparLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
