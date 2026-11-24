package br.com.contabilizapharma.relatorios.usuario;

/** Espelha o enum perfil_usuario do banco (valores em minúsculas, como no DBML). */
public enum Perfil {
    cliente, colaborador, administrador;

    public String papel() {
        return "ROLE_" + name().toUpperCase();
    }
}
