package com.example.planner.authserver;

import com.example.planner.TestcontainersConfiguration;
import com.example.planner.user.EmailAddress;
import com.example.planner.user.PasswordCredential;
import com.example.planner.user.PasswordCredentialRepository;
import com.example.planner.user.PasswordHasher;
import com.example.planner.user.User;
import com.example.planner.user.UserRepository;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Весь путь «войти через Auth» по OAuth 2.1 + OIDC — так, как его пройдёт планировщик.
 * Клиент — временный planner-dev из application.properties.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthorizationServerTest {

    private static final String CLIENT_ID = "planner-dev";
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
        user = User.selfRegistered(new EmailAddress(EMAIL), "Иван Петров");
        user.verifyEmail();
        userRepository.save(user);
        credentialRepository.save(PasswordCredential.forUser(user, passwordHasher.hash(PASSWORD)));
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void discoveryDocumentDescribesTheServer() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:8080"))
                .andExpect(jsonPath("$.jwks_uri").value("http://localhost:8080/oauth2/jwks"))
                .andExpect(jsonPath("$.token_endpoint").value("http://localhost:8080/oauth2/token"));
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
    void codeWithoutCorrectPkceVerifierIsRejected() throws Exception {
        String code = authorize();

        mockMvc.perform(post("/oauth2/token")
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

    /** Настоящий вход через форму, как у человека. Возвращает сессию, в которой он вошёл. */
    private MockHttpSession logIn() throws Exception {
        MvcResult login = mockMvc.perform(formLogin().user(EMAIL).password(PASSWORD))
                .andExpect(authenticated())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    /** Шаг 1: вошедший пользователь идёт на /oauth2/authorize и возвращается в планировщик с одноразовым кодом. */
    private String authorize() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorize").queryParams(authorizeParams()).session(logIn()))
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
        params.add("scope", "openid profile");
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
