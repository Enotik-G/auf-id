package ru.auf.id.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

/** Решает, пропустить ли запрос с этого IP. Вёдра Bucket4j хранятся в Redis — общие для всех копий приложения. */
@RequiredArgsConstructor
public class RateLimiter {

    private final ProxyManager<String> buckets;

    /** Забирает один жетон из ведра этого IP. */
    public Decision tryAcquire(RateLimit limit, String clientIp) {
        ConsumptionProbe probe = buckets.builder()
                .build(limit.keyFor(clientIp), limit::bucketConfiguration)
                .tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            return Decision.allowed();
        }
        return Decision.rejected(Duration.ofNanos(probe.getNanosToWaitForRefill()));
    }

    /** Итог проверки; {@code retryAfter} — через сколько ведро снова наполнится (для отклонённых). */
    public record Decision(boolean isAllowed, Duration retryAfter) {

        static Decision allowed() {
            return new Decision(true, Duration.ZERO);
        }

        static Decision rejected(Duration retryAfter) {
            return new Decision(false, retryAfter);
        }
    }
}
