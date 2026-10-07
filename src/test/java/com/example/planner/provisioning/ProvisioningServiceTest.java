package com.example.planner.provisioning;

import com.example.planner.ClockConfiguration;
import com.example.planner.TestcontainersConfiguration;
import com.example.planner.onetimetoken.OneTimeTokenRepository;
import com.example.planner.onetimetoken.OneTimeTokenService;
import com.example.planner.onetimetoken.TokenPurpose;
import com.example.planner.user.EmailAddress;
import com.example.planner.user.PasswordCredentialRepository;
import com.example.planner.user.Role;
import com.example.planner.user.User;
import com.example.planner.user.UserRepository;
import com.example.planner.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestcontainersConfiguration.class, ClockConfiguration.class,
        ProvisioningService.class, OneTimeTokenService.class})
class ProvisioningServiceTest {

    private static final EmailAddress EMAIL = new EmailAddress("student@college.ru");

    @Autowired
    private ProvisioningService provisioning;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OneTimeTokenRepository tokenRepository;

    @Autowired
    private PasswordCredentialRepository credentialRepository;

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

        assertThatThrownBy(() -> provisioning.invite(new EmailAddress("Student@College.RU"), "Иван", Set.of()))
                .isInstanceOf(EmailAlreadyTakenException.class);
    }

    /** Почту никто не подтверждал: админ назначил адрес, а доступа к ящику у студента нет. */
    @Test
    void doesNotClaimTheEmailIsVerified() {
        Invitation invitation = provisioning.invite(EMAIL, "Иван Иванов", Set.of());

        assertThat(userRepository.findById(invitation.userId()).orElseThrow().isEmailVerified()).isFalse();
    }
}
