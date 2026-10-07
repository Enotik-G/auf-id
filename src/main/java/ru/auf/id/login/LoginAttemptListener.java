package ru.auf.id.login;

import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.InvalidEmailException;
import ru.auf.id.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Передаёт результаты входа в {@link LoginAttemptService}. */
@Component
@RequiredArgsConstructor
public class LoginAttemptListener {

    private final LoginAttemptService loginAttempts;
    private final UserRepository userRepository;

    /**
     * Неверный пароль или несуществующая почта — Spring намеренно их не различает, и мы тоже:
     * считаем неудачу по введённой почте в обоих случаях.
     */
    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        if (!isPersonLogin(event.getAuthentication())) {
            return;
        }
        String enteredEmail = event.getAuthentication().getName();
        try {
            loginAttempts.recordFailure(new EmailAddress(enteredEmail));
        } catch (InvalidEmailException ignored) {
            // Мусор вместо почты — такой адрес не зарегистрировать, считать нечего.
        }
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (!isPersonLogin(event.getAuthentication())) {
            return;
        }
        // После успешного входа имя — уже id пользователя (см. AccountUserDetailsService); по нему узнаём почту.
        UUID userId = UUID.fromString(event.getAuthentication().getName());
        userRepository.findById(userId)
                .ifPresent(user -> loginAttempts.recordSuccess(user.getEmail()));
    }

    /**
     * Вход человека по почте и паролю. Те же события Spring публикует и когда аутентифицируется
     * сервис-клиент сервера авторизации (например, планировщик на /oauth2/token) — их не считаем.
     */
    private static boolean isPersonLogin(Authentication authentication) {
        return authentication instanceof UsernamePasswordAuthenticationToken;
    }
}
