package br.com.contabilizapharma.relatorios.core.seguranca;

import br.com.contabilizapharma.relatorios.core.erro.ErroNegocio;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Regras de senha (US002, Guia §6.13): mínimo 8 caracteres, com letra e número,
 * fora da lista de senhas comuns; colar e frases longas são permitidos.
 */
@Component
public class PoliticaSenha {

    public static final int TAMANHO_MINIMO = 8;
    public static final int TAMANHO_MAXIMO = 128;
    private static final int TAMANHO_TEMPORARIA = 14;
    // Sem caracteres ambíguos (0/O, 1/l/I) para facilitar o repasse da senha temporária
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final Set<String> senhasComuns;

    public PoliticaSenha() {
        this.senhasComuns = carregarSenhasComuns();
    }

    public void validarNova(String nova, String confirmacao, String atual) {
        if (nova == null || nova.length() < TAMANHO_MINIMO || nova.length() > TAMANHO_MAXIMO) {
            throw ErroNegocio.invalido("A nova senha deve ter de 8 a 128 caracteres.");
        }
        if (!nova.chars().anyMatch(Character::isLetter) || !nova.chars().anyMatch(Character::isDigit)) {
            throw ErroNegocio.invalido("A nova senha precisa ter pelo menos uma letra e um número.");
        }
        if (!nova.equals(confirmacao)) {
            throw ErroNegocio.invalido("A confirmação não é igual à nova senha.");
        }
        if (nova.equals(atual)) {
            throw ErroNegocio.invalido("A nova senha precisa ser diferente da atual.");
        }
        if (senhasComuns.contains(nova.toLowerCase(Locale.ROOT))) {
            throw ErroNegocio.invalido("Essa senha é muito comum. Escolha outra, de preferência uma frase.");
        }
    }

    /** Senha temporária com gerador criptográfico (≥ 12 caracteres, com letra e número). */
    public String gerarTemporaria() {
        while (true) {
            StringBuilder sb = new StringBuilder(TAMANHO_TEMPORARIA);
            for (int i = 0; i < TAMANHO_TEMPORARIA; i++) {
                sb.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
            }
            String s = sb.toString();
            if (s.chars().anyMatch(Character::isDigit) && s.chars().anyMatch(Character::isLetter)) {
                return s;
            }
        }
    }

    private static Set<String> carregarSenhasComuns() {
        Set<String> conjunto = new HashSet<>();
        ClassPathResource recurso = new ClassPathResource("seguranca/senhas-comuns.txt");
        if (!recurso.exists()) {
            return conjunto;
        }
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(recurso.getInputStream(), StandardCharsets.UTF_8))) {
            r.lines().map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .map(l -> l.toLowerCase(Locale.ROOT)).forEach(conjunto::add);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler a lista de senhas comuns", e);
        }
        return conjunto;
    }
}
