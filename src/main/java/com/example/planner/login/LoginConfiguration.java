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
 * <p>Временная блокировка (locked) — <b>до</b> пароля: отказ всегда один и тот же, верен пароль или нет,
 * так что при подборе заблокированный аккаунт не подсказывает, что догадка верна. Время ответа при этом
 * не отличается: Spring Security 7 даже после отказа на этой проверке всё равно проверяет пароль
 * (флаг {@code alwaysPerformAdditionalChecksOnUser}, включён по умолчанию) и отбрасывает результат.
 */
@Configuration(proxyBeanMethods = false)
public class LoginConfiguration {

    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider(AccountUserDetailsService userDetailsService,
                                                        PepperedPasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        provider.setPreAuthenticationChecks(user -> {
            if (!user.isAccountNonLocked()) {
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
