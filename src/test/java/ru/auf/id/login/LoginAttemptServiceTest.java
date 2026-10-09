package ru.auf.id.login;

import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.user.EmailAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Import({TestcontainersConfiguration.class, LoginAttemptService.class})
class LoginAttemptServiceTest {

    private static final EmailAddress IVAN = new EmailAddress("ivan@sinhub.ru");

    @Autowired
    private LoginAttemptService loginAttempts;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearRedis() {
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void twoFailuresDoNotRequireCaptcha() {
        failTimes(IVAN, 2);

        assertThat(loginAttempts.isCaptchaRequired(IVAN)).isFalse();
    }

    @Test
    void thirdFailureRequiresCaptcha() {
        failTimes(IVAN, 3);

        assertThat(loginAttempts.isCaptchaRequired(IVAN)).isTrue();
    }

    @Test
    void successResetsTheCount() {
        failTimes(IVAN, 3);
        loginAttempts.recordSuccess(IVAN);

        assertThat(loginAttempts.isCaptchaRequired(IVAN)).isFalse();
    }

    @Test
    void emailTypedDifferentlyIsTheSameCounter() {
        failTimes(new EmailAddress("IVAN@Sinhub.ru"), 3);

        assertThat(loginAttempts.isCaptchaRequired(IVAN)).isTrue();
    }

    @Test
    void failuresForOneEmailDoNotAffectAnother() {
        failTimes(IVAN, 3);

        assertThat(loginAttempts.isCaptchaRequired(new EmailAddress("petr@sinhub.ru"))).isFalse();
    }

    @Test
    void counterIsForgottenAfterADayAndEmailIsNotStoredInPlainText() {
        failTimes(IVAN, 1);

        String key = redis.keys("login:failures:*").iterator().next();
        assertThat(key).doesNotContain("ivan").hasSize("login:failures:".length() + 64);
        assertThat(redis.getExpire(key)).isBetween(23 * 3600L, 24 * 3600L);
    }

    private void failTimes(EmailAddress email, int times) {
        for (int i = 0; i < times; i++) {
            loginAttempts.recordFailure(email);
        }
    }
}
