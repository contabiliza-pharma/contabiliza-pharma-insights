package br.com.contabilizapharma.relatorios.email;

public record MensagemEmail(String para, String assunto, String texto, String html) {}
