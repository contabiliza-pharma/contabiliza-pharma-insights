package br.com.contabilizapharma.relatorios.empresa;

/** Normalização e validação de CNPJ (dígitos verificadores). Função pura. */
public final class Cnpj {

    private static final int[] PESOS_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] PESOS_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private Cnpj() {}

    public static String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    public static boolean ehValido(String valor) {
        String d = somenteDigitos(valor);
        if (d.length() != 14 || d.chars().distinct().count() == 1) {
            return false;
        }
        return digito(d, PESOS_1) == d.charAt(12) - '0' && digito(d, PESOS_2) == d.charAt(13) - '0';
    }

    public static String raiz(String cnpj14) {
        return cnpj14.substring(0, 8);
    }

    /** Ordem "0001" no CNPJ indica a matriz. */
    public static boolean ehMatriz(String cnpj14) {
        return "0001".equals(cnpj14.substring(8, 12));
    }

    public static String formatar(String cnpj14) {
        String d = somenteDigitos(cnpj14);
        if (d.length() != 14) {
            return cnpj14;
        }
        return d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/"
                + d.substring(8, 12) + "-" + d.substring(12);
    }

    private static int digito(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (d.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
