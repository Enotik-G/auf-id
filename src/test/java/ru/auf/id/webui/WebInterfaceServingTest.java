package ru.auf.id.webui;

import ru.auf.id.TestTime;
import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.PasswordCredential;
import ru.auf.id.user.PasswordCredentialRepository;
import ru.auf.id.user.PasswordHasher;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интерфейс отдаётся по-настоящему: вход формой, затем {@code GET /account} — и в ответе сам
 * {@code index.html}.
 *
 * <p>Нужен настоящий Tomcat ({@code RANDOM_PORT}): MockMvc внутреннюю переадресацию на
 * {@code /index.html} не выполняет, а только запоминает. А именно на ней всё и держится: Spring
 * Security проверяет и переадресацию, и заголовки ответа пишет уже после неё — так этот тест и
 * поймал пропавший CSP (см. {@link WebInterfaceController#webInterface}).
 *
 * <p>{@code index.html} кладёт в {@code static/} сборка Maven (из {@code frontend/dist}); без
 * собранного интерфейса этот тест падает — так и задумано.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class WebInterfaceServingTest {

    private static final String EMAIL = "ivan@sinhub.ru";
    private static final String PASSWORD = "пароль-студента-для-теста";
    private static final Pattern CSRF_FIELD = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordCredentialRepository credentialRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private StringRedisTemplate redis;

    /** Свой «браузер»: запоминает cookie и не идёт по редиректам сам — их проверяем мы. */
    private final HttpClient browser = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @BeforeEach
    void createStudent() {
        User student = User.invited(new EmailAddress(EMAIL), "Иван Петров", TestTime.NOW);
        student.activate();
        userRepository.save(student);
        credentialRepository.save(PasswordCredential.forUser(student, passwordHasher.hash(PASSWORD), TestTime.NOW));
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void loggedInUserGetsTheWebInterfacePage() throws Exception {
        logIn();

        HttpResponse<String> page = get("/account/password");

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("<div id=\"root\">");
        assertThat(page.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(
                csp -> assertThat(csp).contains("script-src 'self'"));
    }

    @Test
    void studentGetsNoAdminPanelEvenThroughRealServer() throws Exception {
        logIn();

        assertThat(get("/admin").statusCode()).isEqualTo(403);
    }

    private void logIn() throws Exception {
        String loginPage = get("/login").body();
        Matcher csrf = CSRF_FIELD.matcher(loginPage);
        assertThat(csrf.find()).as("поле _csrf на странице входа").isTrue();

        String form = "username=" + encode(EMAIL) + "&password=" + encode(PASSWORD) + "&_csrf=" + encode(csrf.group(1));
        HttpResponse<String> login = browser.send(HttpRequest.newBuilder(uri("/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.headers().firstValue("Location")).as("вход удался").hasValueSatisfying(
                location -> assertThat(location).doesNotContain("error"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
