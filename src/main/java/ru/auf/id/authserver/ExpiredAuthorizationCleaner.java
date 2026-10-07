package ru.auf.id.authserver;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Удаляет авторизации, от которых больше нет никакого толку: все выданные в них коды и токены
 * уже истекли.
 *
 * <p>Сам Spring Authorization Server строки {@code oauth2_authorization} не удаляет никогда:
 * один вход в сервис = одна новая строка навсегда. А в строке — id пользователя, его IP
 * и id_token с почтой и ФИО. Хранить это дольше, чем оно нужно, незачем.
 *
 * <p>Строка истекла, когда истекло <b>всё</b>, что в ней есть: код, access token, id_token,
 * refresh token, коды устройства. Пока хоть что-то живо (например, refresh token ещё можно
 * обменять) — строку трогать нельзя.
 *
 * <p>Строки, где не выдано ещё ничего (у всех {@code *_expires_at} пусто), не удаляются: это
 * вход, застрявший на полпути (например, на странице согласия). Понять, давно ли он начался,
 * не по чему — в таблице нет времени создания строки.
 *
 * <p>Расписания здесь нет: класс только удаляет, когда его попросят. Кто и как часто просит —
 * отдельный вопрос.
 */
@Component
public class ExpiredAuthorizationCleaner {

    /**
     * {@code GREATEST} в Postgres пропускает {@code NULL}: из («истёк вчера», «не выдавался»,
     * «истекает завтра») он вернёт «завтра». То есть это самый поздний срок среди всего, что
     * в строке реально выдано. Меньше {@code now} — значит, истекло всё.
     *
     * <p>Если не выдано ничего, {@code GREATEST} вернёт {@code NULL}, а сравнение с {@code NULL}
     * не бывает истинным — такие строки не удаляются (см. описание класса).
     */
    private static final String DELETE_EXPIRED = """
            DELETE FROM oauth2_authorization
            WHERE GREATEST(
                      authorization_code_expires_at,
                      access_token_expires_at,
                      oidc_id_token_expires_at,
                      refresh_token_expires_at,
                      user_code_expires_at,
                      device_code_expires_at
                  ) < ?
            """;

    private final JdbcOperations jdbc;

    public ExpiredAuthorizationCleaner(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Удаляет все авторизации, в которых всё выданное истекло к моменту {@code now}.
     *
     * <p>Момент передаётся снаружи, а не берётся внутри: так в тестах можно взять любое время,
     * а вызывающий код возьмёт его из бина {@code Clock}.
     *
     * @return сколько авторизаций удалено
     */
    public int deleteExpired(Instant now) {
        return jdbc.update(DELETE_EXPIRED, now.atOffset(ZoneOffset.UTC));
    }
}
