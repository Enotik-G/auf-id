package ru.auf.id.admin;

import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "Настройки нового клиента")
public record ClientSpec(

        @Schema(description = "Короткое имя сервиса: строчные латинские буквы, цифры, дефисы", example = "planner")
        String clientId,

        @Schema(description = "Название для человека", example = "Планировщик")
        String name,

        @Schema(description = """
                PUBLIC — браузерное или мобильное приложение, без секрета, с PKCE.
                CONFIDENTIAL — серверное приложение, с секретом.
                SERVICE — сервис к сервису, поток client_credentials, без адресов возврата.""")
        ClientKind kind,

        @Schema(description = "Адреса возврата: абсолютные, без фрагмента. Для SERVICE должны быть пустыми.",
                example = "[\"https://planner.college.ru/callback\"]")
        Set<String> redirectUris,

        @Schema(description = "Куда вернуть после выхода", example = "[\"https://planner.college.ru/\"]")
        Set<String> postLogoutRedirectUris,

        @Schema(description = "Права. Пусто — openid, profile, email; для SERVICE обязательны.",
                example = "[\"openid\",\"profile\",\"email\"]")
        Set<String> scopes
) {
    public ClientSpec {
        redirectUris = redirectUris == null ? Set.of() : Set.copyOf(redirectUris);
        postLogoutRedirectUris = postLogoutRedirectUris == null ? Set.of() : Set.copyOf(postLogoutRedirectUris);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }
}
