package ru.auf.id.admin;

/**
 * Зарегистрированный клиент и его секрет.
 *
 * <p>Секрет виден только здесь: в базе лежит его хеш, и достать оттуда исходное значение невозможно.
 * Потерялся — выдать новый через ротацию, прежний при этом перестанет работать.
 *
 * @param secret {@code null} только у вида {@code BROWSER}: вкладке секрет спрятать негде.
 *               Настольное приложение ({@code NATIVE}) секрет получает — см. {@link ClientKind#NATIVE}
 */
public record ClientCredentials(String clientId, String secret) {
}
