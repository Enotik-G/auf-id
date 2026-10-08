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
                .title("AUF ID API")
                .version("v1")
                .description("""
                        Единый сервис входа (SSO, OpenID Connect) цифровой экосистемы колледжа.

                        Саморегистрации нет: учётку заводит администратор, а человек получает
                        одноразовую ссылку активации и задаёт по ней пароль. Писем сервис не
                        отправляет — ссылку администратор передаёт лично.

                        Ошибки возвращаются в формате RFC 9457 (`application/problem+json`).
                        """));
    }
}
