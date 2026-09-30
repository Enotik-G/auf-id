package com.example.planner.authserver;

import com.example.planner.user.User;
import com.example.planner.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Личные поля пользователя в токене: {@code name}, {@code email}, {@code email_verified}.
 *
 * <p>Нужны, чтобы сервисы экосистемы показывали, кто вошёл, не спрашивая Auth по сети на каждый
 * запрос — в этом и смысл самопроверяемого JWT.
 *
 * <p>Имена полей и их привязка к scope — из стандарта OpenID Connect Core (раздел 5.4):
 * {@code name} отдаётся при scope {@code profile}, а {@code email} и {@code email_verified} —
 * при scope {@code email}. Клиент, который scope не запросил, личных данных не получает.
 */
@Component
@RequiredArgsConstructor
public class UserClaims {

    private final UserRepository users;

    public void addTo(JwtEncodingContext context) {
        // Токен для сервиса (client_credentials) выдаётся без участия человека — личных полей в нём нет.
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
            return;
        }
        findUser(context.getPrincipal().getName()).ifPresent(user -> addTo(context, user));
    }

    private void addTo(JwtEncodingContext context, User user) {
        Set<String> scopes = context.getAuthorizedScopes();

        if (scopes.contains(OidcScopes.PROFILE)) {
            context.getClaims().claim("name", user.getFullName());
        }
        if (scopes.contains(OidcScopes.EMAIL)) {
            context.getClaims()
                    .claim("email", user.getEmail().value())
                    .claim("email_verified", user.isEmailVerified());
        }
    }

    /**
     * Владелец токена назван своим <b>id</b> — так решено во входе: id не меняется никогда, а почту
     * можно сменить. Если там не UUID, токен выдан не человеку, и личные поля добавлять нечему.
     */
    private Optional<User> findUser(String principalName) {
        try {
            return users.findById(UUID.fromString(principalName));
        } catch (IllegalArgumentException notAUserId) {
            return Optional.empty();
        }
    }
}
