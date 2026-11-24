package br.com.contabilizapharma.relatorios.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginEntrada(
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 150) String email,
            @NotBlank(message = "Informe a senha.") @Size(max = 128) String senha) {}

    public record TrocarSenhaEntrada(
            @NotBlank(message = "Informe a senha atual.") @Size(max = 128) String senhaAtual,
            @NotBlank(message = "Informe a nova senha.") @Size(max = 128) String novaSenha,
            @NotBlank(message = "Confirme a nova senha.") @Size(max = 128) String confirmacao) {}

    public record UsuarioSaida(
            Integer id, String nome, String email, String perfil,
            boolean senhaTemporaria, EmpresaResumo empresa) {}

    public record EmpresaResumo(Integer id, String razaoSocial, String cnpj) {}

    public record CsrfSaida(String headerName, String token) {}
}
