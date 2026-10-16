package ru.auf.id.onetimetoken;

import java.time.Duration;

/**
 * Для чего выдан одноразовый токен и сколько он живёт.
 * Значения совпадают с {@code one_time_tokens_purpose_check} в БД.
 * Токен одного назначения нельзя использовать для другого.
 *
 * <p>Обе ссылки выдаёт администратор и передаёт человеку лично — писем сервис не отправляет.
 */
public enum TokenPurpose {
    /** Приглашение от администратора. */
    INVITE(Duration.ofDays(7)),

    /**
     * Сброс забытого пароля (миграция 011). Срок короче, чем у приглашения (решение 2026-10-16):
     * ссылка открывает уже работающую учётку, и утёкшая — сфотографированный экран, пересланная в
     * чат — должна быть опасна не дольше суток.
     */
    PASSWORD_RESET(Duration.ofHours(24));

    private final Duration lifetime;

    TokenPurpose(Duration lifetime) {
        this.lifetime = lifetime;
    }

    public Duration lifetime() {
        return lifetime;
    }
}
