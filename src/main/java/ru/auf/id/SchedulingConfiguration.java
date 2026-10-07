package ru.auf.id;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Включает задачи по расписанию: без {@code @EnableScheduling} аннотации {@code @Scheduled}
 * на методах молча игнорируются.
 *
 * <p>Каждая копия приложения запускает расписание у себя. Задача, которая должна выполниться
 * один раз на всех, сама договаривается с остальными через Redis
 * (см. {@code ExpiredAuthorizationCleanupJob}).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfiguration {
}
