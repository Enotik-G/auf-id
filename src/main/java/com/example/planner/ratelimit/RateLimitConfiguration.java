package com.example.planner.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Подключает Bucket4j к Redis и ставит {@link RateLimitFilter} перед Spring Security.
 *
 * <p>Своё соединение с Redis нужно потому, что Bucket4j хранит вёдра в двоичном виде
 * и работает с клиентом Lettuce напрямую. Адрес Redis — тот же, что у остального приложения
 * ({@link DataRedisConnectionDetails}): из application.properties, а в тестах — из Testcontainers.
 */
@Configuration(proxyBeanMethods = false)
public class RateLimitConfiguration {

    @Bean(destroyMethod = "shutdown")
    RedisClient rateLimitRedisClient(DataRedisConnectionDetails redis) {
        DataRedisConnectionDetails.Standalone server = redis.getStandalone();
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(server.getHost())
                .withPort(server.getPort())
                .withDatabase(server.getDatabase());
        if (redis.getPassword() != null) {
            uri.withPassword(redis.getPassword().toCharArray());
        }
        return RedisClient.create(uri.build());
    }

    @Bean(destroyMethod = "close")
    StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(RedisClient rateLimitRedisClient) {
        return rateLimitRedisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
    }

    @Bean
    RateLimiter rateLimiter(StatefulRedisConnection<String, byte[]> rateLimitRedisConnection) {
        return new RateLimiter(Bucket4jLettuce.casBasedBuilder(rateLimitRedisConnection)
                // Полное ведро неотличимо от отсутствующего — такие ключи Redis удаляет сам.
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                .build());
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiter rateLimiter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(new RateLimitFilter(rateLimiter));
        registration.addUrlPatterns(RateLimitFilter.LOGIN_PATH, RateLimitFilter.REGISTRATION_PATH);
        // Самый первый: раньше капчи (DEFAULT_FILTER_ORDER - 1) и Spring Security —
        // лишняя попытка отклоняется до любой другой работы.
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 2);
        return registration;
    }
}
