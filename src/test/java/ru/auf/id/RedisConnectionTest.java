package ru.auf.id;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Приложение действительно подключается к Redis, и ключи умеют истекать сами (на этом построена блокировка). */
@DataRedisTest
@Import(TestcontainersConfiguration.class)
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void storesValueThatExpiresByItself() {
        redis.opsForValue().set("test:key", "42", Duration.ofMinutes(20));

        assertThat(redis.opsForValue().get("test:key")).isEqualTo("42");
        assertThat(redis.getExpire("test:key")).isBetween(1L, 20 * 60L);
    }

    @Test
    void countsAtomically() {
        redis.delete("test:counter");

        redis.opsForValue().increment("test:counter");
        Long afterSecond = redis.opsForValue().increment("test:counter");

        assertThat(afterSecond).isEqualTo(2);
    }
}
