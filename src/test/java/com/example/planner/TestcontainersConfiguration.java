package com.example.planner;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Настоящие PostgreSQL и Redis в Docker для тестов. Подключается к тесту через
 * {@code @Import(TestcontainersConfiguration.class)}.
 * Версии образов — те же, что в compose.yaml.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18-alpine");
    }

    /** Для Redis у Testcontainers нет отдельного класса — берём общий, name подсказывает Spring, что это Redis. */
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:8.8-alpine").withExposedPorts(6379);
    }
}
