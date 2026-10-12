package ru.auf.id.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Отсекает слишком частые попытки входа с одного IP — ещё до Spring Security, то есть до проверки
 * пароля: лишние попытки даже не тратят Argon2. Так же ограничивает обмен кода на токен
 * ({@code POST /oauth2/token}): лишний запрос не доходит ни до БД, ни до подписи токена.
 *
 * <p>Активацию по ссылке не ограничиваем: там один индексированный поиск по хешу токена, сам токен
 * 256-битный и перебору не поддаётся, а пароль хешируется уже после проверки токена. Зато лимит по IP
 * отрезал бы группу студентов, активирующихся из одного класса за общим NAT.
 */
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/login";
    static final String TOKEN_PATH = "/oauth2/token";

    private final RateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimit limit = limitFor(request);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }

        // Адрес клиента. За своим прокси (nginx) Tomcat уже подставил сюда адрес человека из
        // X-Forwarded-For — но только если прокси в списке доверенных (TRUSTED_PROXIES, шаг 20).
        RateLimiter.Decision decision = rateLimiter.tryAcquire(limit, clientKey(request.getRemoteAddr()));
        if (decision.isAllowed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, decision.retryAfter().toSeconds());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        if (limit == RateLimit.LOGIN) {
            // Страница входа — для людей: возвращаем на неё с понятным сообщением.
            response.sendRedirect(request.getContextPath() + LOGIN_PATH + "?tooManyAttempts");
        } else {
            // Ручку токена вызывает программа-клиент: ей нужен код ответа, а не страница.
            // 429 Too Many Requests + Retry-After — сколько секунд подождать перед повтором.
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        }
    }

    /**
     * Кого считать одним клиентом. IPv4 — адрес целиком. IPv6 — первые 64 бита (сеть /64):
     * провайдеры обычно выдают абоненту целый блок из 2^64 адресов, и без этого злоумышленник
     * брал бы новый адрес на каждый запрос — и каждый раз получал полное ведро.
     */
    static String clientKey(String remoteAddr) {
        try {
            InetAddress address = InetAddress.ofLiteral(remoteAddr);
            if (address instanceof Inet6Address) {
                byte[] network = Arrays.copyOf(address.getAddress(), 16);
                Arrays.fill(network, 8, 16, (byte) 0);
                return InetAddress.getByAddress(network).getHostAddress() + "/64";
            }
            return address.getHostAddress();
        } catch (IllegalArgumentException | UnknownHostException e) {
            // Не IP-адрес (так не бывает у настоящего соединения) — считаем как есть.
            return remoteAddr;
        }
    }

    /** Какой лимит применить к запросу; null — запрос не ограничиваем. Считаем только отправку форм (POST). */
    private static RateLimit limitFor(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        // Путь без префикса приложения. Не getServletPath(): в тестах (MockMvc) он пустой.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return switch (path) {
            case LOGIN_PATH -> RateLimit.LOGIN;
            case TOKEN_PATH -> RateLimit.TOKEN;
            default -> null;
        };
    }
}
