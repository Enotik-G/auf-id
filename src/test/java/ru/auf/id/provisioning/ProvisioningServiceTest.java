package ru.auf.id.provisioning;

import ru.auf.id.ClockConfiguration;
import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.onetimetoken.OneTimeTokenRepository;
import ru.auf.id.onetimetoken.InvalidOneTimeTokenException;
import ru.auf.id.onetimetoken.OneTimeTokenService;
import ru.auf.id.onetimetoken.TokenPurpose;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.PasswordCredentialRepository;
import ru.auf.id.user.PasswordHasher;
import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import ru.auf.id.user.UserNotFoundException;
import ru.auf.id.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestcontainersConfiguration.class, ClockConfiguration.class,
        ProvisioningService.class, OneTimeTokenService.class, PasswordHasher.class})
class ProvisioningServiceTest {

    private static final EmailAddress EMAIL = new EmailAddress("student@sinhub.ru");

    @Autowired
    private ProvisioningService provisioning;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OneTimeTokenRepository tokenRepository;

    @Autowired
    private PasswordCredentialRepository credentialRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private OneTimeTokenService tokenService;

    @Test
    void createsAccountAwaitingActivation() {
        Invitation invitation = provisioning.invite(EMAIL, "Иван Иванов", Set.of(Role.STUDENT));

        User created = userRepository.findById(invitation.userId()).orElseThrow();
        assertThat(created.getEmail()).isEqualTo(EMAIL);
        assertThat(created.getFullName()).isEqualTo("Иван Иванов");
        assertThat(created.getStatus()).isEqualTo(UserStatus.INVITED);
        assertThat(created.getRoles()).containsExactly(Role.STUDENT);
    }

    /** Войти в выданную учётку нельзя, пока владелец не задал пароль по ссылке. */
    @Test
    void createsNoPasswordUpfront() {
        Invitation invitation = provisioning.invite(EMAIL, "Иван Иванов", Set.of());

        assertThat(credentialRepository.findById(invitation.userId())).isEmpty();
    }

    @Test
    void issuesActivationTokenStoredOnlyAsHash() {
        Invitation invitation = provisioning.invite(EMAIL, "Иван Иванов", Set.of());

        assertThat(invitation.activationToken()).isNotBlank();
        assertThat(tokenRepository.findAll())
                .singleElement()
                .satisfies(token -> {
                    assertThat(token.getPurpose()).isEqualTo(TokenPurpose.INVITE);
                    assertThat(token.getUsedAt()).isNull();
                    // В базе лежит SHA-256 в hex, а не сам токен.
                    assertThat(token.getTokenHash()).hasSize(64).isNotEqualTo(invitation.activationToken());
                });
    }

    @Test
    void grantsSeveralRolesAtOnce() {
        Invitation invitation = provisioning.invite(EMAIL, "Олег Сидоров", Set.of(Role.CURATOR, Role.STUDENT));

        assertThat(userRepository.findById(invitation.userId()).orElseThrow().getRoles())
                .containsExactlyInAnyOrder(Role.CURATOR, Role.STUDENT);
    }

    @Test
    void allowsAccountWithoutRoles() {
        Invitation invitation = provisioning.invite(EMAIL, "Без роли", Set.of());

        assertThat(userRepository.findById(invitation.userId()).orElseThrow().getRoles()).isEmpty();
    }

    /** Администратору, в отличие от формы саморегистрации, скрывать занятую почту незачем. */
    @Test
    void refusesEmailThatIsAlreadyTaken() {
        provisioning.invite(EMAIL, "Иван Иванов", Set.of());

        assertThatThrownBy(() -> provisioning.invite(EMAIL, "Другой Иван", Set.of()))
                .isInstanceOf(EmailAlreadyTakenException.class)
                .hasMessageContaining(EMAIL.value());
    }

    @Test
    void refusesEmailTakenInAnotherLetterCase() {
        provisioning.invite(EMAIL, "Иван Иванов", Set.of());

        assertThatThrownBy(() -> provisioning.invite(new EmailAddress("Student@Sinhub.RU"), "Иван", Set.of()))
                .isInstanceOf(EmailAlreadyTakenException.class);
    }

    /** Почту никто не подтверждал: админ назначил адрес, а доступа к ящику у студента нет. */
    @Test
    void doesNotClaimTheEmailIsVerified() {
        Invitation invitation = provisioning.invite(EMAIL, "Иван Иванов", Set.of());

        assertThat(userRepository.findById(invitation.userId()).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void activationSetsPasswordAndActivatesAccount() {
        Invitation invitation = provisioning.invite(EMAIL, "Иван Иванов", Set.of(Role.STUDENT));

        provisioning.activate(invitation.activationToken(), "correct horse battery staple");

        User activated = userRepository.findById(invitation.userId()).orElseThrow();
        assertThat(activated.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(activated.getRoles()).containsExactly(Role.STUDENT);
        assertThat(credentialRepository.findById(invitation.userId()))
                .get()
                .satisfies(credential ->
                        assertThat(passwordHasher.matches("correct horse battery staple", credential.getPasswordHash()))
                                .isTrue());
    }

    /** Перешедший по ссылке не владеет ящиком — админ лишь назначил ему адрес. */
    @Test
    void activationDoesNotMarkEmailAsVerified() {
        Invitation invitation = provisioning.invite(EMAIL, "Иван Иванов", Set.of());

        provisioning.activate(invitation.activationToken(), "correct horse battery staple");

        assertThat(userRepository.findById(invitation.userId()).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void sameLinkCannotBeUsedTwice() {
        Invitation invitation = provisioning.invite(EMAIL, "Иван Иванов", Set.of());
        provisioning.activate(invitation.activationToken(), "correct horse battery staple");

        assertThatThrownBy(() -> provisioning.activate(invitation.activationToken(), "another password"))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    @Test
    void refusesUnknownToken() {
        assertThatThrownBy(() -> provisioning.activate("never-issued", "correct horse battery staple"))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    /** Токен сброса пароля не должен открывать активацию, и наоборот. */
    @Test
    void refusesTokenIssuedForAnotherPurpose() {
        Invitation invitation = provisioning.invite(EMAIL, "Иван Иванов", Set.of());
        User user = userRepository.findById(invitation.userId()).orElseThrow();
        String resetToken = tokenService.issue(user, TokenPurpose.PASSWORD_RESET);

        assertThatThrownBy(() -> provisioning.activate(resetToken, "correct horse battery staple"))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    /**
     * Вторая ссылка, выданная до активации, после неё уже не работает: статус проверяется отдельно от
     * токена, иначе активированный аккаунт можно было бы «активировать» повторно с новым паролем.
     */
    @Test
    void refusesActivationOfAnAlreadyActiveAccount() {
        Invitation first = provisioning.invite(EMAIL, "Иван Иванов", Set.of());
        User user = userRepository.findById(first.userId()).orElseThrow();
        String secondLink = tokenService.issue(user, TokenPurpose.INVITE);
        provisioning.activate(first.activationToken(), "correct horse battery staple");

        assertThatThrownBy(() -> provisioning.activate(secondLink, "attacker password"))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    @Test
    void reissuedLinkWorksAndThePreviousOneStopsWorking() {
        Invitation first = provisioning.invite(EMAIL, "Иван Иванов", Set.of(Role.STUDENT));

        Invitation second = provisioning.reissueInvitation(first.userId());

        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(second.activationToken()).isNotEqualTo(first.activationToken());

        assertThatThrownBy(() -> provisioning.activate(first.activationToken(), "correct horse battery staple"))
                .isInstanceOf(InvalidOneTimeTokenException.class);

        provisioning.activate(second.activationToken(), "correct horse battery staple");
        assertThat(userRepository.findById(first.userId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    /** Старая ссылка могла уйти не туда — именно поэтому и просят новую. */
    @Test
    void reissueRevokesEveryPreviousLink() {
        Invitation first = provisioning.invite(EMAIL, "Иван Иванов", Set.of());
        Invitation second = provisioning.reissueInvitation(first.userId());
        Invitation third = provisioning.reissueInvitation(first.userId());

        assertThatThrownBy(() -> provisioning.activate(first.activationToken(), "correct horse battery staple"))
                .isInstanceOf(InvalidOneTimeTokenException.class);
        assertThatThrownBy(() -> provisioning.activate(second.activationToken(), "correct horse battery staple"))
                .isInstanceOf(InvalidOneTimeTokenException.class);

        provisioning.activate(third.activationToken(), "correct horse battery staple");
        assertThat(userRepository.findById(first.userId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void refusesReissueForUnknownUser() {
        assertThatThrownBy(() -> provisioning.reissueInvitation(UUID.randomUUID()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void refusesReissueForAlreadyActiveAccount() {
        Invitation invitation = provisioning.invite(EMAIL, "Иван Иванов", Set.of());
        provisioning.activate(invitation.activationToken(), "correct horse battery staple");

        assertThatThrownBy(() -> provisioning.reissueInvitation(invitation.userId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE");
    }
}
