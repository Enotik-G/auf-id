package com.example.planner;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Кто куда может заходить. Всё, что не открыто явно, требует входа —
 * так новая ручка по умолчанию закрыта, пока её сознательно не откроют здесь.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        // Регистрация и подтверждение почты — до входа, по определению.
                        .requestMatchers(HttpMethod.POST, "/api/v1/registrations", "/api/v1/email-verifications").permitAll()
                        .requestMatchers("/verify-email", "/verify-email/done").permitAll()
                        // Документация API.
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Страница ошибок Spring: без этого любая ошибка превращалась бы в 401.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        // Токен CSRF — в cookie, а не в серверной сессии: сервис остаётся stateless.
                        .csrfTokenRepository(new CookieCsrfTokenRepository())
                        // JSON-API не использует cookie для входа, CSRF-атака на него невозможна.
                        .ignoringRequestMatchers("/api/**"))
                .formLogin(Customizer.withDefaults());
        return http.build();
    }
}
