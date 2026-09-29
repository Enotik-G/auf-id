package com.example.planner.onetimetoken;

/**
 * Для чего выдан одноразовый токен. Значения совпадают с {@code one_time_tokens_purpose_check} в БД.
 * Токен одного назначения нельзя использовать для другого.
 */
public enum TokenPurpose {
    /** Подтверждение почты после саморегистрации. */
    EMAIL_VERIFY,
    /** Сброс забытого пароля. */
    PASSWORD_RESET,
    /** Приглашение от администратора. */
    INVITE
}
