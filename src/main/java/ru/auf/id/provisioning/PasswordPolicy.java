package ru.auf.id.provisioning;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Какой пароль годится. Правила — из архитектурного документа (раздел 3): не короче 12 символов и
 * не из списка утёкших. Требований к составу («цифра, заглавная, спецсимвол») нет намеренно: по
 * NIST SP 800-63B они не помогают, а только подталкивают к {@code Password1!}.
 *
 * <p>Список утёкших — {@code passwords/common-passwords.txt}: пароли от 12 символов из самых частых
 * в утечках (SecLists, лицензия MIT — {@code passwords/LICENSE-SecLists.txt}). Лежит внутри
 * приложения, а не проверяется через внешний сервис: пароль (даже его кусок хеша) никуда не
 * уходит. Загружается один раз при старте, около 46 тысяч строк.
 *
 * <p>Сравнение без учёта регистра: {@code Qwertyuiop123} подбирают так же легко, как
 * {@code qwertyuiop123}.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final String COMMON_PASSWORDS = "passwords/common-passwords.txt";

    private final Set<String> commonPasswords = loadCommonPasswords();

    /** Итог проверки — по нему страница выбирает, что сказать человеку. */
    public enum Verdict {
        OK,
        /** Короче {@link #MIN_LENGTH} или длиннее {@link #MAX_LENGTH} символов. */
        WRONG_LENGTH,
        /** Есть в списке частых паролей из утечек — такой подберут первым. */
        COMMON
    }

    public Verdict check(String password) {
        // Символы, а не char: буква вне базовой плоскости Unicode (эмодзи) занимает два char.
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return Verdict.WRONG_LENGTH;
        }
        if (commonPasswords.contains(password.toLowerCase(Locale.ROOT))) {
            return Verdict.COMMON;
        }
        return Verdict.OK;
    }

    private static Set<String> loadCommonPasswords() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(COMMON_PASSWORDS).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(line -> line.toLowerCase(Locale.ROOT))
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            // Без списка приложение не стартует: тихо пропускать проверку хуже, чем упасть.
            throw new UncheckedIOException("Не удалось прочитать " + COMMON_PASSWORDS, e);
        }
    }
}
