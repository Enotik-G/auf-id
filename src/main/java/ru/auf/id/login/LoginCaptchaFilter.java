package ru.auf.id.login;

import ru.auf.id.captcha.CaptchaService;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.InvalidEmailException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Требует решённую капчу при входе, если к этой почте уже было {@value LoginAttemptService#CAPTCHA_THRESHOLD}
 * неверных пароля подряд. Стоит перед Spring Security: без капчи до проверки пароля дело не доходит.
 */
@RequiredArgsConstructor
public class LoginCaptchaFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/login";
    /** Имя поля формы, в которое виджет ALTCHA кладёт решение. */
    static final String CAPTCHA_FIELD = "altcha";

    private final LoginAttemptService loginAttempts;
    private final CaptchaService captchaService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isLoginFormSubmit(request) && captchaRequired(request) && !captchaService.isSolved(request.getParameter(CAPTCHA_FIELD))) {
            response.sendRedirect(request.getContextPath() + LOGIN_PATH + "?captcha");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isLoginFormSubmit(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "POST".equals(request.getMethod()) && LOGIN_PATH.equals(path);
    }

    private boolean captchaRequired(HttpServletRequest request) {
        try {
            return loginAttempts.isCaptchaRequired(new EmailAddress(request.getParameter("username")));
        } catch (InvalidEmailException e) {
            // Не почта — дальше Spring Security ответит обычным «неверная почта или пароль».
            return false;
        }
    }
}
