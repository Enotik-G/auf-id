package ru.auf.id.admin;

import ru.auf.id.TestTime;
import ru.auf.id.ClockConfiguration;
import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.authserver.UserAuthorizationRevoker;
import ru.auf.id.onetimetoken.InvalidOneTimeTokenException;
import ru.auf.id.onetimetoken.OneTimeTokenService;
import ru.auf.id.onetimetoken.TokenPurpose;
import ru.auf.id.provisioning.EmailAlreadyTakenException;
import ru.auf.id.user.AllowedEmailDomains;
import ru.auf.id.user.InvalidEmailException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestcontainersConfiguration.class, ClockConfiguration.class,
        AdminUserService.class, OneTimeTokenService.class, PasswordHasher.class,
        UserAuthorizationRevoker.class, AllowedEmailDomains.class})
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

    // ─────────────────────────── список ───────────────────────────

    @Test
    void listsUsersAlphabeticallyByEmailInPages() {
        activeUser("boris@sinhub.ru");
        activeUser("anna@sinhub.ru");
        activeUser("vera@sinhub.ru");

        Page<User> first = admin.list(null, null, PageRequest.of(0, 2, Sort.by("email")));

        assertThat(first.getContent()).extracting(user -> user.getEmail().value())
                .containsExactly("anna@sinhub.ru", "boris@sinhub.ru");
        assertThat(first.getTotalElements()).isEqualTo(3);
    }

    @Test
    void findsBySubstringOfNameOrEmailIgnoringCase() {
        User ivan = activeUser("ivan.petrov@sinhub.ru");
        User anna = activeUser("anna@sinhub.ru");
        anna.rename("Анна Петрова");
        userRepository.save(anna);

        // По ФИО, без учёта регистра, кириллицей.
        assertThat(search("ПЕТРОВ")).containsExactly(anna.getId());
        // По почте.
        assertThat(search("Petrov")).containsExactly(ivan.getId());
    }

    /** Введённые {@code _} и {@code %} ищутся как символы, а не как шаблон «любой символ». */
    @Test
    void wildcardsInQueryAreSearchedLiterally() {
        User underscored = activeUser("ivan_p@sinhub.ru");
        activeUser("ivanxp@sinhub.ru");

        assertThat(search("ivan_p")).containsExactly(underscored.getId());
        assertThat(search("%")).isEmpty();
    }

    @Test
    void filtersByStatus() {
        User blocked = activeUser("blocked@sinhub.ru");
        activeUser("active@sinhub.ru");
        admin.block(blocked.getId());

        assertThat(admin.list(null, UserStatus.BLOCKED, PageRequest.of(0, 20)).getContent())
                .extracting(User::getId).containsExactly(blocked.getId());
    }

    // ─────────────────────────── правка ───────────────────────────

    @Test
    void renamesUser() {
        User ivan = activeUser("ivan@sinhub.ru");

        admin.update(ivan.getId(), "  Иван Сидоров ", null);

        assertThat(reload(ivan).getFullName()).isEqualTo("Иван Сидоров");
        assertThat(reload(ivan).getEmail().value()).isEqualTo("ivan@sinhub.ru");
    }

    @Test
    void changesEmail() {
        User ivan = activeUser("ivan@sinhub.ru");

        admin.update(ivan.getId(), null, "Ivan.Sidorov@Sinhub.ru");

        assertThat(reload(ivan).getEmail().value()).isEqualTo("ivan.sidorov@sinhub.ru");
    }

    @Test
    void refusesEmailOutsideTheCollegeDomain() {
        User ivan = activeUser("ivan@sinhub.ru");

        assertThatThrownBy(() -> admin.update(ivan.getId(), null, "ivan@gmail.com"))
                .isInstanceOf(InvalidEmailException.class);
        assertThat(reload(ivan).getEmail().value()).isEqualTo("ivan@sinhub.ru");
    }

    @Test
    void refusesEmailTakenBySomeoneElse() {
        User ivan = activeUser("ivan@sinhub.ru");
        activeUser("oleg@sinhub.ru");

        assertThatThrownBy(() -> admin.update(ivan.getId(), null, "oleg@sinhub.ru"))
                .isInstanceOf(EmailAlreadyTakenException.class);
    }

    /** Свой же адрес (например, в другом регистре) — не «занят». */
    @Test
    void ownEmailIsNotTaken() {
        User ivan = activeUser("ivan@sinhub.ru");

        admin.update(ivan.getId(), "Иван", "IVAN@sinhub.ru");

        assertThat(reload(ivan).getFullName()).isEqualTo("Иван");
    }

    private java.util.List<UUID> search(String query) {
        return admin.list(query, null, PageRequest.of(0, 20)).getContent().stream().map(User::getId).toList();
    }

    private User activeUser(String email) {
        User user = User.invited(new EmailAddress(email), "Кто-то", TestTime.NOW);
        user.activate();
        User saved = userRepository.save(user);
        credentialRepository.save(PasswordCredential.forUser(saved, passwordHasher.hash("correct horse battery staple"), TestTime.NOW));
        return saved;
    }

    private User invitedUser(String email) {
        return userRepository.save(User.invited(new EmailAddress(email), "Приглашённый", TestTime.NOW));
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }
}
