package br.com.contabilizapharma.relatorios.usuario;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 150, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Perfil perfil;

    @Column(name = "empresa_id")
    private Integer empresaId;

    @Column(name = "senha_temporaria", nullable = false)
    private boolean senhaTemporaria = true;

    @Column(name = "senha_expira_em")
    private LocalDateTime senhaExpiraEm;

    @Column(name = "tentativas_falhas", nullable = false)
    private short tentativasFalhas;

    @Column(name = "bloqueado_ate")
    private LocalDateTime bloqueadoAte;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "ultimo_acesso")
    private LocalDateTime ultimoAcesso;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Version
    private Integer versao;

    protected Usuario() {}

    public Usuario(String nome, String email, String senhaHash, Perfil perfil, Integer empresaId) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
        this.empresaId = empresaId;
    }

    public boolean estaBloqueado(LocalDateTime agora) {
        return bloqueadoAte != null && bloqueadoAte.isAfter(agora);
    }

    public boolean senhaTemporariaExpirada(LocalDateTime agora) {
        return senhaTemporaria && senhaExpiraEm != null && senhaExpiraEm.isBefore(agora);
    }

    public void definirSenhaTemporaria(String hash, LocalDateTime expiraEm) {
        this.senhaHash = hash;
        this.senhaTemporaria = true;
        this.senhaExpiraEm = expiraEm;
        this.tentativasFalhas = 0;
        this.bloqueadoAte = null;
    }

    public void definirSenhaDefinitiva(String hash) {
        this.senhaHash = hash;
        this.senhaTemporaria = false;
        this.senhaExpiraEm = null;
    }

    public void registrarAcesso(LocalDateTime agora) {
        this.ultimoAcesso = agora;
        this.tentativasFalhas = 0;
        this.bloqueadoAte = null;
    }

    public Integer getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }
    public Perfil getPerfil() { return perfil; }
    public Integer getEmpresaId() { return empresaId; }
    public boolean isSenhaTemporaria() { return senhaTemporaria; }
    public LocalDateTime getSenhaExpiraEm() { return senhaExpiraEm; }
    public short getTentativasFalhas() { return tentativasFalhas; }
    public LocalDateTime getBloqueadoAte() { return bloqueadoAte; }
    public void setBloqueadoAte(LocalDateTime bloqueadoAte) { this.bloqueadoAte = bloqueadoAte; }
    public void zerarTentativas() { this.tentativasFalhas = 0; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public LocalDateTime getUltimoAcesso() { return ultimoAcesso; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public Integer getVersao() { return versao; }
}
