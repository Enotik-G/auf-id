package ru.auf.id.onetimetoken;

import java.time.Duration;

/**
 * Для чего выдан одноразовый токен и сколько он живёт.
 * Значения совпадают с {@code one_time_tokens_purpose_check} в БД.
 * Токен одного назначения нельзя использовать для другого.
 *
 * <p>Сейчас назначение одно — ссылка активации. Сброс пароля по почте ({@code PASSWORD_RESET})
 * удалён вместе с письмами: новую ссылку выдаёт администратор (миграция 009).
 */
public enum TokenPurpose {
    /** Приглашение от администратора. */
    INVITE(Duration.ofDays(7));

    private final Duration lifetime;

    TokenPurpose(Duration lifetime) {
        this.lifetime = lifetime;
    }

    public Duration lifetime() {
        return lifetime;
    }
}
