package com.example.planner.user;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private final User user = User.selfRegistered(new EmailAddress("ivan@mail.ru"), "Иван Петров");

    @Test
    void selfRegisteredUserAwaitsVerification() {
        assertThat(user.isAwaitingEmailVerification()).isTrue();
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verifyingEmailActivatesAccount() {
        user.verifyEmail();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.isAwaitingEmailVerification()).isFalse();
    }

    @Test
    void newUserHasNeverLoggedIn() {
        assertThat(user.getLastLoginAt()).isNull();
    }

    @Test
    void recordsLoginTime() {
        Instant loginAt = Instant.parse("2026-09-29T08:30:00Z");

        user.recordLogin(loginAt);

        assertThat(user.getLastLoginAt()).isEqualTo(loginAt);
    }

    @Test
    void cannotVerifyEmailTwice() {
        user.verifyEmail();

        assertThatThrownBy(user::verifyEmail).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void newUserHasNoRoles() {
        assertThat(user.getRoles()).isEmpty();
        assertThat(user.hasRole(Role.STUDENT)).isFalse();
    }

    @Test
    void grantsRole() {
        user.grantRole(Role.CURATOR);

        assertThat(user.hasRole(Role.CURATOR)).isTrue();
        assertThat(user.getRoles()).containsExactly(Role.CURATOR);
    }

    @Test
    void grantingTheSameRoleTwiceChangesNothing() {
        user.grantRole(Role.ADMIN);
        user.grantRole(Role.ADMIN);

        assertThat(user.getRoles()).containsExactly(Role.ADMIN);
    }

    @Test
    void revokesRole() {
        user.grantRole(Role.ADMIN);
        user.revokeRole(Role.ADMIN);

        assertThat(user.hasRole(Role.ADMIN)).isFalse();
        assertThat(user.getRoles()).isEmpty();
    }

    @Test
    void revokingARoleTheUserDoesNotHaveChangesNothing() {
        user.revokeRole(Role.ADMIN);

        assertThat(user.getRoles()).isEmpty();
    }

    /** Роли меняют только через grantRole/revokeRole — иначе проверки обойдут мимо сущности. */
    @Test
    void rolesCannotBeChangedThroughTheGetter() {
        assertThatThrownBy(() -> user.getRoles().add(Role.ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invitedUserWaitsForActivationNotForEmailConfirmation() {
        User invited = User.invited(new EmailAddress("student@college.ru"), "Иван Иванов");

        assertThat(invited.getStatus()).isEqualTo(UserStatus.INVITED);
        assertThat(invited.isAwaitingEmailVerification()).isFalse();
        assertThat(invited.getRoles()).isEmpty();
    }

    /** Почту админ не подтверждал: он назначил адрес, а доступа к ящику у студента нет. */
    @Test
    void invitedUserHasUnverifiedEmail() {
        User invited = User.invited(new EmailAddress("student@college.ru"), "Иван Иванов");

        assertThat(invited.isEmailVerified()).isFalse();
    }

    /** Путь подтверждения почты к выданным учёткам не относится — их активируют по ссылке. */
    @Test
    void invitedUserCannotGoThroughEmailConfirmation() {
        User invited = User.invited(new EmailAddress("student@college.ru"), "Иван Иванов");

        assertThatThrownBy(invited::verifyEmail).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void activationMakesInvitedUserActive() {
        User invited = User.invited(new EmailAddress("student@college.ru"), "Иван Иванов");

        invited.activate();

        assertThat(invited.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void cannotActivateTwice() {
        User invited = User.invited(new EmailAddress("student@college.ru"), "Иван Иванов");
        invited.activate();

        assertThatThrownBy(invited::activate).isInstanceOf(IllegalStateException.class);
    }

    /** Путь активации — только для выданных админом учёток; саморегистрация идёт через verifyEmail. */
    @Test
    void cannotActivateSelfRegisteredUser() {
        assertThatThrownBy(user::activate).isInstanceOf(IllegalStateException.class);
    }

    /** Переход по ссылке доказывает получение ссылки, а не владение ящиком. */
    @Test
    void activationDoesNotVerifyEmail() {
        User invited = User.invited(new EmailAddress("student@college.ru"), "Иван Иванов");

        invited.activate();

        assertThat(invited.isEmailVerified()).isFalse();
    }
}
