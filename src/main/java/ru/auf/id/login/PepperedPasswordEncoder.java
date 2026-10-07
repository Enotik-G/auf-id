package ru.auf.id.login;

import ru.auf.id.user.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Переходник: Spring Security проверяет пароли через интерфейс {@link PasswordEncoder},
 * а у нас хеширование — в {@link PasswordHasher} (перец + Argon2id). Этот класс соединяет одно с другим.
 */
@Component
@RequiredArgsConstructor
public class PepperedPasswordEncoder implements PasswordEncoder {

    private final PasswordHasher passwordHasher;

    @Override
    public String encode(CharSequence rawPassword) {
        return passwordHasher.hash(rawPassword.toString());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String storedHash) {
        return passwordHasher.matches(rawPassword.toString(), storedHash);
    }
}
