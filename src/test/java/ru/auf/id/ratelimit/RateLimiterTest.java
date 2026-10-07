package ru.auf.id.ratelimit;

import ru.auf.id.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Import({TestcontainersConfiguration.class, RateLimitConfiguration.class})
class RateLimiterTest {

    private static final String IP = "203.0.113.7";

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearRedis() {
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void allowsTwentyLoginAttemptsPerMinuteThenRejects() {
        for (int i = 0; i < 20; i++) {
            assertThat(rateLimiter.tryAcquire(RateLimit.LOGIN, IP).isAllowed()).isTrue();
        }

        RateLimiter.Decision twentyFirst = rateLimiter.tryAcquire(RateLimit.LOGIN, IP);

        assertThat(twentyFirst.isAllowed()).isFalse();
        assertThat(twentyFirst.retryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1));
    }

    /** Своё ведро на каждый адрес: исчерпавший лимит не мешает остальным. */
    @Test
    void eachIpHasItsOwnLimit() {
        for (int i = 0; i < 20; i++) {
            rateLimiter.tryAcquire(RateLimit.LOGIN, IP);
        }

        assertThat(rateLimiter.tryAcquire(RateLimit.LOGIN, "198.51.100.1").isAllowed()).isTrue();
    }
}
