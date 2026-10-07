package ru.auf.id.authserver;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Отзывает всё, что сервер авторизации успел выдать пользователю: коды, access- и id-токены
 * (а когда появятся — и refresh-токены).
 *
 * <p>Нужен при блокировке: без этого строки {@code oauth2_authorization} заблокированного живут
 * дальше, и выданное ему остаётся в силе. Access token, уже ушедший в сервис экосистемы, это
 * не отменит — сервисы проверяют подпись сами, без запроса к нам, — он доживёт свои 10 минут
 * (принятый в архитектуре компромисс). Зато всё, что ещё должно пройти через нас (обмен кода,
 * {@code /userinfo}, обновление токена), перестанет работать сразу.
 *
 * <p>Согласия ({@code oauth2_authorization_consent}) не трогаем: это не выданный доступ, а
 * запомненное «я разрешаю этому сервису», после разблокировки оно снова пригодится.
 *
 * <p>Готовый {@code OAuth2AuthorizationService} так не умеет — он ищет одну авторизацию по id
 * или по значению токена, а не все по владельцу. Поэтому — прямой запрос к его же таблице.
 */
@Component
public class UserAuthorizationRevoker {

    private final JdbcOperations jdbc;

    public UserAuthorizationRevoker(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Удаляет все авторизации пользователя.
     *
     * <p>Владелец строки — {@code principal_name}, и это id пользователя строкой: при входе
     * имя вошедшего — его id, а не почта (см. {@code AccountUserDetailsService}).
     *
     * @return сколько авторизаций удалено
     */
    public int revokeAll(UUID userId) {
        return jdbc.update("DELETE FROM oauth2_authorization WHERE principal_name = ?", userId.toString());
    }
}
