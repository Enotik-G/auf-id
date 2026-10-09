package ru.auf.id.authserver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Распознаёт кражу refresh-токена и обрывает цепочку.
 *
 * <p><b>Что не так без него.</b> Ротация включена: каждый обмен выдаёт новый refresh-токен, а
 * прежнее значение в строке затирается. Поэтому повторно предъявленный токен <i>отвергается</i> —
 * но не <i>распознаётся</i>: для сервера он просто «не найден», как любая опечатка. Разница
 * существенная. Украли токен, вор обменял его первым — настоящее приложение получит отказ и молча
 * выкинет человека, а токен вора продолжит работать как ни в чём не бывало. Снаружи это выглядит
 * как «меня почему-то разлогинило», и никто не узнает, что доступ ушёл.
 *
 * <p><b>Как работает.</b> Затираемое значение не исчезает бесследно: его хеш попадает сюда, в
 * реестр погашенных. Предъявили токен, которого в базе нет, — смотрим в реестр. Нашли — это
 * предъявление уже погашенного токена, то есть у двоих на руках одна цепочка. Кто из них вор,
 * сервер знать не может, поэтому доступ теряют оба: авторизации пользователя у этого клиента
 * удаляются, и человек входит заново. Так велит OAuth 2.0 Security Best Current Practice (§4.14.2).
 *
 * <p><b>Почему только у этого клиента</b> (решение пользователя от 2026-10-08): утёк токен одного
 * приложения, а не пароль. Украли токен лаунчера — вылетает лаунчер, в планировщике человек
 * остаётся.
 *
 * <p>В Redis, а не в памяти: реестр общий для всех копий приложения, и записи истекают сами.
 * Хранится <b>хеш</b> токена, не сам токен — как и в {@code login/LoginAttemptService}: меньше
 * секретов в Redis, меньше проблем, если кто-то туда заглянет. Хеш уже посчитан
 * {@link HashedTokenAuthorizationService}, второй раз считать нечего.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenReuseDetector {

    /**
     * Сколько помним погашенный токен — ровно столько, сколько живёт сам refresh-токен.
     *
     * <p>Дольше незачем: токен старше этого срока истёк бы и так, и предъявить его нельзя.
     * Короче опасно: забыв о токене раньше, чем он истёк, мы перестали бы узнавать кражу. Поэтому
     * это не своё число, а ссылка на общий срок.
     */
    static final Duration REMEMBER_SPENT_TOKEN_FOR = TokenLifetimes.REFRESH_TOKEN;

    /**
     * Разделитель владельца и клиента в значении. Пробел безопасен: оба — UUID строкой, пробелов
     * в них быть не может.
     */
    private static final String SEPARATOR = " ";

    private final StringRedisTemplate redis;
    private final UserAuthorizationRevoker revoker;

    /**
     * Запомнить токен, который только что перестал действовать из-за ротации.
     *
     * @param tokenHash           значение в том виде, в каком оно лежало в БД, то есть {@code sha256:…}
     * @param principalName       владелец авторизации
     * @param registeredClientId  внутренний id клиента ({@code oauth2_registered_client.id})
     */
    void remember(String tokenHash, String principalName, String registeredClientId) {
        redis.opsForValue().set(key(tokenHash), principalName + SEPARATOR + registeredClientId, REMEMBER_SPENT_TOKEN_FOR);
    }

    /**
     * Предъявлен токен, которого в базе нет. Если он там был и был погашен — это кража: обрываем
     * цепочку.
     *
     * @param tokenHash хеш предъявленного значения
     */
    void revokeIfReused(String tokenHash) {
        String owner = redis.opsForValue().get(key(tokenHash));
        if (owner == null) {
            // Токена не было никогда: опечатка, мусор или подбор. Отзывать нечего.
            return;
        }

        String[] parts = owner.split(SEPARATOR, 2);
        if (parts.length != 2) {
            // Запись испорчена — отзывать наугад хуже, чем не отозвать.
            log.warn("Испорченная запись в реестре погашенных refresh-токенов, отзыв пропущен");
            return;
        }

        int revoked = revoker.revokeAllForClient(parts[0], parts[1]);
        // Единственный след этого события: иначе человека выкинет, а причину никто не узнает.
        // Полноценный журнал действий — задача 11.
        log.warn("Повторно предъявлен погашенный refresh-токен: доступ пользователя {} у клиента {} отозван"
                + " ({} авторизаций). Признак кражи токена.", parts[0], parts[1], revoked);
    }

    private static String key(String tokenHash) {
        return "refresh:retired:" + tokenHash;
    }
}
