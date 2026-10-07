package ru.auf.id.authserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.auf.id.TestcontainersConfiguration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Отзыв авторизаций пользователя — на настоящем Postgres: строки владельца удаляются, чужие остаются.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserAuthorizationRevokerTest {

    private static final String CLIENT_ROW_ID = "revoker-test-client";

    @Autowired
    private UserAuthorizationRevoker revoker;

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
    void revokeAllDeletesOnlyThisUsersAuthorizations() {
        UUID blocked = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        insertAuthorization(blocked);
        insertAuthorization(blocked);
        insertAuthorization(other);

        int deleted = revoker.revokeAll(blocked);

        assertThat(deleted).isEqualTo(2);
        assertThat(countFor(blocked)).isZero();
        assertThat(countFor(other)).isEqualTo(1);
    }

    @Test
    void revokeAllForUserWithoutAuthorizationsDeletesNothing() {
        assertThat(revoker.revokeAll(UUID.randomUUID())).isZero();
    }

    private void insertAuthorization(UUID userId) {
        jdbc.update("""
                INSERT INTO oauth2_authorization
                    (id, registered_client_id, principal_name, authorization_grant_type)
                VALUES (?, ?, ?, 'authorization_code')
                """, UUID.randomUUID().toString(), CLIENT_ROW_ID, userId.toString());
    }

    private int countFor(UUID userId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM oauth2_authorization WHERE principal_name = ?",
                Integer.class, userId.toString());
    }
}
