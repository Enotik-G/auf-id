package ru.auf.id.provisioning;

import ru.auf.id.user.EmailAddress;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Учётка с такой почтой уже есть.
 *
 * <p>В саморегистрации занятая почта обрабатывалась молча — иначе по форме можно было бы перебором
 * узнать, чей адрес у нас зарегистрирован. Здесь всё наоборот: администратор уже имеет доступ к
 * списку пользователей, скрывать от него нечего, а молчание означало бы «учётка создана», хотя её
 * не создали.
 */
public class EmailAlreadyTakenException extends RuntimeException {

    /** Имя ограничения уникальности почты в БД (миграция 001). */
    private static final String EMAIL_UNIQUE_CONSTRAINT = "users_email_key";

    public EmailAlreadyTakenException(EmailAddress email) {
        super("Учётная запись с почтой " + email + " уже существует");
    }

    /**
     * Это ли нарушение уникальности почты — а не какое-то другое ограничение БД.
     *
     * <p>Нужно на случай гонки: проверка «почта свободна» и запись — два разных шага, и между ними
     * адрес может занять параллельный запрос. Тогда отказывает сама база, и вместо 500 человек должен
     * получить обычное «почта занята». Любое другое нарушение — неожиданная ошибка, её не прячем.
     */
    public static boolean isCausedBy(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException violation
                && EMAIL_UNIQUE_CONSTRAINT.equals(violation.getConstraintName());
    }
}
