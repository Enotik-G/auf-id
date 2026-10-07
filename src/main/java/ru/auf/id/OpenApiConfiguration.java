package ru.auf.id;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Заголовок документации API в Swagger UI: /swagger-ui/index.html, JSON — /v3/api-docs. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI authServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Auth Service API")
                .version("v1")
                .description("""
                        Единый сервис входа (SSO) цифровой экосистемы колледжа.
                        Регистрация открыта для любой почты — аккаунт становится активным
                        после перехода по ссылке из письма.

                        Ошибки возвращаются в формате RFC 9457 (`application/problem+json`).
                        Локально письма можно посмотреть в Mailpit: http://localhost:8025
                        """));
    }
}
