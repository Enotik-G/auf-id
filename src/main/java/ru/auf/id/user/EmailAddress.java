package ru.auf.id.user;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Email в нормализованном виде: без пробелов по краям и в нижнем регистре.
 * Создать объект можно только через конструктор, поэтому ненормализованный
 * адрес в коде, принимающем {@code EmailAddress}, появиться не может.
 */
public record EmailAddress(String value) {

    private static final int MAX_LENGTH = 254;

    // Любые пробельные символы по краям, включая неразрывный пробел (\p{Z}),
    // который strip() не считает пробелом, а при копировании из документов он встречается.
    private static final Pattern EDGE_SPACES = Pattern.compile("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$");
    private static final Pattern ANY_SPACE = Pattern.compile("[\\s\\p{Z}]");

    public EmailAddress {
        if (value == null) {
            throw new InvalidEmailException("Email не указан");
        }

        value = EDGE_SPACES.matcher(value).replaceAll("").toLowerCase(Locale.ROOT);

        if (value.length() > MAX_LENGTH) {
            throw new InvalidEmailException("Email длиннее " + MAX_LENGTH + " символов");
        }

        if (ANY_SPACE.matcher(value).find()) {
            throw new InvalidEmailException("Email не может содержать пробелы");
        }

        int at = value.indexOf('@');
        boolean hasSingleAt = at > 0 && at == value.lastIndexOf('@') && at < value.length() - 1;
        if (!hasSingleAt) {
            throw new InvalidEmailException("Email должен иметь вид имя@домен");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
