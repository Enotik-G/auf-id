package ru.auf.id.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.util.Set;

/** Зарегистрированный клиент глазами администратора. Секрета здесь нет и быть не может. */
@Schema(description = "Зарегистрированный сервис. Секрета здесь нет: в базе лежит только его хеш")
public record ClientSummary(

        @Schema(description = "Идентификатор клиента — его передаёт сервис при входе", example = "planner")
        String clientId,

        @Schema(description = "Название для человека", example = "Планировщик")
        String name,

        @Schema(description = "Вид клиента — следует из его настроек, отдельно не хранится", example = "BROWSER")
        ClientKind kind,

        @Schema(description = "Куда разрешено возвращать после входа", example = "[\"https://planner.sinhub.ru/callback\"]")
        Set<String> redirectUris,

        @Schema(description = "Какие права (scope) клиент может запросить", example = "[\"openid\", \"profile\", \"email\"]")
        Set<String> scopes,

        @Schema(description = "Когда клиента зарегистрировали")
        Instant registeredAt
) {
    public static ClientSummary of(RegisteredClient client) {
        return new ClientSummary(
                client.getClientId(),
                client.getClientName(),
                kindOf(client),
                client.getRedirectUris(),
                client.getScopes(),
                client.getClientIdIssuedAt());
    }

    /**
     * Вид не хранится отдельным полем — он однозначно следует из настроек, а дублировать его значило бы
     * допустить расхождение между «видом» и тем, как клиент на самом деле работает.
     *
     * <p>{@link ClientKind#NATIVE} отличается от {@link ClientKind#CONFIDENTIAL} наличием
     * refresh-токена: секрет есть у обоих (см. описание {@code NATIVE} — почему), а обновлять токен
     * без участия человека разрешено только настольному приложению. {@link ClientKind#BROWSER} —
     * единственный, кто совсем без секрета.
     */
    private static ClientKind kindOf(RegisteredClient client) {
        if (client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
            return ClientKind.BROWSER;
        }
        if (client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            return ClientKind.NATIVE;
        }
        if (client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE)) {
            return ClientKind.CONFIDENTIAL;
        }
        return ClientKind.SERVICE;
    }
}
