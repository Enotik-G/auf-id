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
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
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
                        // Активация выданной админом учётки: человек ещё не может войти — пароля у него нет.
                        .requestMatchers("/activate", "/activate/done").permitAll()
                        // Страница входа — со всеми вариантами адреса (?error, ?blocked, ?logout):
                        // permitAll() у formLogin открывает только адрес /login без параметров.
                        .requestMatchers("/login").permitAll()
                        // Стили и скрипты страниц (виджет капчи).
                        .requestMatchers("/css/**", "/js/**").permitAll()
                        // Задачка капчи — её запрашивает страница входа, то есть ещё не вошедший человек.
                        .requestMatchers(HttpMethod.GET, "/captcha/challenge").permitAll()
                        // Админка: только для роли ADMIN. Правило стоит до anyRequest(),
                        // иначе достаточно было бы просто войти.
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
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
                // Браузеру без входа показываем страницу логина, а API отвечаем 401: редирект на
                // HTML-форму в ответ на запрос JSON админ-панель разобрать не сможет.
                //
                // Вторая точка входа обязательна, хотя и выглядит лишней: если зарегистрировать
                // только одну, Spring применит её ко всем запросам, не глядя на матчер, и страница
                // входа перестанет открываться.
                .exceptionHandling(handling -> handling
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI().startsWith("/api/"))
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                request -> true))
                .formLogin(form -> form
                        // Своя страница входа (LoginPageController); открыта выше, в authorizeHttpRequests.
                        .loginPage("/login")
                        .defaultSuccessUrl("/")
                        .failureHandler(SecurityConfiguration::redirectToLoginWithReason))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    /**
     * Куда отправить после неудачного входа.
     *
     * <p>«Доступ закрыт» возможно только при верном пароле (см. LoginConfiguration), поэтому подсказку
     * видит владелец аккаунта, а не тот, кто перебирает адреса. Всё остальное — одно общее сообщение
     * без подробностей.
     */
    private static void redirectToLoginWithReason(HttpServletRequest request, HttpServletResponse response,
                                                  AuthenticationException exception) throws IOException {
        String reason = exception instanceof DisabledException ? "blocked" : "error";
        response.sendRedirect(request.getContextPath() + "/login?" + reason);
    }
}
