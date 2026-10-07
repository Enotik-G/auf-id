package com.example.planner.admin;

import java.util.Set;

/**
 * Чего администратор хочет от нового клиента. Остальное — способ аутентификации, разрешённые потоки,
 * обязательность PKCE, время жизни токена — выводится из {@link ClientKind}, а не задаётся руками.
 *
 * @param clientId               короткое имя сервиса, по нему он представляется: {@code planner}, {@code meet}
 * @param name                   название для человека
 * @param redirectUris           адреса возврата; для {@link ClientKind#SERVICE} должны быть пустыми
 * @param postLogoutRedirectUris куда вернуть после выхода; необязательны
 * @param scopes                 запрашиваемые права; пустой набор означает openid, profile, email
 */
public record ClientSpec(
        String clientId,
        String name,
        ClientKind kind,
        Set<String> redirectUris,
        Set<String> postLogoutRedirectUris,
        Set<String> scopes
) {
    public ClientSpec {
        redirectUris = redirectUris == null ? Set.of() : Set.copyOf(redirectUris);
        postLogoutRedirectUris = postLogoutRedirectUris == null ? Set.of() : Set.copyOf(postLogoutRedirectUris);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
