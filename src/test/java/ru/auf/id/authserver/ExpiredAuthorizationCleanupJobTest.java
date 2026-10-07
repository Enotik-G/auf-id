package ru.auf.id.authserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.auf.id.TestcontainersConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Расписание очистки: блокировка в Redis пускает к очистке только одну копию приложения.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExpiredAuthorizationCleanupJobTest {

    private static final String CLIENT_ROW_ID = "cleanup-job-test-client";
    private static final String AUTHORIZATION_ID = "cleanup-job-test-authorization";

    @Autowired
    private ExpiredAuthorizationCleanupJob job;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${auth.authorization-cleanup.interval}")
    private Duration interval;

    @BeforeEach
    void prepare() {
        // Redis общий для всех тестов с одним контекстом, поэтому начинаем со свободной блокировки
        redis.delete(ExpiredAuthorizationCleanupJob.LOCK_KEY);
        jdbc.update("""
                INSERT INTO oauth2_registered_client
                    (id, client_id, client_name, client_authentication_methods,
                     authorization_grant_types, scopes, client_settings, token_settings)
                VALUES (?, ?, 'Тестовый клиент', 'none', 'authorization_code', 'openid', '{}', '{}')
                """, CLIENT_ROW_ID, CLIENT_ROW_ID);
        // всё истекло давно, так что при настоящих часах очистка эту строку удалит
        Instant longAgo = Instant.parse("2020-01-01T00:00:00Z");
        jdbc.update("""
                INSERT INTO oauth2_authorization
                    (id, registered_client_id, principal_name, authorization_grant_type,
                     authorization_code_expires_at, access_token_expires_at)
                VALUES (?, ?, 'cleanup-job-test-user', 'authorization_code', ?, ?)
                """, AUTHORIZATION_ID, CLIENT_ROW_ID,
                longAgo.atOffset(ZoneOffset.UTC), longAgo.atOffset(ZoneOffset.UTC));
    }

    @AfterEach
    void cleanUp() {
        redis.delete(ExpiredAuthorizationCleanupJob.LOCK_KEY);
        jdbc.update("DELETE FROM oauth2_registered_client WHERE id = ?", CLIENT_ROW_ID);
    }

    @Test
    void skipsCleanupWhenLockIsTaken() {
        redis.opsForValue().set(ExpiredAuthorizationCleanupJob.LOCK_KEY, "другая копия", interval);

        assertThat(job.run()).isEqualTo(-1);
        assertThat(authorizationExists()).isTrue();
    }

    @Test
    void takesLockWithTtlAndCleansWhenLockIsFree() {
        assertThat(job.run()).isGreaterThanOrEqualTo(1);

        assertThat(authorizationExists()).isFalse();
        Long ttlSeconds = redis.getExpire(ExpiredAuthorizationCleanupJob.LOCK_KEY);
        assertThat(ttlSeconds).isPositive().isLessThanOrEqualTo(interval.toSeconds());
    }

    private boolean authorizationExists() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM oauth2_authorization WHERE id = ?", Integer.class, AUTHORIZATION_ID) == 1;
    }
}
