package com.example.planner.user;

/** Строка не похожа на email. Отдельный тип, чтобы API отвечал на это 400, не путая с другими ошибками. */
public class InvalidEmailException extends IllegalArgumentException {

    public InvalidEmailException(String message) {
        super(message);
    }
}
