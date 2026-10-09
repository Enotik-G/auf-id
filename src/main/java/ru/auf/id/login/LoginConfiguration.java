package ru.auf.id.login;

import ru.auf.id.captcha.CaptchaService;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;

/**
 * Порядок проверок при входе.
 *
 * <p>По умолчанию Spring сначала проверяет статус аккаунта, потом пароль. Тогда «доступ закрыт»
 * можно было бы узнать, введя <i>любой</i> пароль, — то есть проверить, заведён ли чужой адрес в
 * системе. Поэтому неактивный аккаунт (не {@code ACTIVE} — disabled) проверяем <b>после</b> пароля:
 * подсказку увидит только тот, кто знает пароль.
 *
 * <p>Статус {@code LOCKED} (locked) — <b>до</b> пароля: отказ всегда один и тот же, верен пароль или нет,
 * так что при подборе закрытый аккаунт не подсказывает, что догадка верна. Сейчас в этот статус не
 * переводит ничто (см. {@code UserStatus.LOCKED}), проверка — на будущее. Время ответа при этом
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
                throw new LockedException("Аккаунт закрыт");
            }
        });
        provider.setPostAuthenticationChecks(user -> {
            if (!user.isEnabled()) {
                throw new DisabledException("Аккаунт не активен");
            }
        });
        return provider;
    }

    @Bean
    FilterRegistrationBean<LoginCaptchaFilter> loginCaptchaFilter(LoginAttemptService loginAttempts,
                                                                 CaptchaService captchaService) {
        FilterRegistrationBean<LoginCaptchaFilter> registration =
                new FilterRegistrationBean<>(new LoginCaptchaFilter(loginAttempts, captchaService));
        registration.addUrlPatterns(LoginCaptchaFilter.LOGIN_PATH);
        // После лимита по IP (он первый, SecurityFilterProperties.DEFAULT_FILTER_ORDER - 2) и перед Spring Security.
        registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
        return registration;
    }
}
