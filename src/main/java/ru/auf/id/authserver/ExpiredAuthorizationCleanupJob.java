package ru.auf.id.authserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Раз в {@code auth.authorization-cleanup.interval} удаляет истёкшие авторизации
 * ({@link ExpiredAuthorizationCleaner}).
 *
 * <p>Копий приложения может быть несколько, и расписание сработает в каждой. Чтобы очистка
 * шла одна, копия сначала занимает ключ в Redis командой {@code SET NX EX}: «записать, только
 * если ключа ещё нет, и стереть через столько-то секунд». Redis выполняет её атомарно, поэтому
 * из нескольких одновременных попыток успешна ровно одна; остальные пропускают свой запуск.
 *
 * <p>Ключ живёт столько же, сколько интервал, и после очистки <b>не</b> удаляется. Так очистка
 * идёт не чаще раза за интервал на все копии вместе, даже если их расписания сдвинуты друг
 * относительно друга. Если копия упала посреди очистки, ключ всё равно исчезнет сам — следующий
 * запуск не заблокирован навсегда.
 */
@Component
public class ExpiredAuthorizationCleanupJob {

    static final String LOCK_KEY = "authorization-cleanup:lock";

    private final ExpiredAuthorizationCleaner cleaner;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Duration interval;

    public ExpiredAuthorizationCleanupJob(ExpiredAuthorizationCleaner cleaner,
                                          StringRedisTemplate redis,
                                          Clock clock,
                                          @Value("${auth.authorization-cleanup.interval}") Duration interval) {
        this.cleaner = cleaner;
        this.redis = redis;
        this.clock = clock;
        this.interval = interval;
    }

    /**
     * {@code fixedDelay} — следующий запуск через интервал после <b>окончания</b> предыдущего,
     * поэтому два запуска в одной копии не наложатся. {@code initialDelay} — первый запуск
     * не сразу при старте, а через интервал: старт приложения (и тестов) не ждёт очистки.
     *
     * @return сколько авторизаций удалено; {@code -1}, если очистку в этот раз делает другая копия
     */
    @Scheduled(fixedDelayString = "${auth.authorization-cleanup.interval}",
            initialDelayString = "${auth.authorization-cleanup.interval}")
    public int run() {
        Instant now = Instant.now(clock);
        Boolean locked = redis.opsForValue().setIfAbsent(LOCK_KEY, now.toString(), interval);
        if (!Boolean.TRUE.equals(locked)) {
            return -1;
        }
        return cleaner.deleteExpired(now);
    }
}
