package com.example.planner;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.io.IOException;

/**
 * Кто куда может заходить. Всё, что не открыто явно, требует входа —
 * так новая ручка по умолчанию закрыта, пока её сознательно не откроют здесь.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    /** Вторая по очереди: первой идёт цепочка сервера авторизации (authserver/AuthorizationServerConfiguration). */
    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(requests -> requests
                        // Регистрация и подтверждение почты — до входа, по определению.
                        .requestMatchers(HttpMethod.POST, "/api/v1/registrations", "/api/v1/email-verifications").permitAll()
                        .requestMatchers("/verify-email", "/verify-email/done").permitAll()
                        // Активация выданной админом учётки: человек ещё не может войти — пароля у него нет.
                        .requestMatchers("/activate", "/activate/done").permitAll()
                        // Страница входа — со всеми вариантами адреса (?error, ?unconfirmed, ?logout):
                        // permitAll() у formLogin открывает только адрес /login без параметров.
                        .requestMatchers("/login").permitAll()
                        // Стили и скрипты страниц (виджет капчи).
                        .requestMatchers("/css/**", "/js/**").permitAll()
                        // Задачка капчи — её запрашивает страница входа, то есть ещё не вошедший человек.
                        .requestMatchers(HttpMethod.GET, "/captcha/challenge").permitAll()
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
                .formLogin(form -> form
                        // Своя страница входа (LoginPageController); открыта выше, в authorizeHttpRequests.
                        .loginPage("/login")
                        .defaultSuccessUrl("/")
                        .failureHandler(SecurityConfiguration::redirectToLoginWithReason))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    /**
     * Куда отправить после неудачного входа. «Почта не подтверждена» возможна только при верном пароле
     * (см. LoginConfiguration), всё остальное — одно общее сообщение без подробностей.
     */
    private static void redirectToLoginWithReason(HttpServletRequest request, HttpServletResponse response,
                                                  AuthenticationException exception) throws IOException {
        String reason = exception instanceof DisabledException ? "unconfirmed" : "error";
        response.sendRedirect(request.getContextPath() + "/login?" + reason);
    }
}
