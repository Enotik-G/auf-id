package ru.auf.id.ratelimit;

import ru.auf.id.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Шаг 20: за обратным прокси лимиты считаются по адресу <b>человека</b>, а не прокси.
 *
 * <p>Нужен настоящий Tomcat ({@code RANDOM_PORT}), а не MockMvc: заголовок {@code X-Forwarded-For}
 * разбирает клапан Tomcat ({@code RemoteIpValve}), и в MockMvc его просто нет.
 *
 * <p>Здесь прокси доверенный: подключение идёт с {@code 127.0.0.1}, а {@code internal-proxies} по
 * умолчанию — {@code 127.0.0.1/32}. Обратный случай проверяет {@link UntrustedForwardedHeaderTest}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.forward-headers-strategy=native")
@Import(TestcontainersConfiguration.class)
class ForwardedClientAddressTest {

    private static final String FIRST_STUDENT = "203.0.113.7";
    private static final String SECOND_STUDENT = "203.0.113.8";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void clearRedis() {
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void loginLimitCountsTheForwardedAddress() {
        for (int attempt = 1; attempt <= 20; attempt++) {
            assertThat(isRejectedByRateLimit(attemptLoginAs(FIRST_STUDENT)))
                    .as("попытка %d из 20 должна проходить лимит", attempt)
                    .isFalse();
        }

        assertThat(isRejectedByRateLimit(attemptLoginAs(FIRST_STUDENT))).isTrue();
    }

    /**
     * Самое важное: исчерпав лимит, один человек не закрывает вход остальным.
     *
     * <p>Ровно это и ломалось бы за прокси без шага 20 — и выглядело бы как «утром сайт не
     * пускает», хотя ни один аккаунт не заблокирован.
     */
    @Test
    void oneStudentExhaustingTheLimitDoesNotBlockAnother() {
        for (int attempt = 1; attempt <= 21; attempt++) {
            attemptLoginAs(FIRST_STUDENT);
        }
        assertThat(isRejectedByRateLimit(attemptLoginAs(FIRST_STUDENT))).isTrue();

        assertThat(isRejectedByRateLimit(attemptLoginAs(SECOND_STUDENT))).isFalse();
    }

    /**
     * Запрос на вход от имени указанного адреса. Берём HttpClient из JDK, а не TestRestTemplate:
     * тот переехал в отдельный модуль, а тащить зависимость ради двух тестов незачем.
     *
     * <p>Перенаправления не следуем ({@code Redirect.NEVER} — умолчание): нам нужен сам ответ
     * фильтра, а не страница, на которую он отправляет.
     */
    private HttpResponse<String> attemptLoginAs(String clientAddress) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/login"))
                .header("X-Forwarded-For", clientAddress)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("username=nobody%40mail.ru&password=wrong"))
                .build();
        try {
            return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Запрос на вход не удался", e);
        }
    }

    /**
     * Отказ по лимиту — это возврат на страницу входа с {@code ?tooManyAttempts}
     * ({@code RateLimitFilter}). Остальные ответы (в том числе отказ из-за CSRF) лимитом не
     * вызваны: фильтр лимитов стоит до Spring Security, и до неё дело просто не доходит.
     */
    private static boolean isRejectedByRateLimit(HttpResponse<String> response) {
        return response.headers().firstValue("Location")
                .filter(location -> location.endsWith("/login?tooManyAttempts"))
                .isPresent();
    }
}
