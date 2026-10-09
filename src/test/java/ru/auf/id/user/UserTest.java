package ru.auf.id.user;

import ru.auf.id.TestTime;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private final User user = User.invited(new EmailAddress("ivan@sinhub.ru"), "Иван Петров", TestTime.NOW);

    @Test
    void invitedUserWaitsForActivation() {
        assertThat(user.getStatus()).isEqualTo(UserStatus.INVITED);
        assertThat(user.getRoles()).isEmpty();
        assertThat(user.getLastLoginAt()).isNull();
    }

    /** Почту админ не подтверждал: он назначил адрес, а доступа к ящику у студента нет. */
    @Test
    void invitedUserHasUnverifiedEmail() {
        assertThat(user.isEmailVerified()).isFalse();
    }

    // ─────────────────────────── активация ───────────────────────────

    @Test
    void activationMakesInvitedUserActive() {
        user.activate();

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void cannotActivateTwice() {
        user.activate();

        assertThatThrownBy(user::activate).isInstanceOf(IllegalStateException.class);
    }

    /** Переход по ссылке доказывает получение ссылки от администратора, а не владение ящиком. */
    @Test
    void activationDoesNotVerifyEmail() {
        user.activate();

        assertThat(user.isEmailVerified()).isFalse();
    }

    // ─────────────────────────── вход ───────────────────────────

    @Test
    void recordsLoginTime() {
        Instant loginAt = Instant.parse("2026-09-29T08:30:00Z");

        user.recordLogin(loginAt);

        assertThat(user.getLastLoginAt()).isEqualTo(loginAt);
    }

    // ─────────────────────────── блокировка ───────────────────────────

    @Test
    void blockClosesAccessFromAnyLivingStatus() {
        user.block();
        assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);

        User active = User.invited(new EmailAddress("oleg@sinhub.ru"), "Олег Сидоров", TestTime.NOW);
        active.activate();
        active.block();
        assertThat(active.getStatus()).isEqualTo(UserStatus.BLOCKED);
    }

    @Test
    void unblockReturnsAccountToTheRequestedStatus() {
        user.activate();
        user.block();

        user.unblock(UserStatus.ACTIVE);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void cannotUnblockAccountThatIsNotBlocked() {
        assertThatThrownBy(() -> user.unblock(UserStatus.ACTIVE)).isInstanceOf(IllegalStateException.class);
    }

    /** Разблокировка возвращает только в рабочие состояния — не в LOCKED и не в DELETED. */
    @Test
    void cannotUnblockIntoAnArbitraryStatus() {
        user.block();

        assertThatThrownBy(() -> user.unblock(UserStatus.LOCKED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.unblock(UserStatus.DELETED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotActivateBlockedAccount() {
        user.block();

        assertThatThrownBy(user::activate).isInstanceOf(IllegalStateException.class);
    }

    // ─────────────────────────── роли ───────────────────────────

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
}
