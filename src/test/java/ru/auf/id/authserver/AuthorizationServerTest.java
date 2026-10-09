package ru.auf.id.authserver;

import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.PasswordCredential;
import ru.auf.id.user.PasswordCredentialRepository;
import ru.auf.id.user.PasswordHasher;
import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Весь путь «войти через Auth» по OAuth 2.1 + OIDC — так, как его пройдёт планировщик.
 * Клиент — launcher-dev (вид NATIVE: с refresh-токеном), его при запуске заводит в БД
 * DevClientRegistration вместе с planner-dev. Берём именно лаунчер: у него поток полнее,
 * обновление токена есть только здесь.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthorizationServerTest {

    private static final String CLIENT_ID = DevClientRegistration.LAUNCHER_CLIENT_ID;
    private static final String REDIRECT_URI = "http://127.0.0.1:8090/login/oauth2/code/auth";

    private static final String EMAIL = "ivan@mail.ru";
    private static final String PASSWORD = "correct horse battery staple";

    /** PKCE: секрет, который знает только клиент; в запрос на вход уходит его хеш (challenge). */
    private final String codeVerifier = "verifier-" + UUID.randomUUID() + "-" + UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordCredentialRepository credentialRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private StringRedisTemplate redis;

    private User user;

    @BeforeEach
    void createActiveUser() {
        user = User.invited(new EmailAddress(EMAIL), "Иван Петров");
        user.activate();
        userRepository.save(user);
        credentialRepository.save(PasswordCredential.forUser(user, passwordHasher.hash(PASSWORD)));
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DevClientRegistration devClientRegistration;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM oauth2_authorization");
        userRepository.deleteAll();
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void discoveryDocumentDescribesTheServer() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:8080"))
                .andExpect(jsonPath("$.jwks_uri").value("http://localhost:8080/oauth2/jwks"))
                .andExpect(jsonPath("$.token_endpoint").value("http://localhost:8080/oauth2/token"));
    }

    @Test
    void discoveryAnnouncesTheAlgorithmTokensAreSignedWith() throws Exception {
        // Токены подписаны ES256 (ключ EC P-256) — discovery должен говорить то же, а не RS256 по умолчанию.
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_token_signing_alg_values_supported.length()").value(1))
                .andExpect(jsonPath("$.id_token_signing_alg_values_supported[0]").value("ES256"));
    }

    /**
     * Discovery обязан объявлять способ аутентификации {@code none}: вид клиента {@code BROWSER}
     * (приложение в браузерной вкладке) ходит за токеном без секрета, его защищает PKCE.
     *
     * <p>Spring перечисляет только способы с секретом или сертификатом. Строгая библиотека
     * OIDC-клиента, не найдя в списке своего способа, откажется идти за токеном — поэтому
     * проверяем. Ошибка того же рода, что RS256 вместо ES256 в тесте выше.
     */
    @Test
    void discoveryAnnouncesThatPublicClientsAreAccepted() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_endpoint_auth_methods_supported").value(hasItem("none")));
    }

    /** Метаданные отдают две ручки, и список способов в них свой у каждой — проверяем обе. */
    @Test
    void oauthMetadataAlsoAnnouncesThatPublicClientsAreAccepted() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_endpoint_auth_methods_supported").value(hasItem("none")));
    }

    @Test
    void publicKeysArePublished() throws Exception {
        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys.length()").value(1))
                .andExpect(jsonPath("$.keys[0].kty").value("EC"))
                .andExpect(jsonPath("$.keys[0].crv").value("P-256"))
                .andExpect(jsonPath("$.keys[0].kid").exists())
                // Только открытая часть ключа: закрытой (d) здесь быть не должно.
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    @Test
    void notLoggedInBrowserIsSentToLoginPage() throws Exception {
        mockMvc.perform(get("/oauth2/authorize").queryParams(authorizeParams()).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loggedInUserGetsCodeAndExchangesItForTokens() throws Exception {
        String code = authorize();

        MvcResult tokenResponse = mockMvc.perform(post("/oauth2/token")
                        .with(clientSecret())
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.id_token").exists())
                .andReturn();

        String accessToken = JsonPath.read(tokenResponse.getResponse().getContentAsString(), "$.access_token");
        Jwt jwt = jwtDecoder.decode(accessToken);
        String idToken = JsonPath.read(tokenResponse.getResponse().getContentAsString(), "$.id_token");

        // Оба токена подписаны нашим постоянным ключом ES256.
        assertThat(jwt.getHeaders()).containsEntry("alg", "ES256");
        assertThat(jwtDecoder.decode(idToken).getHeaders()).containsEntry("alg", "ES256");

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getIssuer()).hasToString("http://localhost:8080");
        assertThat(jwt.getAudience()).containsExactly(CLIENT_ID);
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void issuedAuthorizationIsStoredInDatabase() throws Exception {
        exchangeCodeForTokens(authorize());

        Integer stored = jdbc.queryForObject(
                "SELECT count(*) FROM oauth2_authorization WHERE principal_name = ? AND access_token_value IS NOT NULL",
                Integer.class, user.getId().toString());
        assertThat(stored).isEqualTo(1);
    }

    /**
     * Утечка дампа БД не должна выдавать живые коды и токены: в колонках значений лежат только
     * их хеши SHA-256, предъявить такой «токен» нельзя.
     *
     * <p>ФИО и почта этим <b>не закрыты</b>: они остаются в {@code *_metadata} —
     * см. {@link HashedTokenAuthorizationService}.
     */
    @Test
    void codeAndTokensAreStoredOnlyAsHashes() throws Exception {
        String code = authorize();
        String tokens = exchangeCodeForTokens(code);
        String accessToken = JsonPath.read(tokens, "$.access_token");
        String idToken = JsonPath.read(tokens, "$.id_token");

        var row = jdbc.queryForMap(
                "SELECT authorization_code_value, access_token_value, oidc_id_token_value"
                        + " FROM oauth2_authorization WHERE principal_name = ?",
                user.getId().toString());

        assertThat(row.get("authorization_code_value")).isEqualTo(HashedTokenAuthorizationService.hash(code));
        assertThat(row.get("access_token_value")).isEqualTo(HashedTokenAuthorizationService.hash(accessToken));
        assertThat(row.get("oidc_id_token_value")).isEqualTo(HashedTokenAuthorizationService.hash(idToken));
        assertThat(row.values()).allSatisfy(value -> assertThat((String) value).startsWith("sha256:"));
    }

    /**
     * Код одноразовый: второй обмен того же кода отклоняется.
     *
     * <p>Тест стоит именно здесь, рядом с хешированием: отметку «код использован» Spring хранит в
     * метаданных токена, а {@link HashedTokenAuthorizationService} пересобирает токен, подменяя
     * значение. Потеряйся при этом метаданные — код остался бы одноразовым только на словах,
     * и заметить это по остальным тестам было бы нельзя.
     */
    @Test
    void sameCodeCannotBeExchangedTwice() throws Exception {
        String code = authorize();
        exchangeCodeForTokens(code);

        mockMvc.perform(post("/oauth2/token")
                        .with(clientSecret())
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    // ─────────────────────────── refresh-токены (шаг 16) ───────────────────────────

    /**
     * Лаунчер получает refresh-токен вместе с access-токеном и меняет его на новый access-токен
     * без участия человека. Без этого вход живёт 10 минут, и лаунчер каждые 10 минут открывал бы
     * браузер заново.
     */
    @Test
    void refreshTokenBuysANewAccessTokenWithoutTheUser() throws Exception {
        String refreshToken = JsonPath.read(exchangeCodeForTokens(authorize()), "$.refresh_token");

        String refreshed = refresh(refreshToken);

        Jwt accessToken = jwtDecoder.decode(JsonPath.read(refreshed, "$.access_token"));
        assertThat(accessToken.getSubject()).isEqualTo(user.getId().toString());
        assertThat(Duration.between(accessToken.getIssuedAt(), accessToken.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(10));
    }

    /**
     * Ротация: обновление выдаёт <b>новый</b> refresh-токен, прежний перестаёт работать.
     *
     * <p>У Spring по умолчанию наоборот ({@code reuseRefreshTokens = true}) — возвращается тот же
     * токен. Тогда украденный токен работал бы все 30 дней, и кража ничем бы себя не выдала.
     */
    @Test
    void refreshTokenIsRotatedAndTheOldOneStopsWorking() throws Exception {
        String firstRefreshToken = JsonPath.read(exchangeCodeForTokens(authorize()), "$.refresh_token");

        String secondRefreshToken = JsonPath.read(refresh(firstRefreshToken), "$.refresh_token");
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        mockMvc.perform(post("/oauth2/token")
                        .with(clientSecret())
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", firstRefreshToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    /** Срок refresh-токена отсчитывается заново от каждого обновления — скользящее окно 30 дней. */
    @Test
    void refreshTokenIsStoredWithAThirtyDayLifetime() throws Exception {
        exchangeCodeForTokens(authorize());

        var row = jdbc.queryForMap(
                "SELECT refresh_token_issued_at, refresh_token_expires_at FROM oauth2_authorization"
                        + " WHERE principal_name = ?",
                user.getId().toString());

        Duration lifetime = Duration.between(
                ((java.sql.Timestamp) row.get("refresh_token_issued_at")).toInstant(),
                ((java.sql.Timestamp) row.get("refresh_token_expires_at")).toInstant());
        assertThat(lifetime).isEqualTo(Duration.ofDays(30));
    }

    /** В БД refresh-токен тоже лежит хешем (шаг 12), а не открытым текстом. */
    @Test
    void refreshTokenIsStoredOnlyAsHash() throws Exception {
        String refreshToken = JsonPath.read(exchangeCodeForTokens(authorize()), "$.refresh_token");

        String stored = jdbc.queryForObject(
                "SELECT refresh_token_value FROM oauth2_authorization WHERE principal_name = ?",
                String.class, user.getId().toString());

        assertThat(stored).isEqualTo(HashedTokenAuthorizationService.hash(refreshToken));
    }

    /**
     * Секрет клиента в заголовке Basic — так его предъявляет настольное приложение.
     * Защищает здесь не он, а PKCE: см. {@code ClientKind.NATIVE}.
     */
    private static RequestPostProcessor clientSecret() {
        return httpBasic(CLIENT_ID, DevClientRegistration.LAUNCHER_CLIENT_SECRET);
    }

    private String refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/oauth2/token")
                        .with(clientSecret())
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void userinfoReturnsSubjectForIssuedToken() throws Exception {
        String accessToken = JsonPath.read(exchangeCodeForTokens(authorize()), "$.access_token");

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value(user.getId().toString()))
                .andExpect(jsonPath("$.name").value("Иван Петров"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                // Почту никто не подтверждал: админ назначил адрес, доступа к ящику у студента нет.
                .andExpect(jsonPath("$.email_verified").value(false));
    }

    @Test
    void devClientsAreRegisteredInDatabaseOnlyOnce() {
        devClientRegistration.run(null);
        devClientRegistration.run(null);

        // Оба dev-клиента: лаунчер и планировщик делаются одновременно, и каждому нужен свой.
        assertThat(countOf(DevClientRegistration.LAUNCHER_CLIENT_ID)).isEqualTo(1);
        assertThat(countOf(DevClientRegistration.PLANNER_CLIENT_ID)).isEqualTo(1);
    }

    // ─────────────────── продление входа для браузерного клиента (планировщик) ───────────────────

    /**
     * Планировщик живёт в браузере: refresh-токена у него нет и секрета тоже — украл бы XSS.
     */
    @Test
    void plannerDevClientIsPublicAndHasNoRefreshTokenGrant() {
        var client = jdbc.queryForMap(
                "SELECT authorization_grant_types, client_authentication_methods, client_secret"
                        + " FROM oauth2_registered_client WHERE client_id = ?",
                DevClientRegistration.PLANNER_CLIENT_ID);

        assertThat((String) client.get("authorization_grant_types"))
                .contains("authorization_code").doesNotContain("refresh_token");
        assertThat((String) client.get("client_authentication_methods")).isEqualTo("none");
        assertThat(client.get("client_secret")).isNull();
    }

    /**
     * Так планировщик продлевает вход: уже вошедший человек заходит на /oauth2/authorize с
     * {@code prompt=none} и получает новый код <b>без формы входа</b>. Это замена refresh-токену
     * для браузерного приложения, поэтому путь закреплён тестом.
     */
    @Test
    void browserClientRenewsLoginSilentlyWithPromptNone() throws Exception {
        MockHttpSession session = logIn();

        var params = plannerAuthorizeParams();
        params.add("prompt", "none");

        MvcResult result = mockMvc.perform(get("/oauth2/authorize").queryParams(params).session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        var redirect = UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl()).build();
        assertThat(redirect.toUriString()).startsWith(DevClientRegistration.PLANNER_REDIRECT_URI);
        assertThat(redirect.getQueryParams().getFirst("code")).isNotBlank();
        assertThat(redirect.getQueryParams().getFirst("error")).isNull();
    }

    /**
     * Не вошедшему {@code prompt=none} отдаёт ошибку {@code login_required}, а не форму входа:
     * вкладка по этому признаку понимает, что человека надо отправить входить по-настоящему.
     */
    @Test
    void promptNoneWithoutSessionReturnsLoginRequired() throws Exception {
        var params = plannerAuthorizeParams();
        params.add("prompt", "none");

        MvcResult result = mockMvc.perform(get("/oauth2/authorize").queryParams(params))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        var redirect = UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl()).build();
        assertThat(redirect.toUriString()).startsWith(DevClientRegistration.PLANNER_REDIRECT_URI);
        assertThat(redirect.getQueryParams().getFirst("error")).isEqualTo("login_required");
    }

    /**
     * Продлевать вход скрытым iframe нельзя: ответ /oauth2/authorize запрещает встраивание в рамку.
     *
     * <p>Это умолчание Spring Security, и менять его мы не собираемся — защита от clickjacking на
     * странице входа. Но для браузерного клиента это значит, что молчаливое продление делается
     * <b>переходом страницы</b> (или всплывающим окном), а не невидимым iframe, как часто пишут в
     * статьях про SPA. Тест стоит здесь, чтобы это ограничение нашли до того, как напишут iframe.
     */
    @Test
    void authorizeEndpointForbidsBeingEmbeddedInAnIframe() throws Exception {
        mockMvc.perform(get("/oauth2/authorize").queryParams(plannerAuthorizeParams()).session(logIn()))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }

    private org.springframework.util.LinkedMultiValueMap<String, String> plannerAuthorizeParams()
            throws Exception {
        var params = new org.springframework.util.LinkedMultiValueMap<String, String>();
        params.add("response_type", "code");
        params.add("client_id", DevClientRegistration.PLANNER_CLIENT_ID);
        params.add("scope", "openid profile email");
        params.add("redirect_uri", DevClientRegistration.PLANNER_REDIRECT_URI);
        params.add("state", "xyz");
        params.add("code_challenge", codeChallenge(codeVerifier));
        params.add("code_challenge_method", "S256");
        return params;
    }

    private Integer countOf(String clientId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM oauth2_registered_client WHERE client_id = ?", Integer.class, clientId);
    }

    @Test
    void codeWithoutCorrectPkceVerifierIsRejected() throws Exception {
        String code = authorize();

        mockMvc.perform(post("/oauth2/token")
                        .with(clientSecret())
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", "somebody-elses-verifier-" + UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void authorizationWithoutPkceIsRefused() throws Exception {
        var params = authorizeParams();
        params.remove("code_challenge");
        params.remove("code_challenge_method");

        MvcResult result = mockMvc.perform(get("/oauth2/authorize").queryParams(params).session(logIn()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).contains("error=invalid_request");
    }

    @Test
    void tokensCarryNameAndEmailOfTheUser() throws Exception {
        String tokens = exchangeCodeForTokens(authorize());

        assertUserClaims(jwtDecoder.decode(JsonPath.read(tokens, "$.access_token")));
        assertUserClaims(jwtDecoder.decode(JsonPath.read(tokens, "$.id_token")));
    }

    @Test
    void clientThatDidNotAskForScopesGetsNoPersonalClaims() throws Exception {
        var params = authorizeParams();
        params.set("scope", "openid");

        Jwt accessToken = jwtDecoder.decode(JsonPath.read(exchangeCodeForTokens(authorize(params)), "$.access_token"));

        assertThat(accessToken.getSubject()).isEqualTo(user.getId().toString());
        assertThat(accessToken.hasClaim("name")).isFalse();
        assertThat(accessToken.hasClaim("email")).isFalse();
        assertThat(accessToken.hasClaim("email_verified")).isFalse();
    }

    @Test
    void accessTokenCarriesRolesOfTheUser() throws Exception {
        user.grantRole(Role.CURATOR);
        user.grantRole(Role.STUDENT);
        userRepository.save(user);

        Jwt accessToken = jwtDecoder.decode(JsonPath.read(exchangeCodeForTokens(authorize()), "$.access_token"));

        assertThat(accessToken.getClaimAsStringList("roles")).containsExactly("CURATOR", "STUDENT");
    }

    /** Роли решают «пускать или нет» — это дело access token; id token только говорит, кто вошёл. */
    @Test
    void idTokenDoesNotCarryRoles() throws Exception {
        user.grantRole(Role.ADMIN);
        userRepository.save(user);

        Jwt idToken = jwtDecoder.decode(JsonPath.read(exchangeCodeForTokens(authorize()), "$.id_token"));

        assertThat(idToken.hasClaim("roles")).isFalse();
    }

    /** Claim есть всегда, пусть и пустой: клиенту не нужно отличать «нет ролей» от «нет поля». */
    @Test
    void userWithoutRolesGetsEmptyRolesClaim() throws Exception {
        Jwt accessToken = jwtDecoder.decode(JsonPath.read(exchangeCodeForTokens(authorize()), "$.access_token"));

        assertThat(accessToken.hasClaim("roles")).isTrue();
        assertThat(accessToken.getClaimAsStringList("roles")).isEmpty();
    }

    /**
     * Страж ловушки: выданная авторизация сохраняется в БД как JSON, а {@code /userinfo} поднимает её
     * обратно. Если claim с ролями собрать неизменяемой коллекцией JDK, Jackson откажется её прочитать
     * ({@code ImmutableCollections$ListN} нет в списке разрешённых типов) — и отвалится именно здесь,
     * а не при выдаче токена.
     */
    @Test
    void rolesDoNotBreakReadingTheAuthorizationBack() throws Exception {
        user.grantRole(Role.ADMIN);
        userRepository.save(user);
        String accessToken = JsonPath.read(exchangeCodeForTokens(authorize()), "$.access_token");

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sub").value(user.getId().toString()));
    }

    /**
     * Браузер перед {@code fetch} на {@code /oauth2/token} с другого сайта шлёт предварительный запрос
     * (preflight, метод OPTIONS). Сайт из {@code auth.cors.allowed-origins} должен получить в ответ
     * своё имя в {@code Access-Control-Allow-Origin}, иначе браузер не отдаст JavaScript ответ с токеном.
     */
    @Test
    void preflightFromAllowedOriginIsAnswered() throws Exception {
        mockMvc.perform(options("/oauth2/token")
                        .header("Origin", "http://planner.test")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://planner.test"));
    }

    /**
     * Чужой сайт (не из {@code auth.cors.allowed-origins}) разрешения не получает: Spring отвечает 403,
     * и заголовка {@code Access-Control-Allow-Origin} в ответе нет.
     */
    @Test
    void preflightFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/oauth2/token")
                        .header("Origin", "http://evil.test")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    /** Поля name, email, email_verified — стандартные для OpenID Connect, их понимает любой клиент. */
    private void assertUserClaims(Jwt token) {
        assertThat(token.getClaimAsString("name")).isEqualTo("Иван Петров");
        assertThat(token.getClaimAsString("email")).isEqualTo(EMAIL);
        assertThat(token.getClaimAsBoolean("email_verified")).isFalse();
    }

    /** Шаг 2: планировщик меняет код на токены. Возвращает JSON-ответ сервера. */
    private String exchangeCodeForTokens(String code) throws Exception {
        return mockMvc.perform(post("/oauth2/token")
                        .with(clientSecret())
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Настоящий вход через форму, как у человека. Возвращает сессию, в которой он вошёл. */
    private MockHttpSession logIn() throws Exception {
        MvcResult login = mockMvc.perform(formLogin().user(EMAIL).password(PASSWORD))
                .andExpect(authenticated())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    /** Шаг 1: вошедший пользователь идёт на /oauth2/authorize и возвращается в планировщик с одноразовым кодом. */
    private String authorize() throws Exception {
        return authorize(authorizeParams());
    }

    private String authorize(org.springframework.util.LinkedMultiValueMap<String, String> params) throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorize").queryParams(params).session(logIn()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        var redirect = UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl()).build();
        assertThat(redirect.toUriString()).startsWith(REDIRECT_URI);
        assertThat(redirect.getQueryParams().getFirst("state")).isEqualTo("xyz");
        return redirect.getQueryParams().getFirst("code");
    }

    private org.springframework.util.LinkedMultiValueMap<String, String> authorizeParams() throws Exception {
        var params = new org.springframework.util.LinkedMultiValueMap<String, String>();
        params.add("response_type", "code");
        params.add("client_id", CLIENT_ID);
        params.add("scope", "openid profile email");
        params.add("redirect_uri", REDIRECT_URI);
        params.add("state", "xyz");
        params.add("code_challenge", codeChallenge(codeVerifier));
        params.add("code_challenge_method", "S256");
        return params;
    }

    private static String codeChallenge(String verifier) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
