package com.example.planner.authserver;

import com.example.planner.user.Role;
import com.example.planner.user.User;
import com.example.planner.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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
        // Роли нужны сервисам для решения «пускать или нет», поэтому только в access token:
        // id token описывает, кто вошёл, и правами не распоряжается.
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            context.getClaims().claim("roles", roleNames(user));
        }
    }

    /**
     * Отсортированы, чтобы claim не менялся от порядка строк в БД.
     *
     * <p>Именно {@code ArrayList}, а не {@code List.of} и не {@code stream().toList()}. Выданная
     * авторизация целиком сохраняется в {@code oauth2_authorization} как JSON, и при обратном чтении
     * (например, когда {@code /userinfo} поднимает токен из БД) Jackson отказывается восстанавливать
     * классы, которых нет в его списке разрешённых. Неизменяемые коллекции JDK — как раз такие:
     * {@code java.util.ImmutableCollections$ListN}. Ошибка возникает не при выдаче токена, а позже и
     * в другом месте, поэтому связать её с этой строкой почти невозможно.
     */
    private static List<String> roleNames(User user) {
        List<String> names = new ArrayList<>();
        user.getRoles().stream().map(Role::name).sorted().forEach(names::add);
        return names;
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
