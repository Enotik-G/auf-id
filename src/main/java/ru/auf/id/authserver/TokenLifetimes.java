package ru.auf.id.authserver;

import java.time.Duration;

/**
 * Сроки жизни токенов — в одном месте, потому что от них зависят сразу несколько частей: настройки
 * клиентов из админки ({@code AdminClientService}), dev-клиенты ({@link DevClientRegistration}) и
 * память о погашенных refresh-токенах ({@link RefreshTokenReuseDetector}). Разъедись эти числа —
 * и, например, кража refresh-токена в последние дни его жизни перестала бы замечаться, молча.
 *
 * <p>Менять — только вместе с {@code docs/service-integration.md}: сроки входят в контракт
 * с сервисами экосистемы.
 */
public final class TokenLifetimes {

    /** Access token живёт 10 минут: сервисы проверяют его сами, без запроса в Auth (решение архитектуры). */
    public static final Duration ACCESS_TOKEN = Duration.ofMinutes(10);

    /**
     * Refresh-токен живёт 30 дней — и срок считается заново от каждого обновления (решение 2026-10-08).
     *
     * <p>Скользящее окно получается само: при ротации выдаётся новый токен, а ему Spring берёт срок
     * из этой же настройки. Пользуешься — срок продлевается, забросил на месяц — вход заново.
     */
    public static final Duration REFRESH_TOKEN = Duration.ofDays(30);

    private TokenLifetimes() {
    }
}
