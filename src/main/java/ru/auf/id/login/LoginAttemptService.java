package ru.auf.id.login;

import ru.auf.id.user.EmailAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Защита от подбора пароля: после {@value #CAPTCHA_THRESHOLD} неверных паролей подряд к одной почте
 * вход с этой почтой требует решённую капчу. Аккаунт при этом <b>не блокируется</b> —
 * иначе чужой аккаунт можно было бы держать закрытым, специально вводя неверные пароли.
 *
 * <p>Счётчик ведётся по <b>введённой почте</b>, а не по id пользователя: так капча появляется одинаково
 * для существующих и выдуманных адресов, и по ней нельзя понять, зарегистрирован ли адрес.
 * Счётчики — в Redis: общие для всех копий приложения и истекают сами (TTL).
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    static final int CAPTCHA_THRESHOLD = 3;
    /** Сколько помним неудачи после последней: сутки тишины — и капча больше не нужна. */
    static final Duration FAILURE_MEMORY = Duration.ofDays(1);

    private final StringRedisTemplate redis;

    public void recordFailure(EmailAddress email) {
        String key = failuresKey(email);
        redis.opsForValue().increment(key);
        redis.expire(key, FAILURE_MEMORY);
    }

    /** Удачный вход обнуляет счётчик: капча — за ошибки подряд, а не за все ошибки вообще. */
    public void recordSuccess(EmailAddress email) {
        redis.delete(failuresKey(email));
    }

    public boolean isCaptchaRequired(EmailAddress email) {
        String failures = redis.opsForValue().get(failuresKey(email));
        return failures != null && Integer.parseInt(failures) >= CAPTCHA_THRESHOLD;
    }

    /**
     * Ключ — SHA-256 от почты, а не сама почта: в Redis не лежат адреса пользователей открытым текстом
     * (меньше персональных данных — меньше проблем, если кто-то заглянет в Redis).
     */
    private static String failuresKey(EmailAddress email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(email.value().getBytes(StandardCharsets.UTF_8));
            return "login:failures:" + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 недоступен в этой JVM", e);
        }
    }
}
