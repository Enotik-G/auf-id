package ru.auf.id.user;

/**
 * Действие невозможно в текущем статусе учётки: активировать уже активную, разблокировать
 * незаблокированную и т.п. Админка отвечает на него 409.
 *
 * <p>Свой класс, а не просто {@link IllegalStateException}: тот бросают и внутренние сбои
 * («SHA-256 недоступен в этой JVM»), и их нельзя выдавать за «конфликт состояния» — это ошибка
 * сервера (500), её надо чинить, а не показывать администратору как подсказку.
 */
public class WrongUserStatusException extends IllegalStateException {

    public WrongUserStatusException(String message) {
        super(message);
    }
}
