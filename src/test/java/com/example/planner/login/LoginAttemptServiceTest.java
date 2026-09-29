package com.example.planner.login;

import com.example.planner.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Import({TestcontainersConfiguration.class, LoginAttemptService.class})
class LoginAttemptServiceTest {

    private final UUID user = UUID.randomUUID();

    @Autowired
    private LoginAttemptService loginAttempts;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearRedis() {
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void fiveFailuresDoNotLock() {
        failTimes(5);

        assertThat(loginAttempts.isLocked(user)).isFalse();
    }

    @Test
    void sixthFailureLocksForTwentyMinutes() {
        failTimes(6);

        assertThat(loginAttempts.isLocked(user)).isTrue();
        // Блокировка — ключ с TTL: снимется сама. Проверяем, что срок — около 20 минут.
        assertThat(redis.getExpire("login:locked:" + user)).isBetween(19 * 60L, 20 * 60L);
    }

    @Test
    void successInBetweenResetsTheCount() {
        failTimes(5);
        loginAttempts.recordSuccess(user);
        failTimes(5);

        assertThat(loginAttempts.isLocked(user)).isFalse();
    }

    @Test
    void failuresOfOneUserDoNotLockAnother() {
        failTimes(6);

        assertThat(loginAttempts.isLocked(UUID.randomUUID())).isFalse();
    }

    private void failTimes(int times) {
        for (int i = 0; i < times; i++) {
            loginAttempts.recordFailure(user);
        }
    }
}
