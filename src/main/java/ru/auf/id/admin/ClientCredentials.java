package ru.auf.id.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Зарегистрированный клиент и его секрет.
 *
 * <p>Секрет виден только здесь: в базе лежит его хеш, и достать оттуда исходное значение невозможно.
 * Потерялся — выдать новый через ротацию, прежний при этом перестанет работать.
 *
 * @param secret {@code null} только у вида {@code BROWSER}: вкладке секрет спрятать негде.
 *               Настольное приложение ({@code NATIVE}) секрет получает — см. {@link ClientKind#NATIVE}
 */
@Schema(description = "Клиент и его секрет. Секрет показывается один раз — сохраните его сразу")
public record ClientCredentials(

        @Schema(description = "Идентификатор клиента", example = "planner")
        String clientId,

        @Schema(description = "Секрет клиента; null у вида BROWSER — вкладке браузера его негде спрятать",
                nullable = true)
        String secret
) {
}
