package ru.auf.id.account;

import java.time.Duration;

/** Слишком много неверных текущих паролей подряд — смена пароля временно закрыта. */
public class TooManyPasswordAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyPasswordAttemptsException(Duration retryAfter) {
        super("Слишком много неверных попыток. Попробуйте позже");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
