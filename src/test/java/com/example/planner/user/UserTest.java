package com.example.planner.user;

import org.junit.jupiter.api.Test;

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
    void cannotVerifyEmailTwice() {
        user.verifyEmail();

        assertThatThrownBy(user::verifyEmail).isInstanceOf(IllegalStateException.class);
    }
}
