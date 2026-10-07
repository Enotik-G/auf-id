package ru.auf.id.ratelimit;

import io.github.bucket4j.BucketConfiguration;

import java.time.Duration;

/**
 * Лимиты запросов с одного IP-адреса (решения пользователя — см. CLAUDE.md, задача 4.4).
 *
 * <p>Модель «ведро с жетонами» (token bucket): в ведре {@code capacity} жетонов, каждый запрос забирает один,
 * раз в {@code period} ведро снова наполняется целиком. Пустое ведро — запрос отклоняется.
 */
public enum RateLimit {

    /** Попытки входа: 20 в минуту. */
    LOGIN("login", 20, Duration.ofMinutes(1)),

    /**
     * Обмен кода на токен ({@code POST /oauth2/token}): 600 в минуту (решение от 2026-10-07).
     * Ручка публичная, а каждый запрос — поход в БД и подпись ES256, поэтому без лимита её можно
     * дёргать в цикле и грузить сервер. Число большое с запасом: за общим NAT колледжа
     * в пик бывают сотни входов в минуту с одного адреса.
     */
    TOKEN("token", 600, Duration.ofMinutes(1));

    private final String keyPrefix;
    private final int capacity;
    private final Duration period;

    RateLimit(String keyPrefix, int capacity, Duration period) {
        this.keyPrefix = keyPrefix;
        this.capacity = capacity;
        this.period = period;
    }

    /** Ключ ведра в Redis — своё ведро на каждую пару «вид лимита + IP». */
    String keyFor(String clientIp) {
        return "rate:" + keyPrefix + ":" + clientIp;
    }

    BucketConfiguration bucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(capacity).refillIntervally(capacity, period))
                .build();
    }
}
