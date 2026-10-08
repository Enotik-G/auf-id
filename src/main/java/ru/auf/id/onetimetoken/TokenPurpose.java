package ru.auf.id.onetimetoken;

import java.time.Duration;

/**
 * Для чего выдан одноразовый токен и сколько он живёт.
 * Значения совпадают с {@code one_time_tokens_purpose_check} в БД.
 * Токен одного назначения нельзя использовать для другого.
 */
public enum TokenPurpose {
    /** Сброс забытого пароля — короткий срок, это доступ к аккаунту. */
    PASSWORD_RESET(Duration.ofMinutes(30)),
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
