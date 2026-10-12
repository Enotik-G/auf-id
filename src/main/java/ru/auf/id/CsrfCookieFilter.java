package ru.auf.id;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Заставляет Spring Security записать токен CSRF в cookie {@code XSRF-TOKEN} на каждом ответе.
 *
 * <p>Spring создаёт токен лениво: в cookie он попадает, только когда к нему кто-то обратился. Формы
 * Thymeleaf обращаются сами (скрытое поле {@code _csrf}), а ответы JSON и статичный Swagger UI — нет.
 * Хуже того, после входа Spring выдаёт новый токен, и без этого фильтра первый POST из Swagger
 * или админ-панели получал бы 403 — cookie с действующим токеном у браузера просто не было бы.
 *
 * <p>Так рекомендует документация Spring Security для страниц, которые зовут API из JavaScript.
 */
class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Само обращение к значению и есть сигнал «запиши токен в cookie».
            csrfToken.getToken();
        }
        chain.doFilter(request, response);
    }
}
