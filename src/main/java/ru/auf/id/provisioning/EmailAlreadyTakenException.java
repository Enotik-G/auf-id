package ru.auf.id.provisioning;

import ru.auf.id.user.EmailAddress;

/**
 * Учётка с такой почтой уже есть.
 *
 * <p>В саморегистрации занятая почта обрабатывалась молча — иначе по форме можно было бы перебором
 * узнать, чей адрес у нас зарегистрирован. Здесь всё наоборот: администратор уже имеет доступ к
 * списку пользователей, скрывать от него нечего, а молчание означало бы «учётка создана», хотя её
 * не создали.
 */
public class EmailAlreadyTakenException extends RuntimeException {

    public EmailAlreadyTakenException(EmailAddress email) {
        super("Учётная запись с почтой " + email + " уже существует");
    }
}
