package com.example.planner.login;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Порядок проверок при входе — без базы и Spring, на заглушках. */
class LoginConfigurationTest {

    private final AccountUserDetailsService userDetailsService = mock(AccountUserDetailsService.class);
    private final PepperedPasswordEncoder passwordEncoder = mock(PepperedPasswordEncoder.class);

    @Test
    void lockedAccountStillSpendsTimeOnPasswordHashing() {
        when(passwordEncoder.encode("timing-equalizer")).thenReturn("$argon2id$equalizer");
        when(userDetailsService.loadUserByUsername("ivan@mail.ru")).thenReturn(User.withUsername("some-id")
                .password("$argon2id$real").accountLocked(true).build());
        DaoAuthenticationProvider provider = new LoginConfiguration()
                .daoAuthenticationProvider(userDetailsService, passwordEncoder);

        assertThatThrownBy(() -> provider.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated("ivan@mail.ru", "any password")))
                .isInstanceOf(LockedException.class);

        // Argon2 всё равно выполнился — на пустышке, настоящий пароль не проверялся.
        verify(passwordEncoder).matches(eq(""), eq("$argon2id$equalizer"));
        verify(passwordEncoder, org.mockito.Mockito.never()).matches(anyString(), eq("$argon2id$real"));
    }
}
