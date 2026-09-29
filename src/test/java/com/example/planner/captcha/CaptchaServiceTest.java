package com.example.planner.captcha;

import com.example.planner.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Import({TestcontainersConfiguration.class, CaptchaService.class})
class CaptchaServiceTest {

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearRedis() {
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void solvedChallengeIsAccepted() {
        String payload = CaptchaTestSupport.solve(captchaService.createChallenge());

        assertThat(captchaService.isSolved(payload)).isTrue();
    }

    @Test
    void sameSolutionIsAcceptedOnlyOnce() {
        String payload = CaptchaTestSupport.solve(captchaService.createChallenge());
        captchaService.isSolved(payload);

        assertThat(captchaService.isSolved(payload)).isFalse();
    }

    @Test
    void challengeSignedWithAnotherSecretIsRejected() {
        CaptchaService forger = new CaptchaService("attacker-secret-that-is-not-ours-000", redis);
        String payload = CaptchaTestSupport.solve(forger.createChallenge());

        assertThat(captchaService.isSolved(payload)).isFalse();
    }

    @Test
    void missingOrGarbagePayloadIsRejected() {
        assertThat(captchaService.isSolved(null)).isFalse();
        assertThat(captchaService.isSolved("")).isFalse();
        assertThat(captchaService.isSolved("not-a-captcha-at-all")).isFalse();
    }
}
