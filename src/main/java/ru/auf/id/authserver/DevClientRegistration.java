package ru.auf.id.authserver;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Клиенты для локальной разработки: при запуске регистрируются в базе, если их там ещё нет.
 *
 * <p>Их <b>два</b>, потому что лаунчер и планировщик делаются одновременно (решение 2026-10-08) и
 * устроены по-разному. Один клиент на двоих означал бы, что одна из команд разрабатывает против
 * чужих настроек и обнаруживает это уже на сервере:
 * <ul>
 *   <li>{@value #LAUNCHER_CLIENT_ID} — вид {@code NATIVE}: возврат на локальный порт,
 *       refresh-токен на 30 дней с ротацией;</li>
 *   <li>{@value #PLANNER_CLIENT_ID} — вид {@code CONFIDENTIAL}: серверное приложение со своей
 *       сессией, refresh-токен ему не нужен (истёк access-токен — вкладка молча идёт на
 *       {@code /oauth2/authorize}, где её узнаёт сессия провайдера).</li>
 * </ul>
 *
 * <p><b>Планировщику клиент нужен не всегда.</b> Если python-микросервис только проверяет JWT и
 * сам никого не пускает внутрь (а вход делает фронтенд или шлюз перед ним) — он не клиент, а
 * <i>сервер ресурсов</i>: ему достаточно читать {@code jwks_uri} из discovery и сверять подпись,
 * {@code iss}, {@code aud} и {@code exp}. Регистрировать его в этом случае не нужно вовсе, а
 * клиентом станет тот, кто ведёт человека на страницу входа. Клиент
 * {@value #PLANNER_CLIENT_ID} здесь — для случая, когда вход делает сам планировщик.
 *
 * <p>Включается только явно: {@code auth.dev-client.enabled=true} (локально — в {@code .env},
 * в тестах — в тестовых настройках). На сервере выключен: адреса возврата на 127.0.0.1 там не нужны,
 * а настоящие клиенты регистрируются через админку (задача 9).
 */
@Component
@ConditionalOnProperty(name = "auth.dev-client.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevClientRegistration implements ApplicationRunner {

    /** Лаунчер: настольное приложение, возврат на порт, который оно занимает на время входа. */
    static final String LAUNCHER_CLIENT_ID = "launcher-dev";
    static final String LAUNCHER_REDIRECT_URI = "http://127.0.0.1:8090/login/oauth2/code/auth";

    /** Планировщик: серверное приложение, возврат на свой адрес. */
    static final String PLANNER_CLIENT_ID = "planner-dev";
    static final String PLANNER_REDIRECT_URI = "http://127.0.0.1:8000/auth/callback";

    /** Access token живёт 10 минут: сервисы проверяют его сами, без запроса в Auth (решение архитектуры). */
    static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(10);
    /** Как у настоящего настольного клиента: 30 дней от последнего обновления (решение 2026-10-08). */
    static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(30);

    /**
     * Секреты dev-клиентов. Лежат в коде открыто намеренно: это клиенты <b>только для разработки</b>
     * (регистрируются при {@code auth.dev-client.enabled=true}, на сервере выключены), а настоящие
     * клиенты свои секреты получат через админку. Прятать эти негде и незачем.
     */
    static final String LAUNCHER_CLIENT_SECRET = "launcher-dev-secret";
    static final String PLANNER_CLIENT_SECRET = "planner-dev-secret";

    private final RegisteredClientRepository clients;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        registerIfAbsent(launcherClient());
        registerIfAbsent(plannerClient());
    }

    /** Повторный запуск не должен плодить клиентов и не должен затирать уже выданные авторизации. */
    private void registerIfAbsent(RegisteredClient client) {
        if (clients.findByClientId(client.getClientId()) == null) {
            clients.save(client);
        }
    }

    private RegisteredClient launcherClient() {
        return common(LAUNCHER_CLIENT_ID, "Лаунчер Minecraft (разработка)",
                LAUNCHER_REDIRECT_URI, LAUNCHER_CLIENT_SECRET)
                // Обновление access-токена без участия человека — то, без чего лаунчер нежизнеспособен.
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(ACCESS_TOKEN_LIFETIME)
                        // Ротация: каждый обмен выдаёт новый refresh-токен, старый перестаёт работать.
                        // У Spring по умолчанию наоборот (reuseRefreshTokens = true), а срок — 60 минут.
                        .reuseRefreshTokens(false)
                        .refreshTokenTimeToLive(REFRESH_TOKEN_LIFETIME)
                        .build())
                .build();
    }

    private RegisteredClient plannerClient() {
        return common(PLANNER_CLIENT_ID, "Планировщик (разработка)",
                PLANNER_REDIRECT_URI, PLANNER_CLIENT_SECRET)
                // Без grant refresh_token: серверное приложение держит сессию с человеком само.
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(ACCESS_TOKEN_LIFETIME)
                        .build())
                .build();
    }

    /**
     * Общее у обоих: вход человека по коду с обязательным PKCE и секрет клиента.
     *
     * <p>Секрет есть и у лаунчера, хотя спрятать его в настольной программе невозможно — без него
     * Spring не даёт обменять refresh-токен. Почему это допустимо, разобрано в
     * {@code ClientKind.NATIVE}; защищает здесь PKCE, а не секрет.
     */
    private RegisteredClient.Builder common(String clientId, String name, String redirectUri, String secret) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientName(name)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientSecret(passwordEncoder.encode(secret))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirectUri)
                .scope(OidcScopes.OPENID)
                // profile -> claim name, email -> claims email и email_verified (OpenID Connect Core, 5.4)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        // Свои сервисы: не спрашиваем «разрешить доступ?».
                        .requireAuthorizationConsent(false)
                        .build());
    }
}
