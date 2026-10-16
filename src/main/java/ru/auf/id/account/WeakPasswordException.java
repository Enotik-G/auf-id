package ru.auf.id.account;

import ru.auf.id.provisioning.PasswordPolicy;

/**
 * Новый пароль не прошёл {@link PasswordPolicy}. Причина — в {@link #verdict()}: по ней вызывающий
 * решает, что сказать человеку.
 */
public class WeakPasswordException extends RuntimeException {

    private final PasswordPolicy.Verdict verdict;

    public WeakPasswordException(PasswordPolicy.Verdict verdict) {
        super("Пароль не подходит: " + verdict);
        this.verdict = verdict;
    }

    public PasswordPolicy.Verdict verdict() {
        return verdict;
    }
}
