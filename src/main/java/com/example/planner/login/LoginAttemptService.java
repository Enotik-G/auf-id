package com.example.planner.login;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Защита аккаунта от подбора пароля: после {@value #MAX_FAILURES} неудачных входов подряд
 * аккаунт закрыт на {@link #LOCK_DURATION}. Счётчики — в Redis: общие для всех копий приложения
 * и истекают сами (TTL), так что снимать блокировку отдельным кодом не нужно.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    static final int MAX_FAILURES = 6;
    static final Duration LOCK_DURATION = Duration.ofMinutes(20);

    private final StringRedisTemplate redis;

    public void recordFailure(UUID userId) {
        String failuresKey = failuresKey(userId);
        Long failures = redis.opsForValue().increment(failuresKey);
        // Счётчик забывается через 20 минут тишины: старые ошибки не копятся вечно.
        redis.expire(failuresKey, LOCK_DURATION);

        if (failures != null && failures >= MAX_FAILURES) {
            redis.opsForValue().set(lockKey(userId), "locked", LOCK_DURATION);
            redis.delete(failuresKey);
        }
    }

    /** Удачный вход обнуляет счётчик: блокируем за ошибки подряд, а не за все ошибки вообще. */
    public void recordSuccess(UUID userId) {
        redis.delete(failuresKey(userId));
    }

    public boolean isLocked(UUID userId) {
        return Boolean.TRUE.equals(redis.hasKey(lockKey(userId)));
    }

    private static String failuresKey(UUID userId) {
        return "login:failures:" + userId;
    }

    private static String lockKey(UUID userId) {
        return "login:locked:" + userId;
    }
}
