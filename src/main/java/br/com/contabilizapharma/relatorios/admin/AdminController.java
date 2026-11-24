package br.com.contabilizapharma.relatorios.admin;

import br.com.contabilizapharma.relatorios.core.idempotencia.Idempotencia;
import br.com.contabilizapharma.relatorios.core.seguranca.ContextoUsuario;
import br.com.contabilizapharma.relatorios.core.seguranca.UsuarioAutenticado;
import br.com.contabilizapharma.relatorios.empresa.VinculoUsuarioEmpresa;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Painel administrativo (EP02). Só administrador (declarado aqui e no SecurityConfig). */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AdminController {

    private final EmpresaAdminService empresas;
    private final UsuarioAdminService usuarios;
    private final Idempotencia idempotencia;

    public AdminController(EmpresaAdminService empresas, UsuarioAdminService usuarios, Idempotencia idempotencia) {
        this.empresas = empresas;
        this.usuarios = usuarios;
        this.idempotencia = idempotencia;
    }

    // ---------- Empresas ----------
    @GetMapping("/empresas")
    public List<AdminDtos.EmpresaSaida> listarEmpresas(@RequestParam(required = false) String busca) {
        return empresas.listar(busca);
    }

    @GetMapping("/empresas/{id}")
    public AdminDtos.EmpresaSaida empresa(@PathVariable Integer id) {
        return empresas.detalhar(id);
    }

    @PostMapping("/empresas")
    public ResponseEntity<?> criarEmpresa(@Valid @RequestBody AdminDtos.EmpresaEntrada in,
                                          @RequestHeader(value = Idempotencia.CABECALHO, required = false) String chave,
                                          HttpServletRequest req) {
        UsuarioAutenticado admin = ContextoUsuario.atual();
        return idempotencia.executar(chave, admin.id(), "POST /admin/empresas",
                () -> ResponseEntity.status(HttpStatus.CREATED).body(empresas.criar(in, admin.id(), req.getRemoteAddr())),
                corpo -> corpo);
    }

    @PutMapping("/empresas/{id}")
    public AdminDtos.EmpresaSaida atualizarEmpresa(@PathVariable Integer id,
                                                   @Valid @RequestBody AdminDtos.EmpresaAtualizacao in,
                                                   HttpServletRequest req) {
        return empresas.atualizar(id, in, ContextoUsuario.atual().id(), req.getRemoteAddr());
    }

    @PostMapping("/empresas/{id}/estabelecimentos")
    public ResponseEntity<AdminDtos.EmpresaSaida> adicionarEstabelecimento(
            @PathVariable Integer id, @Valid @RequestBody AdminDtos.EstabelecimentoEntrada in, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(empresas.adicionarEstabelecimento(id, in, ContextoUsuario.atual().id(), req.getRemoteAddr()));
    }

    @PutMapping("/empresas/{id}/estabelecimentos/{estabId}")
    public AdminDtos.EmpresaSaida atualizarEstabelecimento(
            @PathVariable Integer id, @PathVariable Integer estabId,
            @Valid @RequestBody AdminDtos.EstabelecimentoAtualizacao in, HttpServletRequest req) {
        return empresas.atualizarEstabelecimento(id, estabId, in, ContextoUsuario.atual().id(), req.getRemoteAddr());
    }

    @GetMapping("/empresas/{id}/usuarios")
    public List<VinculoUsuarioEmpresa.UsuarioVinculado> usuariosDaEmpresa(@PathVariable Integer id) {
        return empresas.usuarios(id);
    }

    @PostMapping("/empresas/{id}/usuarios")
    public List<VinculoUsuarioEmpresa.UsuarioVinculado> vincularUsuario(
            @PathVariable Integer id, @Valid @RequestBody AdminDtos.VincularUsuarioEntrada in, HttpServletRequest req) {
        return empresas.vincularUsuario(id, in.usuarioId(), ContextoUsuario.atual().id(), req.getRemoteAddr());
    }

    @DeleteMapping("/empresas/{id}/usuarios/{usuarioId}")
    public List<VinculoUsuarioEmpresa.UsuarioVinculado> desvincularUsuario(
            @PathVariable Integer id, @PathVariable Integer usuarioId, HttpServletRequest req) {
        return empresas.desvincularUsuario(id, usuarioId, ContextoUsuario.atual().id(), req.getRemoteAddr());
    }

    // ---------- Usuários ----------
    @GetMapping("/usuarios")
    public List<AdminDtos.UsuarioSaida> listarUsuarios(@RequestParam(required = false) String busca,
                                                       @RequestParam(required = false) String situacao) {
        return usuarios.listar(busca, situacao);
    }

    @PostMapping("/usuarios")
    public ResponseEntity<?> criarUsuario(@Valid @RequestBody AdminDtos.UsuarioEntrada in,
                                          @RequestHeader(value = Idempotencia.CABECALHO, required = false) String chave,
                                          HttpServletRequest req) {
        UsuarioAutenticado admin = ContextoUsuario.atual();
        // Ao repetir a mesma chave, a resposta guardada NÃO traz a senha temporária (nunca persistida)
        return idempotencia.executar(chave, admin.id(), "POST /admin/usuarios",
                () -> ResponseEntity.status(HttpStatus.CREATED).body(usuarios.criar(in, admin.id(), req.getRemoteAddr())),
                corpo -> Map.of("usuario", corpo.usuario(), "senhaTemporaria", "",
                        "aviso", "Conta já criada por esta solicitação. Se não anotou a senha, use Redefinir senha."));
    }

    @PutMapping("/usuarios/{id}/empresas")
    public AdminDtos.UsuarioSaida definirEmpresas(@PathVariable Integer id,
                                                  @Valid @RequestBody AdminDtos.EmpresasDoUsuarioEntrada in,
                                                  HttpServletRequest req) {
        return usuarios.definirEmpresas(id, in.empresaIds(), ContextoUsuario.atual().id(), req.getRemoteAddr());
    }

    @PostMapping("/usuarios/{id}/redefinir-senha")
    public AdminDtos.SenhaTemporariaSaida redefinirSenha(@PathVariable Integer id, HttpServletRequest req) {
        return usuarios.redefinirSenha(id, ContextoUsuario.atual().id(), req.getRemoteAddr());
    }

    @PostMapping("/usuarios/{id}/bloquear")
    public AdminDtos.UsuarioSaida bloquear(@PathVariable Integer id, HttpServletRequest req) {
        return usuarios.bloquear(id, ContextoUsuario.atual().id(), req.getRemoteAddr());
    }

    @PostMapping("/usuarios/{id}/desbloquear")
    public AdminDtos.UsuarioSaida desbloquear(@PathVariable Integer id, HttpServletRequest req) {
        return usuarios.desbloquear(id, ContextoUsuario.atual().id(), req.getRemoteAddr());
    }

    @PostMapping("/usuarios/{id}/desativar")
    public AdminDtos.UsuarioSaida desativar(@PathVariable Integer id, HttpServletRequest req) {
        return usuarios.definirAtivo(id, false, ContextoUsuario.atual().id(), req.getRemoteAddr());
    }

    @PostMapping("/usuarios/{id}/reativar")
    public AdminDtos.UsuarioSaida reativar(@PathVariable Integer id, HttpServletRequest req) {
        return usuarios.definirAtivo(id, true, ContextoUsuario.atual().id(), req.getRemoteAddr());
    }
}
