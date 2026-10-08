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
 * Обратная сторона шага 20, и она важнее удобства: {@code X-Forwarded-For} от <b>недоверенного</b>
 * отправителя игнорируется.
 *
 * <p>Иначе настройка превратилась бы в дыру: обход лимитов стал бы тривиальным — присылай на каждый
 * запрос новый случайный {@code X-Forwarded-For} и получай полное ведро. Именно поэтому Tomcat
 * принимает заголовок только от адресов из {@code internal-proxies}, а умолчание (все частные сети)
 * мы сузили.
 *
 * <p>Здесь доверенным объявлен {@code 10.0.0.5/32}, а подключение в тесте идёт с {@code 127.0.0.1} —
 * то есть заголовок присылает кто-то посторонний. Все запросы должны считаться как один клиент,
 * сколько бы разных адресов в заголовке ни было.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.forward-headers-strategy=native",
                "server.tomcat.remoteip.internal-proxies=10.0.0.5/32"
        })
@Import(TestcontainersConfiguration.class)
class UntrustedForwardedHeaderTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void clearRedis() {
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void forgedForwardedHeaderDoesNotGiveAFreshLimit() {
        // Каждый запрос — с нового «адреса». Доверяй мы заголовку, лимит не кончился бы никогда.
        for (int attempt = 1; attempt <= 20; attempt++) {
            assertThat(isRejectedByRateLimit(attemptLoginAs("203.0.113." + attempt)))
                    .as("попытка %d из 20 должна проходить лимит", attempt)
                    .isFalse();
        }

        assertThat(isRejectedByRateLimit(attemptLoginAs("203.0.113.200")))
                .as("заголовок от постороннего должен игнорироваться, лимит — исчерпан")
                .isTrue();
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
