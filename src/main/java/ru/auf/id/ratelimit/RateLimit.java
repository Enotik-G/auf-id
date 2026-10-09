package ru.auf.id.ratelimit;

import io.github.bucket4j.BucketConfiguration;

import java.time.Duration;

/**
 * Лимиты запросов с одного IP-адреса (решения пользователя — см. CLAUDE.md, задача 4.4).
 *
 * <p>Модель «ведро с жетонами» (token bucket): в ведре {@code capacity} жетонов, каждый запрос забирает один,
 * раз в {@code period} ведро снова наполняется целиком. Пустое ведро — запрос отклоняется.
 *
 * <p>Сколько жетонов в ведре — не здесь, а в настройках ({@code auth.rate-limit.*}, см.
 * {@link RateLimitConfiguration}): числа зависят от того, как колледж выходит в интернет, и
 * подбираются по логам на сервере без пересборки.
 */
public enum RateLimit {

    /**
     * Попытки входа ({@code POST /login}) — {@code auth.rate-limit.login-per-minute}, по умолчанию 300.
     *
     * <p>Не 20, как было сначала (решение 2026-10-10): колледж, скорее всего, выходит в интернет
     * через общий NAT, то есть все студенты приходят с одного адреса, и утром двадцать первый получил
     * бы отказ. От подбора пароля защищает не этот лимит, а капча после трёх неудач по почте
     * ({@code LoginAttemptService}); этот — только от совсем грубой нагрузки.
     */
    LOGIN("login", Duration.ofMinutes(1)),

    /**
     * Обмен кода на токен ({@code POST /oauth2/token}) — {@code auth.rate-limit.token-per-minute},
     * по умолчанию 600 (решение от 2026-10-07). Ручка публичная, а каждый запрос — поход в БД и
     * подпись ES256, поэтому без лимита её можно дёргать в цикле и грузить сервер. Число большое с
     * запасом по той же причине — общий NAT.
     */
    TOKEN("token", Duration.ofMinutes(1));

    private final String keyPrefix;
    private final Duration period;

    RateLimit(String keyPrefix, Duration period) {
        this.keyPrefix = keyPrefix;
        this.period = period;
    }

    /** Ключ ведра в Redis — своё ведро на каждую пару «вид лимита + IP». */
    String keyFor(String clientIp) {
        return "rate:" + keyPrefix + ":" + clientIp;
    }

    BucketConfiguration bucketConfiguration(int capacity) {
        return BucketConfiguration.builder()
                .addLimit(limit -> limit.capacity(capacity).refillIntervally(capacity, period))
                .build();
    }
}
