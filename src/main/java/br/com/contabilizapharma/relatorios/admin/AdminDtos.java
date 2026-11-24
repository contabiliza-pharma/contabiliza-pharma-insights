package br.com.contabilizapharma.relatorios.admin;

import br.com.contabilizapharma.relatorios.empresa.Regime;
import br.com.contabilizapharma.relatorios.usuario.Perfil;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {}

    // ---------- Empresas ----------
    public record EmpresaEntrada(
            @NotBlank(message = "Informe o CNPJ.") @Size(max = 18) String cnpj,
            @NotBlank(message = "Informe a razão social.") @Size(max = 150) String razaoSocial,
            Regime regime,
            @Size(max = 150) String nomeEstabelecimento,
            @Size(max = 14) @Pattern(regexp = "^[0-9A-Za-z]*$", message = "IE só com letras e números.") String ie,
            @Pattern(regexp = "^([A-Za-z]{2})?$", message = "UF com 2 letras.") String uf,
            @Positive(message = "Código do Domínio deve ser positivo.") Integer codigoDominio) {}

    public record EmpresaAtualizacao(
            @NotBlank @Size(max = 150) String razaoSocial,
            Regime regime,
            @NotNull Boolean ativa,
            @NotNull Integer versao) {}

    public record EstabelecimentoEntrada(
            @NotBlank(message = "Informe o CNPJ.") @Size(max = 18) String cnpj,
            @Size(max = 150) String nome,
            @Size(max = 14) @Pattern(regexp = "^[0-9A-Za-z]*$", message = "IE só com letras e números.") String ie,
            @Pattern(regexp = "^([A-Za-z]{2})?$", message = "UF com 2 letras.") String uf,
            @Positive(message = "Código do Domínio deve ser positivo.") Integer codigoDominio) {}

    public record EstabelecimentoAtualizacao(
            @Size(max = 150) String nome,
            @Size(max = 14) @Pattern(regexp = "^[0-9A-Za-z]*$") String ie,
            @Pattern(regexp = "^([A-Za-z]{2})?$") String uf,
            @Positive Integer codigoDominio,
            @NotNull Boolean ativo,
            @NotNull Integer versao) {}

    public record EstabelecimentoSaida(Integer id, String cnpj, String cnpjFormatado, String nome, String ie,
                                       String uf, Integer codigoDominio, boolean matriz, boolean ativo,
                                       Integer versao) {}

    public record EmpresaSaida(Integer id, String raizCnpj, String razaoSocial, String regime, boolean ativa,
                               LocalDateTime criadoEm, Integer versao, List<EstabelecimentoSaida> estabelecimentos,
                               long usuarios) {}

    // ---------- Usuários ----------
    public record UsuarioEntrada(
            @NotBlank(message = "Informe o nome.") @Size(max = 150) String nome,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 150) String email,
            @NotNull(message = "Escolha o perfil.") Perfil perfil,
            Integer empresaId) {}

    public record UsuarioSaida(Integer id, String nome, String email, String perfil, Integer empresaId,
                               String empresa, String situacao, boolean senhaTemporaria,
                               LocalDateTime senhaExpiraEm, LocalDateTime bloqueadoAte,
                               LocalDateTime ultimoAcesso, LocalDateTime criadoEm) {}

    /** A senha temporária aparece UMA vez, nesta resposta (US008); nunca é guardada em texto. */
    public record SenhaTemporariaSaida(UsuarioSaida usuario, String senhaTemporaria, LocalDateTime expiraEm,
                                       boolean emailEnviado) {}
}
