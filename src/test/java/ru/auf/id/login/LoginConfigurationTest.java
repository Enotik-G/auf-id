package ru.auf.id.login;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Поведение Spring Security, на которое опирается LoginConfiguration, — на заглушках, без базы. */
class LoginConfigurationTest {

    private static final String REAL_HASH = "$argon2id$real";

    private final AccountUserDetailsService userDetailsService = mock(AccountUserDetailsService.class);
    private final PepperedPasswordEncoder passwordEncoder = mock(PepperedPasswordEncoder.class);
    private final DaoAuthenticationProvider provider =
            new LoginConfiguration().daoAuthenticationProvider(userDetailsService, passwordEncoder);

    /** Время ответа заблокированного аккаунта — как у обычного: пароль проверяется ровно один раз. */
    @Test
    void lockedAccountSpendsSameTimeOnPasswordCheckAsAnyOther() {
        lockedUser();
        when(passwordEncoder.matches("wrong password", REAL_HASH)).thenReturn(false);

        assertThatThrownBy(() -> login("wrong password")).isInstanceOf(LockedException.class);

        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

    /** Верный пароль заблокированного аккаунта даёт тот же отказ, что и неверный, — догадку не подтвердить. */
    @Test
    void lockedAccountRejectsEvenCorrectPasswordTheSameWay() {
        lockedUser();
        when(passwordEncoder.matches("correct password", REAL_HASH)).thenReturn(true);

        assertThatThrownBy(() -> login("correct password")).isInstanceOf(LockedException.class);
    }

    private void lockedUser() {
        when(userDetailsService.loadUserByUsername("ivan@sinhub.ru")).thenReturn(User.withUsername("some-id")
                .password(REAL_HASH).accountLocked(true).build());
    }

    private void login(String password) {
        provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("ivan@sinhub.ru", password));
    }
}
