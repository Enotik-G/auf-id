package ru.auf.id.admin;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Instant;
import java.util.Set;

/** Зарегистрированный клиент глазами администратора. Секрета здесь нет и быть не может. */
public record ClientSummary(
        String clientId,
        String name,
        ClientKind kind,
        Set<String> redirectUris,
        Set<String> scopes,
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
     */
    private static ClientKind kindOf(RegisteredClient client) {
        if (client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
            return ClientKind.PUBLIC;
        }
        if (client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE)) {
            return ClientKind.CONFIDENTIAL;
        }
        return ClientKind.SERVICE;
    }
}
