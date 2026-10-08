package ru.auf.id.authserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Где сервер авторизации хранит клиентов и выданные авторизации — в PostgreSQL (миграция 005),
 * готовыми JDBC-репозиториями Spring. Без этих бинов всё жило бы в памяти: пропадало при перезапуске
 * и было бы своим у каждой копии приложения.
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationStoreConfiguration {

    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcOperations jdbc) {
        return new JdbcRegisteredClientRepository(jdbc);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(JdbcOperations jdbc, RegisteredClientRepository clients) {
        // Коды и токены — в БД только хешами, см. HashedTokenAuthorizationService.
        return new HashedTokenAuthorizationService(new JdbcOAuth2AuthorizationService(jdbc, clients));
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(JdbcOperations jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }
}
