package com.example.planner.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Хеширует и проверяет пароли: HMAC-SHA256 с «перцем» (секрет вне БД), затем Argon2id.
 * Перец берётся из переменной окружения PASSWORD_PEPPER; без него приложение не стартует.
 */
@Component
public class PasswordHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_PEPPER_LENGTH = 32;

    // Параметры Argon2id по рекомендации OWASP: 19 МиБ памяти, 2 прохода, 1 поток.
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BYTES = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19_456;
    private static final int ITERATIONS = 2;

    private final Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(
            SALT_LENGTH_BYTES, HASH_LENGTH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS);

    private final SecretKeySpec pepperKey;

    public PasswordHasher(@Value("${auth.password.pepper}") String pepper) {
        if (pepper.length() < MIN_PEPPER_LENGTH) {
            throw new IllegalStateException(
                    "Перец для паролей (PASSWORD_PEPPER) должен быть не короче " + MIN_PEPPER_LENGTH + " символов");
        }
        this.pepperKey = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public String hash(String rawPassword) {
        return argon2.encode(applyPepper(rawPassword));
    }

    public boolean matches(String rawPassword, String storedHash) {
        return argon2.matches(applyPepper(rawPassword), storedHash);
    }

    private String applyPepper(String rawPassword) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(pepperKey);
            byte[] peppered = mac.doFinal(rawPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(peppered);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 недоступен в этой JVM", e);
        }
    }
}
