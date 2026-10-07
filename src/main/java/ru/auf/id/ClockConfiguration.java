package ru.auf.id;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Единые «часы» приложения. Код берёт время через {@code Instant.now(clock)}, а не {@code Instant.now()},
 * чтобы в тестах можно было подставить часы, показывающие нужный момент.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
