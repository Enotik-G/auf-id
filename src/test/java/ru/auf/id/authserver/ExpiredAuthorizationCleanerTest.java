package ru.auf.id.authserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.auf.id.TestcontainersConfiguration;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Очистка истёкших авторизаций — на настоящем Postgres: удаляется только то, где истекло всё.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExpiredAuthorizationCleanerTest {

    private static final String CLIENT_ROW_ID = "cleaner-test-client";

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final Instant PAST = NOW.minus(1, ChronoUnit.HOURS);
    private static final Instant FUTURE = NOW.plus(1, ChronoUnit.HOURS);

    @Autowired
    private ExpiredAuthorizationCleaner cleaner;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createClient() {
        // авторизация ссылается на клиента внешним ключом, поэтому клиент нужен до неё
        jdbc.update("""
                INSERT INTO oauth2_registered_client
                    (id, client_id, client_name, client_authentication_methods,
                     authorization_grant_types, scopes, client_settings, token_settings)
                VALUES (?, ?, 'Тестовый клиент', 'none', 'authorization_code', 'openid', '{}', '{}')
                """, CLIENT_ROW_ID, CLIENT_ROW_ID);
    }

    @AfterEach
    void deleteClient() {
        // авторизации удаляются каскадно вместе с клиентом
        jdbc.update("DELETE FROM oauth2_registered_client WHERE id = ?", CLIENT_ROW_ID);
    }

    @Test
    void deletesAuthorizationWhereEverythingExpired() {
        insertAuthorization("expired", PAST, PAST);

        int deleted = cleaner.deleteExpired(NOW);

        assertThat(deleted).isEqualTo(1);
        assertThat(exists("expired")).isFalse();
    }

    @Test
    void keepsAuthorizationWhereEverythingIsAlive() {
        insertAuthorization("alive", FUTURE, FUTURE);

        assertThat(cleaner.deleteExpired(NOW)).isZero();
        assertThat(exists("alive")).isTrue();
    }

    @Test
    void keepsPartiallyAliveAuthorization() {
        // код давно истёк, но access token ещё живой
        insertAuthorization("partial", PAST, FUTURE);

        assertThat(cleaner.deleteExpired(NOW)).isZero();
        assertThat(exists("partial")).isTrue();
    }

    @Test
    void keepsAuthorizationWithoutAnyIssuedValues() {
        insertAuthorization("unfinished", null, null);

        assertThat(cleaner.deleteExpired(NOW)).isZero();
        assertThat(exists("unfinished")).isTrue();
    }

    private void insertAuthorization(String id, Instant codeExpiresAt, Instant accessExpiresAt) {
        jdbc.update("""
                INSERT INTO oauth2_authorization
                    (id, registered_client_id, principal_name, authorization_grant_type,
                     authorization_code_expires_at, access_token_expires_at)
                VALUES (?, ?, 'cleaner-test-user', 'authorization_code', ?, ?)
                """, id, CLIENT_ROW_ID, toOffset(codeExpiresAt), toOffset(accessExpiresAt));
    }

    private static Object toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private boolean exists(String id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM oauth2_authorization WHERE id = ?", Integer.class, id) == 1;
    }
}
