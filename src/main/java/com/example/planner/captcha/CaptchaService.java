package com.example.planner.captcha;

import org.altcha.altcha.v2.Altcha;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Капча ALTCHA: сервер выдаёт браузеру задачку, браузер ~секунду подбирает к ней ответ (proof-of-work),
 * сервер проверяет ответ за миллисекунду. Человеку ничего решать не нужно, а боту каждая попытка
 * входа обходится в секунду работы процессора.
 *
 * <p>Задачка подписана нашим секретом (HMAC) — подсунуть свою, лёгкую, нельзя. Каждое решение
 * принимается только один раз (Redis) — иначе бот решил бы одну задачку и использовал её бесконечно.
 */
@Service
public class CaptchaService {

    /** Алгоритм, который виджет ALTCHA 3.x понимает «из коробки». */
    private static final String ALGORITHM = "PBKDF2/SHA-256";
    /** Сложность одной попытки подбора; вместе с ALTCHA по умолчанию — около секунды в браузере. */
    private static final int COST = 5_000;
    /** Сколько живёт задачка: за это время человек должен успеть ввести пароль. */
    static final Duration CHALLENGE_LIFETIME = Duration.ofMinutes(10);

    private final String hmacSecret;
    private final StringRedisTemplate redis;

    public CaptchaService(@Value("${auth.captcha.hmac-secret}") String hmacSecret, StringRedisTemplate redis) {
        this.hmacSecret = hmacSecret;
        this.redis = redis;
    }

    public Altcha.Challenge createChallenge() {
        try {
            return Altcha.createChallenge(new Altcha.CreateChallengeOptions()
                    .algorithm(ALGORITHM)
                    .cost(COST)
                    .deriveKey(Altcha.pbkdf2())
                    .expiresInSeconds(CHALLENGE_LIFETIME.toSeconds())
                    .hmacSignatureSecret(hmacSecret));
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось создать задачку капчи", e);
        }
    }

    /**
     * @param payload то, что виджет положил в поле формы {@code altcha} (base64 от JSON «задачка + ответ»)
     * @return true, если задачка наша, не просрочена, решена верно и это решение ещё не использовалось
     */
    public boolean isSolved(String payload) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        try {
            boolean verified = Altcha.verifySolution(payload, hmacSecret, Altcha.pbkdf2()).verified();
            return verified && isFirstUse(Altcha.parsePayload(payload).challenge().signature());
        } catch (Exception e) {
            // Мусор вместо ответа — не решено.
            return false;
        }
    }

    /** Запоминаем использованную задачку до конца её срока жизни; SET NX — атомарно «только если ещё нет». */
    private boolean isFirstUse(String challengeSignature) {
        Boolean stored = redis.opsForValue().setIfAbsent("captcha:used:" + challengeSignature, "1", CHALLENGE_LIFETIME);
        return Boolean.TRUE.equals(stored);
    }
}
