package ru.auf.id.admin;

import ru.auf.id.ClockConfiguration;
import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.authserver.UserAuthorizationRevoker;
import ru.auf.id.onetimetoken.InvalidOneTimeTokenException;
import ru.auf.id.onetimetoken.OneTimeTokenService;
import ru.auf.id.onetimetoken.TokenPurpose;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.PasswordCredential;
import ru.auf.id.user.PasswordCredentialRepository;
import ru.auf.id.user.PasswordHasher;
import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import ru.auf.id.user.UserNotFoundException;
import ru.auf.id.user.UserRepository;
import ru.auf.id.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestcontainersConfiguration.class, ClockConfiguration.class,
        AdminUserService.class, OneTimeTokenService.class, PasswordHasher.class,
        UserAuthorizationRevoker.class})
class AdminUserServiceTest {

    @Autowired
    private AdminUserService admin;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordCredentialRepository credentialRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private OneTimeTokenService tokenService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void blocksUser() {
        User student = activeUser("ivan@sinhub.ru");

        admin.block(student.getId());

        assertThat(reload(student).getStatus()).isEqualTo(UserStatus.BLOCKED);
    }

    /** Выданные коды и токены не должны пережить блокировку: до refresh-токенов это закрываем заранее. */
    @Test
    void blockingRevokesIssuedAuthorizations() {
        User student = activeUser("ivan@sinhub.ru");
        User other = activeUser("petr@sinhub.ru");
        insertAuthorization(student);
        insertAuthorization(other);

        admin.block(student.getId());

        assertThat(authorizationsOf(student)).isZero();
        assertThat(authorizationsOf(other)).isEqualTo(1);
    }

    /** Неиспользованная ссылка — это отложенный вход: заблокировали, а он активировался через час. */
    @Test
    void blockingRevokesPendingLinks() {
        User invited = invitedUser("new@sinhub.ru");
        String activationLink = tokenService.issue(invited, TokenPurpose.INVITE);

        admin.block(invited.getId());

        assertThatThrownBy(() -> tokenService.consume(activationLink, TokenPurpose.INVITE))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    @Test
    void unblockingRestoresActiveAccount() {
        User student = activeUser("ivan@sinhub.ru");
        admin.block(student.getId());

        admin.unblock(student.getId());

        assertThat(reload(student).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    /** Без пароля «активный» аккаунт выглядел бы исправным, а войти в него было бы нельзя. */
    @Test
    void unblockingAccountWithoutPasswordReturnsItToInvited() {
        User invited = invitedUser("new@sinhub.ru");
        admin.block(invited.getId());

        admin.unblock(invited.getId());

        assertThat(reload(invited).getStatus()).isEqualTo(UserStatus.INVITED);
    }

    @Test
    void refusesToUnblockUserThatIsNotBlocked() {
        User student = activeUser("ivan@sinhub.ru");

        assertThatThrownBy(() -> admin.unblock(student.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void grantsAndRevokesRole() {
        User student = activeUser("ivan@sinhub.ru");

        admin.grantRole(student.getId(), Role.CURATOR);
        assertThat(reload(student).getRoles()).containsExactly(Role.CURATOR);

        admin.revokeRole(student.getId(), Role.CURATOR);
        assertThat(reload(student).getRoles()).isEmpty();
    }

    @Test
    void refusesToRevokeAdminRoleFromTheLastAdmin() {
        User onlyAdmin = activeUser("boss@sinhub.ru");
        admin.grantRole(onlyAdmin.getId(), Role.ADMIN);

        assertThatThrownBy(() -> admin.revokeRole(onlyAdmin.getId(), Role.ADMIN))
                .isInstanceOf(LastAdminException.class);
        assertThat(reload(onlyAdmin).hasRole(Role.ADMIN)).isTrue();
    }

    @Test
    void refusesToBlockTheLastAdmin() {
        User onlyAdmin = activeUser("boss@sinhub.ru");
        admin.grantRole(onlyAdmin.getId(), Role.ADMIN);

        assertThatThrownBy(() -> admin.block(onlyAdmin.getId()))
                .isInstanceOf(LastAdminException.class);
        assertThat(reload(onlyAdmin).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void allowsRevokingAdminRoleWhenAnotherAdminRemains() {
        User first = activeUser("boss@sinhub.ru");
        User second = activeUser("deputy@sinhub.ru");
        admin.grantRole(first.getId(), Role.ADMIN);
        admin.grantRole(second.getId(), Role.ADMIN);

        admin.revokeRole(first.getId(), Role.ADMIN);

        assertThat(reload(first).hasRole(Role.ADMIN)).isFalse();
    }

    /** Заблокированный администратор — всё равно что отсутствующий, поэтому последним не считается. */
    @Test
    void blockedAdminDoesNotCountAsRemainingAdmin() {
        User working = activeUser("boss@sinhub.ru");
        User blocked = activeUser("retired@sinhub.ru");
        admin.grantRole(working.getId(), Role.ADMIN);
        admin.grantRole(blocked.getId(), Role.ADMIN);
        admin.block(blocked.getId());

        assertThatThrownBy(() -> admin.block(working.getId()))
                .isInstanceOf(LastAdminException.class);
    }

    /** Защита касается только роли ADMIN: остальные роли снимаются свободно. */
    @Test
    void protectionAppliesOnlyToTheAdminRole() {
        User onlyAdmin = activeUser("boss@sinhub.ru");
        admin.grantRole(onlyAdmin.getId(), Role.ADMIN);
        admin.grantRole(onlyAdmin.getId(), Role.CURATOR);

        admin.revokeRole(onlyAdmin.getId(), Role.CURATOR);

        assertThat(reload(onlyAdmin).getRoles()).containsExactly(Role.ADMIN);
    }

    @Test
    void refusesUnknownUser() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> admin.block(missing)).isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> admin.grantRole(missing, Role.STUDENT)).isInstanceOf(UserNotFoundException.class);
    }

    private void insertAuthorization(User owner) {
        // авторизация ссылается на клиента внешним ключом; тест откатывается, поэтому клиент не мусорит
        jdbc.update("""
                INSERT INTO oauth2_registered_client
                    (id, client_id, client_name, client_authentication_methods,
                     authorization_grant_types, scopes, client_settings, token_settings)
                VALUES (?, ?, 'Тестовый клиент', 'none', 'authorization_code', 'openid', '{}', '{}')
                ON CONFLICT DO NOTHING
                """, "admin-test-client", "admin-test-client");
        jdbc.update("""
                INSERT INTO oauth2_authorization
                    (id, registered_client_id, principal_name, authorization_grant_type)
                VALUES (?, 'admin-test-client', ?, 'authorization_code')
                """, UUID.randomUUID().toString(), owner.getId().toString());
    }

    private int authorizationsOf(User owner) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM oauth2_authorization WHERE principal_name = ?",
                Integer.class, owner.getId().toString());
    }

    private User activeUser(String email) {
        User user = User.invited(new EmailAddress(email), "Кто-то");
        user.activate();
        User saved = userRepository.save(user);
        credentialRepository.save(PasswordCredential.forUser(saved, passwordHasher.hash("correct horse battery staple")));
        return saved;
    }

    private User invitedUser(String email) {
        return userRepository.save(User.invited(new EmailAddress(email), "Приглашённый"));
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }
}
