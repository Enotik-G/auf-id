package com.example.planner.captcha;

import org.altcha.altcha.v2.Altcha;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Решает задачку капчи так же, как это делает виджет ALTCHA в браузере. */
public final class CaptchaTestSupport {

    private CaptchaTestSupport() {
    }

    /** @return значение поля формы {@code altcha}: base64 от JSON «задачка + ответ» */
    public static String solve(Altcha.Challenge challenge) {
        try {
            Altcha.Solution solution = Altcha.solveChallenge(challenge, Altcha.pbkdf2());
            String payload = """
                    {"challenge":%s,"solution":{"counter":%d,"derivedKey":"%s"}}"""
                    .formatted(challenge.toJson(), solution.counter(), solution.derivedKey());
            return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
