package com.example.planner.login;

import com.example.planner.user.EmailAddress;
import com.example.planner.user.InvalidEmailException;
import com.example.planner.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
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
     * Неверный пароль (или несуществующая почта — Spring не различает их намеренно).
     * В событии — то, что человек ввёл в поле логина, то есть почта; ищем по ней пользователя.
     * Для несуществующей почты считать нечего — от перебора таких адресов защищает лимит по IP.
     */
    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String enteredEmail = event.getAuthentication().getName();
        try {
            userRepository.findByEmail(new EmailAddress(enteredEmail))
                    .ifPresent(user -> loginAttempts.recordFailure(user.getId()));
        } catch (InvalidEmailException ignored) {
            // Мусор вместо почты — такого пользователя точно нет.
        }
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        // После успешного входа имя — уже id пользователя (см. AccountUserDetailsService).
        loginAttempts.recordSuccess(UUID.fromString(event.getAuthentication().getName()));
    }
}
