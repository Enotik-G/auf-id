package ru.auf.id.account;

/** При смене пароля неверно введён текущий. */
public class WrongCurrentPasswordException extends RuntimeException {

    public WrongCurrentPasswordException() {
        super("Текущий пароль введён неверно");
    }
}
