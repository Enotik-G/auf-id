package ru.auf.id.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

/**
 * Чего администратор хочет от нового клиента. Остальное — способ аутентификации, разрешённые потоки,
 * обязательность PKCE, время жизни токена — выводится из {@link ClientKind}, а не задаётся руками.
 *
 * @param clientId               короткое имя сервиса, по нему он представляется: {@code planner}, {@code meet}
 * @param name                   название для человека
 * @param redirectUris           адреса возврата; для {@link ClientKind#SERVICE} должны быть пустыми.
 *                               Настольному приложению ({@link ClientKind#NATIVE}) годится
 *                               {@code http://127.0.0.1:<порт>/...}: Spring разрешает любой порт для
 *                               loopback-адресов (RFC 8252 §7.3), поэтому лаунчер может занять
 *                               свободный, а здесь достаточно указать один
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
                NATIVE — настольное или мобильное приложение (лаунчер), с PKCE. Единственный вид,
                которому выдаётся refresh-токен: 30 дней, с ротацией. Секрет выдаётся, но настоящей
                защитой здесь служит PKCE — спрятать секрет в программе на чужом компьютере нельзя.
                BROWSER — приложение в браузерной вкладке (SPA), без секрета и без refresh-токена.
                CONFIDENTIAL — серверное приложение, с секретом.
                SERVICE — сервис к сервису, поток client_credentials, без адресов возврата.""")
        ClientKind kind,

        @Schema(description = "Адреса возврата: абсолютные, без фрагмента. Для SERVICE должны быть пустыми.",
                example = "[\"https://planner.sinhub.ru/callback\"]")
        Set<String> redirectUris,

        @Schema(description = "Куда вернуть после выхода", example = "[\"https://planner.sinhub.ru/\"]")
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
