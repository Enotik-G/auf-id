package ru.auf.id.authserver;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Клиент {@value #CLIENT_ID} для разработки: при запуске регистрируется в базе, если его там ещё нет.
 *
 * <p>Включается только явно: {@code auth.dev-client.enabled=true} (локально — в .env, в тестах — в тестовых
 * настройках). На сервере по умолчанию выключен: клиент с адресом возврата на 127.0.0.1 там не нужен.
 * Настоящие клиенты (планировщик на сервере) будут регистрироваться через админку (задача 9).
 */
@Component
@ConditionalOnProperty(name = "auth.dev-client.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DevClientRegistration implements ApplicationRunner {

    static final String CLIENT_ID = "planner-dev";
    static final String REDIRECT_URI = "http://127.0.0.1:8090/login/oauth2/code/auth";
    /** Access token живёт 10 минут: сервисы проверяют его сами, без запроса в Auth (решение архитектуры). */
    static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(10);
    /** Как у настоящего настольного клиента: 30 дней от последнего обновления (решение 2026-10-08). */
    static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(30);

    private final RegisteredClientRepository clients;

    @Override
    public void run(ApplicationArguments args) {
        if (clients.findByClientId(CLIENT_ID) != null) {
            return;
        }
        clients.save(RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(CLIENT_ID)
                .clientName("Планировщик (разработка)")
                // Публичный клиент (без секрета) — вид NATIVE, как лаунчер; защищён PKCE.
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                // Обновление access-токена без участия человека — то, без чего лаунчер нежизнеспособен.
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(REDIRECT_URI)
                .scope(OidcScopes.OPENID)
                // profile -> claim name, email -> claims email и email_verified (OpenID Connect Core, 5.4)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        // Свой сервис: не спрашиваем «разрешить доступ?».
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(ACCESS_TOKEN_LIFETIME)
                        // Ротация: каждый обмен выдаёт новый refresh-токен, старый перестаёт работать.
                        // У Spring по умолчанию наоборот (reuseRefreshTokens = true), а срок — 60 минут.
                        .reuseRefreshTokens(false)
                        .refreshTokenTimeToLive(REFRESH_TOKEN_LIFETIME)
                        .build())
                .build());
    }
}
