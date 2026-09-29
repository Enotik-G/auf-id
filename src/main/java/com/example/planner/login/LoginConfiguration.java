package com.example.planner.login;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

/**
 * Порядок проверок при входе.
 *
 * <p>По умолчанию Spring сначала проверяет статус аккаунта, потом пароль. Тогда «почта не подтверждена»
 * можно было бы узнать, введя <i>любой</i> пароль, — то есть проверить, зарегистрирован ли чужой адрес.
 * Поэтому «почта не подтверждена» (disabled) проверяем <b>после</b> пароля: подсказку увидит только тот,
 * кто знает пароль.
 *
 * <p>Временная блокировка (locked) — наоборот, <b>до</b> пароля: иначе при подборе пароля
 * заблокированный аккаунт сообщал бы, что очередная догадка верна.
 */
@Configuration(proxyBeanMethods = false)
public class LoginConfiguration {

    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider(AccountUserDetailsService userDetailsService,
                                                        PepperedPasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        // Хеш-«пустышка» для выравнивания времени ответа (считается один раз при запуске).
        String timingEqualizerHash = passwordEncoder.encode("timing-equalizer");

        provider.setPreAuthenticationChecks(user -> {
            if (!user.isAccountNonLocked()) {
                // Заблокированный аккаунт отказывает до проверки пароля — без этой строки он отвечал бы
                // быстрее (Argon2 не выполняется), и по времени ответа можно было бы понять, что он заблокирован.
                // Время Argon2 не зависит от пароля (перед ним HMAC), поэтому одной проверки пустышки достаточно.
                passwordEncoder.matches("", timingEqualizerHash);
                throw new LockedException("Аккаунт временно заблокирован");
            }
        });
        provider.setPostAuthenticationChecks(user -> {
            if (!user.isEnabled()) {
                throw new DisabledException("Аккаунт не активен");
            }
        });
        return provider;
    }
}
