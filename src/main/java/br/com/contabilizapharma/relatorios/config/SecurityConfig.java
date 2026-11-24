package br.com.contabilizapharma.relatorios.config;

import br.com.contabilizapharma.relatorios.core.seguranca.SessaoFilter;
import br.com.contabilizapharma.relatorios.core.web.OrigemFilter;
import br.com.contabilizapharma.relatorios.core.web.RespostaJson;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Web com sessão em cookie HttpOnly guardada no Redis (RNF03, Guia §6.12) e CSRF por
 * synchronizer token no cabeçalho X-CSRF-Token (Guia §8.6). Negar por padrão (§6.3).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    public static final String CABECALHO_CSRF = "X-CSRF-Token";

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filtros(HttpSecurity http, AppProperties props, RespostaJson resposta,
                                       SecurityContextRepository contextos) throws Exception {
        HttpSessionCsrfTokenRepository csrfRepo = new HttpSessionCsrfTokenRepository();
        csrfRepo.setHeaderName(CABECALHO_CSRF);

        http
            .securityContext(sc -> sc.securityContextRepository(contextos))
            .csrf(c -> c.csrfTokenRepository(csrfRepo)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
            .authorizeHttpRequests(a -> a
                    .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMINISTRADOR")
                    .requestMatchers("/api/v1/importacoes/**").hasAnyRole("ADMINISTRADOR", "COLABORADOR")
                    .requestMatchers("/api/v1/relatorios/**").hasAnyRole("CLIENTE", "ADMINISTRADOR")
                    .requestMatchers("/api/v1/auth/**").authenticated()
                    .anyRequest().denyAll())
            .exceptionHandling(e -> e
                    .authenticationEntryPoint((req, res, ex) -> resposta.problema(res, HttpStatus.UNAUTHORIZED,
                            "Não autenticado", "Entre com seu e-mail e senha para continuar."))
                    .accessDeniedHandler((req, res, ex) -> {
                        if (ex instanceof CsrfException) {
                            resposta.problema(res, HttpStatus.FORBIDDEN, "Sessão desatualizada",
                                    "Recarregue a página e tente de novo.");
                        } else {
                            resposta.problema(res, HttpStatus.FORBIDDEN, "Acesso negado",
                                    "Você não tem permissão para esta ação.");
                        }
                    }))
            .headers(h -> {
                h.frameOptions(f -> f.deny());
                h.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"));
                if (props.producao()) {
                    h.httpStrictTransportSecurity(t -> t.includeSubDomains(true).maxAgeInSeconds(31_536_000));
                }
            })
            .addFilterBefore(new OrigemFilter(props.origensPermitidas(), resposta), AuthorizationFilter.class)
            .addFilterBefore(new SessaoFilter(props, resposta), AuthorizationFilter.class);

        return http.build();
    }
}
