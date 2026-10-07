package com.example.planner.admin;

import com.example.planner.ClockConfiguration;
import com.example.planner.TestcontainersConfiguration;
import com.example.planner.onetimetoken.InvalidOneTimeTokenException;
import com.example.planner.onetimetoken.OneTimeTokenService;
import com.example.planner.onetimetoken.TokenPurpose;
import com.example.planner.user.EmailAddress;
import com.example.planner.user.PasswordCredential;
import com.example.planner.user.PasswordCredentialRepository;
import com.example.planner.user.PasswordHasher;
import com.example.planner.user.Role;
import com.example.planner.user.User;
import com.example.planner.user.UserNotFoundException;
import com.example.planner.user.UserRepository;
import com.example.planner.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestcontainersConfiguration.class, ClockConfiguration.class,
        AdminUserService.class, OneTimeTokenService.class, PasswordHasher.class})
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

    @Test
    void blocksUser() {
        User student = activeUser("ivan@mail.ru");

        admin.block(student.getId());

        assertThat(reload(student).getStatus()).isEqualTo(UserStatus.BLOCKED);
    }

    /** Неиспользованная ссылка — это отложенный вход: заблокировали, а он активировался через час. */
    @Test
    void blockingRevokesPendingLinks() {
        User invited = invitedUser("new@college.ru");
        String activationLink = tokenService.issue(invited, TokenPurpose.INVITE);

        admin.block(invited.getId());

        assertThatThrownBy(() -> tokenService.consume(activationLink, TokenPurpose.INVITE))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    @Test
    void unblockingRestoresActiveAccount() {
        User student = activeUser("ivan@mail.ru");
        admin.block(student.getId());

        admin.unblock(student.getId());

        assertThat(reload(student).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    /** Без пароля «активный» аккаунт выглядел бы исправным, а войти в него было бы нельзя. */
    @Test
    void unblockingAccountWithoutPasswordReturnsItToInvited() {
        User invited = invitedUser("new@college.ru");
        admin.block(invited.getId());

        admin.unblock(invited.getId());

        assertThat(reload(invited).getStatus()).isEqualTo(UserStatus.INVITED);
    }

    @Test
    void refusesToUnblockUserThatIsNotBlocked() {
        User student = activeUser("ivan@mail.ru");

        assertThatThrownBy(() -> admin.unblock(student.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void grantsAndRevokesRole() {
        User student = activeUser("ivan@mail.ru");

        admin.grantRole(student.getId(), Role.CURATOR);
        assertThat(reload(student).getRoles()).containsExactly(Role.CURATOR);

        admin.revokeRole(student.getId(), Role.CURATOR);
        assertThat(reload(student).getRoles()).isEmpty();
    }

    @Test
    void refusesToRevokeAdminRoleFromTheLastAdmin() {
        User onlyAdmin = activeUser("boss@college.ru");
        admin.grantRole(onlyAdmin.getId(), Role.ADMIN);

        assertThatThrownBy(() -> admin.revokeRole(onlyAdmin.getId(), Role.ADMIN))
                .isInstanceOf(LastAdminException.class);
        assertThat(reload(onlyAdmin).hasRole(Role.ADMIN)).isTrue();
    }

    @Test
    void refusesToBlockTheLastAdmin() {
        User onlyAdmin = activeUser("boss@college.ru");
        admin.grantRole(onlyAdmin.getId(), Role.ADMIN);

        assertThatThrownBy(() -> admin.block(onlyAdmin.getId()))
                .isInstanceOf(LastAdminException.class);
        assertThat(reload(onlyAdmin).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void allowsRevokingAdminRoleWhenAnotherAdminRemains() {
        User first = activeUser("boss@college.ru");
        User second = activeUser("deputy@college.ru");
        admin.grantRole(first.getId(), Role.ADMIN);
        admin.grantRole(second.getId(), Role.ADMIN);

        admin.revokeRole(first.getId(), Role.ADMIN);

        assertThat(reload(first).hasRole(Role.ADMIN)).isFalse();
    }

    /** Заблокированный администратор — всё равно что отсутствующий, поэтому последним не считается. */
    @Test
    void blockedAdminDoesNotCountAsRemainingAdmin() {
        User working = activeUser("boss@college.ru");
        User blocked = activeUser("retired@college.ru");
        admin.grantRole(working.getId(), Role.ADMIN);
        admin.grantRole(blocked.getId(), Role.ADMIN);
        admin.block(blocked.getId());

        assertThatThrownBy(() -> admin.block(working.getId()))
                .isInstanceOf(LastAdminException.class);
    }

    /** Защита касается только роли ADMIN: остальные роли снимаются свободно. */
    @Test
    void protectionAppliesOnlyToTheAdminRole() {
        User onlyAdmin = activeUser("boss@college.ru");
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

    private User activeUser(String email) {
        User user = User.selfRegistered(new EmailAddress(email), "Кто-то");
        user.verifyEmail();
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
