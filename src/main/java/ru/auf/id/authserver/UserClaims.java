package ru.auf.id.authserver;

import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Личные поля пользователя — в токене и в ответе {@code /userinfo}: {@code name}, {@code email},
 * {@code email_verified}, плюс {@code roles} в access token.
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
        personalClaims(user, context.getAuthorizedScopes()).forEach(context.getClaims()::claim);

        // Роли нужны сервисам для решения «пускать или нет», поэтому только в access token:
        // id token описывает, кто вошёл, и правами не распоряжается.
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            context.getClaims().claim("roles", roleNames(user));
        }
    }

    /**
     * Профиль для {@code /userinfo} — <b>из таблицы {@code users}</b>, а не из claims, сохранённых
     * при входе (шаг 24).
     *
     * <p>Так Spring делает по умолчанию: берёт поля из сохранённого id_token. Из-за этого ФИО и
     * почта лежали в колонке {@code oidc_id_token_metadata} открытым текстом, и хеширование токенов
     * (шаг 12) их не закрывало. Теперь claims там не хранятся, а профиль собирается заново.
     *
     * <p>Побочный выигрыш: {@code /userinfo} отдаёт данные <b>на текущий момент</b>. Исправили ФИО
     * в админке — следующий запрос вернёт новое, не дожидаясь, пока человек войдёт заново. С
     * сохранёнными claims так не работало.
     *
     * <p>{@code sub} обязателен по стандарту (OpenID Connect Core, 5.3.2) и всегда присутствует.
     * Личные поля — по тем же правилам, что и в токене: один метод {@link #personalClaims}, чтобы
     * токен и {@code /userinfo} не разошлись.
     *
     * @param principalName владелец авторизации, то есть id пользователя строкой
     * @param scopes        права, выданные при входе
     */
    public OidcUserInfo userInfo(String principalName, Set<String> scopes) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(StandardClaimNames.SUB, principalName);
        findUser(principalName).ifPresent(user -> claims.putAll(personalClaims(user, scopes)));
        return new OidcUserInfo(claims);
    }

    /**
     * Какие личные поля достаются клиенту при каких правах — единственное место, где это решается.
     *
     * <p>Имена полей и привязка к scope — из стандарта OpenID Connect Core (раздел 5.4):
     * {@code name} при scope {@code profile}, {@code email} и {@code email_verified} при scope
     * {@code email}. Клиент, который scope не запросил, личных данных не получает.
     *
     * <p>{@code HashMap}, а не {@code Map.of} — по той же причине, что {@code ArrayList} в
     * {@link #roleNames}: неизменяемые коллекции JDK Jackson не восстанавливает при чтении
     * авторизации обратно из БД.
     */
    private static Map<String, Object> personalClaims(User user, Set<String> scopes) {
        Map<String, Object> claims = new HashMap<>();
        if (scopes.contains(OidcScopes.PROFILE)) {
            claims.put(StandardClaimNames.NAME, user.getFullName());
        }
        if (scopes.contains(OidcScopes.EMAIL)) {
            claims.put(StandardClaimNames.EMAIL, user.getEmail().value());
            claims.put(StandardClaimNames.EMAIL_VERIFIED, user.isEmailVerified());
        }
        return claims;
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
