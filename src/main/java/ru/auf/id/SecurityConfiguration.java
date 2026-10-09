package ru.auf.id;

import ru.auf.id.login.SessionUserRevalidationFilter;
import ru.auf.id.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import java.io.IOException;

/**
 * Кто куда может заходить. Всё, что не открыто явно, требует входа —
 * так новая ручка по умолчанию закрыта, пока её сознательно не откроют здесь.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    private static final String HOST_PREFIX = "__Host-";

    /** Вторая по очереди: первой идёт цепочка сервера авторизации (authserver/AuthorizationServerConfiguration). */
    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            UserRepository userRepository,
            @Value("${auth.csrf.cookie-name}") String csrfCookieName,
            @Value("${server.servlet.session.cookie.secure}") boolean secureCookies) throws Exception {
        http
                // Сверять вошедшего с БД на каждом запросе: заблокированного — выпустить из сессии,
                // снятую или выданную роль — применить сразу. Подробно — в самом фильтре.
                .addFilterAfter(new SessionUserRevalidationFilter(userRepository), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(requests -> requests
                        // Активация выданной админом учётки: человек ещё не может войти — пароля у него нет.
                        .requestMatchers("/activate", "/activate/done").permitAll()
                        // Текст согласия на обработку ПДн — его читают до активации, то есть без входа.
                        .requestMatchers(HttpMethod.GET, "/consent/personal-data").permitAll()
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
                // CSRF — на всех запросах, включая админку (/api/v1/admin/**).
                //
                // Раньше /api/** был исключён с доводом «JSON-API не входит по cookie», но админка
                // входит именно по cookie сессии. Тогда любая страница на соседнем поддомене колледжа
                // могла отправить обычную HTML-форму POST .../users/{id}/unblock от имени вошедшего
                // админа: SameSite=Lax от «своего» сайта не защищает.
                //
                // spa() — готовый режим Spring Security для страниц, которые зовут API из JavaScript
                // (Swagger UI, будущая админ-панель): токен лежит в cookie, его можно прочитать
                // скриптом со своего адреса и вернуть в заголовке X-XSRF-TOKEN. Формы Thymeleaf
                // по-прежнему получают его скрытым полем _csrf. Токен в cookie, а не в серверной
                // сессии — сервис остаётся stateless. Имя cookie — см. csrfTokenRepository.
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository(csrfCookieName, secureCookies)))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
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
     * Где лежит токен CSRF: в cookie, читаемой скриптом (так его берут Swagger UI и админ-панель).
     *
     * <p>На сервере имя обязано начинаться с {@code __Host-}. Иначе защиту обходит соседний
     * поддомен колледжа: страница на {@code *.sinhub.ru} может сама поставить нашему адресу cookie
     * {@code XSRF-TOKEN} со своим значением (cookie tossing) и отправить форму с тем же значением —
     * проверка «cookie совпадает с присланным» пройдёт. Cookie с префиксом {@code __Host-} браузер
     * принимает только от самого нашего адреса, по HTTPS и без домена — поддомен её не задаст и не
     * перекроет.
     *
     * <p>Локально HTTPS нет, поэтому имя — настройка ({@code AUTH_CSRF_COOKIE_NAME}). Чтобы не забыть
     * её на сервере, приложение не стартует, если cookie сессии уже только по HTTPS, а у CSRF —
     * обычное имя.
     */
    static CookieCsrfTokenRepository csrfTokenRepository(String cookieName, boolean secureCookies) {
        boolean hostPrefixed = cookieName.startsWith(HOST_PREFIX);
        if (secureCookies && !hostPrefixed) {
            throw new IllegalStateException("SESSION_COOKIE_SECURE=true, а cookie CSRF называется " + cookieName
                    + ": на сервере задайте AUTH_CSRF_COOKIE_NAME=__Host-XSRF-TOKEN");
        }
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(cookieName);
        if (hostPrefixed) {
            // Без этих двух условий браузер cookie с префиксом __Host- просто не примет.
            repository.setCookieCustomizer(cookie -> cookie.secure(true).path("/"));
        }
        return repository;
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
