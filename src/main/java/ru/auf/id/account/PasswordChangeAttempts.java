package ru.auf.id.account;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Неверные «текущие пароли» при смене пароля — счётчик в Redis на каждую учётку.
 *
 * <p>Без него открытая сессия — например, брошенная в компьютерном классе — позволяла бы подбирать
 * пароль владельца без капчи и без ограничений: каждый неверный вариант давал бы просто 400.
 *
 * <p>Окно фиксированное: {@link #WINDOW} отсчитывается от <b>первой</b> неудачи и не продлевается
 * следующими. Поэтому всегда известно, когда снова можно пробовать, — это уходит человеку в
 * заголовке {@code Retry-After}.
 */
@Component
@RequiredArgsConstructor
public class PasswordChangeAttempts {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    /**
     * Сколько ждать до следующей попытки; пусто — пробовать можно.
     *
     * <p>Закрыто для любой попытки, в том числе с верным паролем: иначе подбирающий просто дождался
     * бы своего «угадал», и ограничение ничего бы не значило.
     */
    public Optional<Duration> lockedFor(UUID userId) {
        String failures = redis.opsForValue().get(key(userId));
        if (failures == null || Integer.parseInt(failures) < MAX_FAILURES) {
            return Optional.empty();
        }
        Long secondsLeft = redis.getExpire(key(userId));
        // Ключ мог истечь между двумя обращениями — тогда ждать уже нечего.
        return secondsLeft == null || secondsLeft <= 0 ? Optional.empty() : Optional.of(Duration.ofSeconds(secondsLeft));
    }

    public void recordFailure(UUID userId) {
        Long failures = redis.opsForValue().increment(key(userId));
        // Срок ставится только первой неудаче: так окно не продлевается каждой следующей.
        if (failures != null && failures == 1) {
            redis.expire(key(userId), WINDOW);
        }
    }

    /** Пароль сменён — прошлые ошибки больше не важны. */
    public void reset(UUID userId) {
        redis.delete(key(userId));
    }

    private static String key(UUID userId) {
        return "password-change:failures:" + userId;
    }
}
