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
    private static final String OTHER_CLIENT_ROW_ID = "revoker-test-client-2";

    @Autowired
    private UserAuthorizationRevoker revoker;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createClients() {
        // авторизация ссылается на клиента внешним ключом, поэтому клиент нужен до неё.
        // Второй клиент — чтобы проверить отзыв «только у этого клиента».
        createClient(CLIENT_ROW_ID);
        createClient(OTHER_CLIENT_ROW_ID);
    }

    private void createClient(String id) {
        jdbc.update("""
                INSERT INTO oauth2_registered_client
                    (id, client_id, client_name, client_authentication_methods,
                     authorization_grant_types, scopes, client_settings, token_settings)
                VALUES (?, ?, 'Тестовый клиент', 'none', 'authorization_code', 'openid', '{}', '{}')
                """, id, id);
    }

    @AfterEach
    void deleteClients() {
        // авторизации удаляются каскадно вместе с клиентом
        jdbc.update("DELETE FROM oauth2_registered_client WHERE id IN (?, ?)",
                CLIENT_ROW_ID, OTHER_CLIENT_ROW_ID);
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

    /**
     * Отзыв при краже refresh-токена: вылетает только то приложение, чей токен украли.
     * Украли токен лаунчера — в планировщике человек остаётся.
     */
    @Test
    void revokeAllForClientSparesTheSameUsersOtherClients() {
        UUID user = UUID.randomUUID();
        insertAuthorization(user, CLIENT_ROW_ID);
        insertAuthorization(user, CLIENT_ROW_ID);
        insertAuthorization(user, OTHER_CLIENT_ROW_ID);

        int deleted = revoker.revokeAllForClient(user.toString(), CLIENT_ROW_ID);

        assertThat(deleted).isEqualTo(2);
        assertThat(countFor(user, CLIENT_ROW_ID)).isZero();
        assertThat(countFor(user, OTHER_CLIENT_ROW_ID)).isEqualTo(1);
    }

    /** И чужого пользователя у того же клиента отзыв не касается. */
    @Test
    void revokeAllForClientSparesOtherUsers() {
        UUID victim = UUID.randomUUID();
        UUID bystander = UUID.randomUUID();
        insertAuthorization(victim, CLIENT_ROW_ID);
        insertAuthorization(bystander, CLIENT_ROW_ID);

        revoker.revokeAllForClient(victim.toString(), CLIENT_ROW_ID);

        assertThat(countFor(victim, CLIENT_ROW_ID)).isZero();
        assertThat(countFor(bystander, CLIENT_ROW_ID)).isEqualTo(1);
    }

    private int countFor(UUID userId, String clientRowId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM oauth2_authorization"
                        + " WHERE principal_name = ? AND registered_client_id = ?",
                Integer.class, userId.toString(), clientRowId);
    }

    private void insertAuthorization(UUID userId) {
        insertAuthorization(userId, CLIENT_ROW_ID);
    }

    private void insertAuthorization(UUID userId, String clientRowId) {
        jdbc.update("""
                INSERT INTO oauth2_authorization
                    (id, registered_client_id, principal_name, authorization_grant_type)
                VALUES (?, ?, ?, 'authorization_code')
                """, UUID.randomUUID().toString(), clientRowId, userId.toString());
    }

    private int countFor(UUID userId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM oauth2_authorization WHERE principal_name = ?",
                Integer.class, userId.toString());
    }
}
