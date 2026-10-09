package ru.auf.id;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 от строки в виде 64 шестнадцатеричных символов — для хранения того, что нельзя держать
 * открытым текстом: одноразовых ссылок, кодов и токенов, почт в ключах Redis.
 *
 * <p>Не шифрование и не хеш пароля: исходное значение из результата не восстановить, но и
 * подбирать его никто не будет — у токенов 256 бит случайности. Пароли хешируются иначе
 * ({@code PasswordHasher}, Argon2id), потому что их как раз подбирают.
 */
public final class Sha256 {

    private Sha256() {
    }

    public static String hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 обязан быть в любой Java (спецификация Java SE) — сюда не попадём.
            throw new IllegalStateException("SHA-256 недоступен в этой JVM", e);
        }
    }
}
