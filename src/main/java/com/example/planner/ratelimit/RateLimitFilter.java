package com.example.planner.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Отсекает слишком частые попытки входа с одного IP — ещё до Spring Security, то есть до проверки
 * пароля: лишние попытки даже не тратят Argon2.
 *
 * <p>Активацию по ссылке не ограничиваем: там один индексированный поиск по хешу токена, сам токен
 * 256-битный и перебору не поддаётся, а пароль хешируется уже после проверки токена. Зато лимит по IP
 * отрезал бы группу студентов, активирующихся из одного класса за общим NAT.
 */
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/login";

    private final RateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimit limit = limitFor(request);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }

        // Адрес клиента. За прокси (nginx и т.п.) здесь будет адрес прокси — см. CLAUDE.md, «Заметки».
        RateLimiter.Decision decision = rateLimiter.tryAcquire(limit, clientKey(request.getRemoteAddr()));
        if (decision.isAllowed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, decision.retryAfter().toSeconds());
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        // Страница входа — для людей: возвращаем на неё с понятным сообщением.
        response.sendRedirect(request.getContextPath() + LOGIN_PATH + "?tooManyAttempts");
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
        return LOGIN_PATH.equals(path) ? RateLimit.LOGIN : null;
    }
}
