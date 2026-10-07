package ru.auf.id.user;

import java.util.UUID;

/**
 * Пользователя с таким id нет.
 *
 * <p>В отличие от поиска по почте на входе, здесь скрывать нечего: id запрашивает администратор,
 * у которого и так есть список пользователей, а молчание в ответ на опечатку в id сбивало бы с толку.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("Пользователь " + userId + " не найден");
    }
}
