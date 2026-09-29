package com.example.planner.login;

import com.example.planner.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Записывает время последнего входа. Spring Security после каждого успешного входа публикует
 * событие {@link AuthenticationSuccessEvent} — мы его слушаем, а код входа об этом ничего не знает.
 */
@Component
@RequiredArgsConstructor
public class LastLoginRecorder {

    private final UserRepository userRepository;
    private final Clock clock;

    @EventListener
    @Transactional
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        // То же событие публикуется, когда вход выполняет не человек, а сервис-клиент
        // (например, планировщик обменивает код на токен) — такие входы нас не касаются.
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        // Имя вошедшего — его id (см. AccountUserDetailsService).
        UUID userId = UUID.fromString(event.getAuthentication().getName());
        userRepository.findById(userId)
                .ifPresent(user -> user.recordLogin(Instant.now(clock)));
    }
}
