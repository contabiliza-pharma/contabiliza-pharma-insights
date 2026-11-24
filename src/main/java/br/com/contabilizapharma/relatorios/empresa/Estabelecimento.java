package br.com.contabilizapharma.relatorios.empresa;

import jakarta.persistence.*;

@Entity
@Table(name = "estabelecimento")
public class Estabelecimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(nullable = false, columnDefinition = "char(14)")
    private String cnpj;

    @Column(length = 150)
    private String nome;

    @Column(length = 14)
    private String ie;

    @Column(columnDefinition = "char(2)")
    private String uf;

    @Column(name = "codigo_dominio")
    private Integer codigoDominio;

    @Column(nullable = false)
    private boolean matriz;

    @Column(nullable = false)
    private boolean ativo = true;

    @Version
    private Integer versao;

    protected Estabelecimento() {}

    public Estabelecimento(Integer empresaId, String cnpj, String nome, String ie, String uf,
                           Integer codigoDominio, boolean matriz) {
        this.empresaId = empresaId;
        this.cnpj = cnpj;
        this.nome = nome;
        this.ie = ie;
        this.uf = uf;
        this.codigoDominio = codigoDominio;
        this.matriz = matriz;
    }

    public Integer getId() { return id; }
    public Integer getEmpresaId() { return empresaId; }
    public String getCnpj() { return cnpj; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getIe() { return ie; }
    public void setIe(String ie) { this.ie = ie; }
    public String getUf() { return uf; }
    public void setUf(String uf) { this.uf = uf; }
    public Integer getCodigoDominio() { return codigoDominio; }
    public void setCodigoDominio(Integer codigoDominio) { this.codigoDominio = codigoDominio; }
    public boolean isMatriz() { return matriz; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public Integer getVersao() { return versao; }
}
