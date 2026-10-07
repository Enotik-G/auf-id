package com.example.planner.provisioning;

import java.util.UUID;

/**
 * Созданная учётка и одноразовый токен к ней.
 *
 * <p>Ссылку из токена собирает тот, кто знает публичный адрес сервиса, — не сервис выдачи учёток.
 * Сам токен виден только здесь и только один раз: в базе лежит лишь его SHA-256.
 */
public record Invitation(UUID userId, String activationToken) {
}
