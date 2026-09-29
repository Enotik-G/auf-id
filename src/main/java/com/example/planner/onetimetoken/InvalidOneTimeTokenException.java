package com.example.planner.onetimetoken;

/**
 * Токен не найден, уже использован или истёк. Причину наружу намеренно не различаем:
 * пользователю — «ссылка недействительна», злоумышленнику — никаких подсказок.
 */
public class InvalidOneTimeTokenException extends RuntimeException {

    public InvalidOneTimeTokenException() {
        super("Ссылка недействительна или устарела");
    }
}
