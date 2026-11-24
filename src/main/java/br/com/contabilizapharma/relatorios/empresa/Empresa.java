package br.com.contabilizapharma.relatorios.empresa;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "empresa")
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "raiz_cnpj", nullable = false, columnDefinition = "char(8)")
    private String raizCnpj;

    @Column(name = "razao_social", nullable = false, length = 150)
    private String razaoSocial;

    @Enumerated(EnumType.STRING)
    private Regime regime;

    @Column(nullable = false)
    private boolean ativa = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Version
    private Integer versao;

    protected Empresa() {}

    public Empresa(String raizCnpj, String razaoSocial, Regime regime) {
        this.raizCnpj = raizCnpj;
        this.razaoSocial = razaoSocial;
        this.regime = regime;
    }

    public Integer getId() { return id; }
    public String getRaizCnpj() { return raizCnpj; }
    public String getRazaoSocial() { return razaoSocial; }
    public void setRazaoSocial(String razaoSocial) { this.razaoSocial = razaoSocial; }
    public Regime getRegime() { return regime; }
    public void setRegime(Regime regime) { this.regime = regime; }
    public boolean isAtiva() { return ativa; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public Integer getVersao() { return versao; }
}
