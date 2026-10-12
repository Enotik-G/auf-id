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
 *   <li>{@value #PLANNER_CLIENT_ID} — вид {@code BROWSER}: приложение живёт в браузерной вкладке
 *       (решение 2026-10-08). Без секрета — спрятать его в JavaScript негде; без refresh-токена —
 *       его украл бы XSS. Вход продлевается молчаливым заходом на {@code /oauth2/authorize} с
 *       {@code prompt=none}: сессию вкладки узнаёт сам провайдер и сразу возвращает код.</li>
 * </ul>
 *
 * <p><b>Python-микросервис планировщика клиентом не является.</b> Клиент — тот, кто ведёт человека
 * на страницу входа, то есть браузерное приложение. Микросервис за ним только проверяет готовый
 * токен, а значит он <i>сервер ресурсов</i>: ему достаточно прочитать {@code jwks_uri} из discovery
 * и сверять подпись, {@code iss}, {@code aud} и {@code exp}. Регистрировать его в AUF ID не нужно
 * вовсе, и секрета у него нет.
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

    /**
     * Планировщик: браузерное приложение.
     *
     * <p>Порт в адресе возврата не важен — Spring разрешает любой для loopback-адресов
     * (RFC 8252 §7.3), поэтому dev-сервер фронтенда может слушать 5173, 3000 или что угодно.
     * А вот <b>путь сверяется точно</b>: {@value #PLANNER_REDIRECT_URI} должен совпадать.
     */
    static final String PLANNER_CLIENT_ID = "planner-dev";
    static final String PLANNER_REDIRECT_URI = "http://127.0.0.1:5173/auth/callback";

    /**
     * Секреты dev-клиентов. Лежат в коде открыто намеренно: это клиенты <b>только для разработки</b>
     * (регистрируются при {@code auth.dev-client.enabled=true}, на сервере выключены), а настоящие
     * клиенты свои секреты получат через админку. Прятать эти негде и незачем.
     */
    static final String LAUNCHER_CLIENT_SECRET = "launcher-dev-secret";

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
        return userFacing(LAUNCHER_CLIENT_ID, "Лаунчер Minecraft (разработка)", LAUNCHER_REDIRECT_URI)
                // Секрет есть, хотя спрятать его в настольной программе невозможно: без него Spring
                // не даёт обменять refresh-токен. Почему это допустимо — в ClientKind.NATIVE;
                // защищает здесь PKCE, а не секрет.
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientSecret(passwordEncoder.encode(LAUNCHER_CLIENT_SECRET))
                // Обновление access-токена без участия человека — то, без чего лаунчер нежизнеспособен.
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(TokenLifetimes.ACCESS_TOKEN)
                        // Ротация: каждый обмен выдаёт новый refresh-токен, старый перестаёт работать.
                        // У Spring по умолчанию наоборот (reuseRefreshTokens = true), а срок — 60 минут.
                        .reuseRefreshTokens(false)
                        .refreshTokenTimeToLive(TokenLifetimes.REFRESH_TOKEN)
                        .build())
                .build();
    }

    private RegisteredClient plannerClient() {
        return userFacing(PLANNER_CLIENT_ID, "Планировщик (разработка)", PLANNER_REDIRECT_URI)
                // Публичный клиент: секрет в JavaScript спрятать негде, защищает PKCE.
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                // Без grant refresh_token: в браузере его украл бы XSS. Вход продлевается
                // заходом на /oauth2/authorize с prompt=none.
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(TokenLifetimes.ACCESS_TOKEN)
                        .build())
                .build();
    }

    /** Общее у обоих: вход человека по коду с обязательным PKCE. Способ аутентификации — свой у каждого. */
    private RegisteredClient.Builder userFacing(String clientId, String name, String redirectUri) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientName(name)
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
