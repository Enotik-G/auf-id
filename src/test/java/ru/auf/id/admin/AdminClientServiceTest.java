package ru.auf.id.admin;

import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.authserver.AuthorizationStoreConfiguration;
import ru.auf.id.authserver.RefreshTokenReuseDetector;
import ru.auf.id.login.PepperedPasswordEncoder;
import ru.auf.id.user.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestcontainersConfiguration.class, AuthorizationStoreConfiguration.class,
        AdminClientService.class, PepperedPasswordEncoder.class, PasswordHasher.class})
class AdminClientServiceTest {

    /**
     * Этот тест — срез без Redis, а {@code AuthorizationStoreConfiguration} собирает хранилище
     * авторизаций вместе с выявлением кражи refresh-токенов, которому Redis нужен. Регистрация
     * клиентов к этому отношения не имеет, поэтому подменяем заглушкой.
     */
    @MockitoBean
    private RefreshTokenReuseDetector refreshTokenReuseDetector;

    @Autowired
    private AdminClientService service;

    @Autowired
    private RegisteredClientRepository clients;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ─────────────────── настольный клиент (NATIVE) и вкладка (BROWSER) ───────────────────

    /**
     * Настольному приложению секрет выдаётся — без него Spring не даёт обменять refresh-токен
     * (см. {@code ClientKind.NATIVE}). Настоящая защита здесь — PKCE, и он обязателен.
     */
    @Test
    void registersNativeClientWithSecretAndPkce() {
        ClientCredentials credentials = service.register(nativeSpec("planner"));

        assertThat(credentials.secret()).isNotBlank();
        RegisteredClient stored = clients.findByClientId("planner");
        assertThat(stored.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        // В базе — только хеш секрета.
        assertThat(stored.getClientSecret()).isNotEqualTo(credentials.secret());
        assertThat(passwordEncoder.matches(credentials.secret(), stored.getClientSecret())).isTrue();
        assertThat(stored.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(stored.getRedirectUris()).containsExactly("https://planner.college.ru/callback");
        assertThat(stored.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
    }

    /** У вкладки секрета нет: спрятать его в браузере негде. */
    @Test
    void browserClientGetsNoSecret() {
        ClientCredentials credentials = service.register(new ClientSpec("spa", "Вкладка",
                ClientKind.BROWSER, Set.of("https://spa.college.ru/callback"), Set.of(), Set.of()));

        assertThat(credentials.secret()).isNull();
        assertThat(clients.findByClientId("spa").getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.NONE);
    }

    /**
     * Настольному приложению refresh-токен выдаётся: иначе через 10 минут лаунчер обязан снова
     * вести человека в браузер.
     */
    @Test
    void nativeClientGetsRotatingRefreshTokenForThirtyDays() {
        service.register(nativeSpec("launcher"));

        RegisteredClient stored = clients.findByClientId("launcher");
        assertThat(stored.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
                AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(stored.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(Duration.ofDays(30));
        // Ротация: у Spring по умолчанию reuseRefreshTokens = true, то есть на обновление вернулся бы
        // тот же токен и кража ничем бы себя не выдала.
        assertThat(stored.getTokenSettings().isReuseRefreshTokens()).isFalse();
    }

    /**
     * Браузерной вкладке refresh-токен не выдаётся: спрятать его от XSS негде, а живёт он 30 дней.
     * Вкладка продлевает вход молчаливым заходом на /oauth2/authorize.
     */
    @Test
    void browserClientGetsNoRefreshToken() {
        service.register(new ClientSpec("spa", "Вкладка", ClientKind.BROWSER,
                Set.of("https://spa.college.ru/callback"), Set.of(), Set.of()));

        RegisteredClient stored = clients.findByClientId("spa");
        assertThat(stored.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(stored.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE);
    }

    /** Оба публичных вида различимы в списке — иначе администратор не увидит, кому дали refresh-токен. */
    @Test
    void nativeAndBrowserAreToldApartInTheList() {
        service.register(nativeSpec("launcher"));
        service.register(new ClientSpec("spa", "Вкладка", ClientKind.BROWSER,
                Set.of("https://spa.college.ru/callback"), Set.of(), Set.of()));

        assertThat(service.list())
                .extracting(ClientSummary::clientId, ClientSummary::kind)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("launcher", ClientKind.NATIVE),
                        org.assertj.core.groups.Tuple.tuple("spa", ClientKind.BROWSER));
    }

    /** Возврат на свободный локальный порт — то, как входит настольное приложение (RFC 8252). */
    @Test
    void acceptsLoopbackRedirectUriOfNativeClient() {
        service.register(new ClientSpec("launcher", "Лаунчер", ClientKind.NATIVE,
                Set.of("http://127.0.0.1:8090/callback"), Set.of(), Set.of()));

        assertThat(clients.findByClientId("launcher").getRedirectUris())
                .containsExactly("http://127.0.0.1:8090/callback");
    }

    @Test
    void refusesPublicClientWithoutRedirectUri() {
        assertThatThrownBy(() -> service.register(new ClientSpec(
                "planner", "Планировщик", ClientKind.NATIVE, Set.of(), Set.of(), Set.of())))
                .isInstanceOf(InvalidClientSpecException.class)
                .hasMessageContaining("адрес возврата");
    }

    // ─────────────────────────── конфиденциальный клиент ───────────────────────────

    @Test
    void registersConfidentialClientAndStoresOnlyHashOfTheSecret() {
        ClientCredentials credentials = service.register(new ClientSpec(
                "board", "Доска", ClientKind.CONFIDENTIAL,
                Set.of("https://board.college.ru/callback"), Set.of("https://board.college.ru/"), Set.of()));

        assertThat(credentials.secret()).isNotBlank();
        RegisteredClient stored = clients.findByClientId("board");
        assertThat(stored.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(stored.getClientSecret()).isNotEqualTo(credentials.secret());
        assertThat(passwordEncoder.matches(credentials.secret(), stored.getClientSecret())).isTrue();
        // PKCE обязателен и для серверного клиента: так требует OAuth 2.1.
        assertThat(stored.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(stored.getPostLogoutRedirectUris()).containsExactly("https://board.college.ru/");
    }

    // ─────────────────────────── сервисный клиент ───────────────────────────

    @Test
    void registersServiceClientForMachineToMachine() {
        ClientCredentials credentials = service.register(new ClientSpec(
                "mc-server", "Minecraft-сервер", ClientKind.SERVICE, Set.of(), Set.of(), Set.of("users.read")));

        assertThat(credentials.secret()).isNotBlank();
        RegisteredClient stored = clients.findByClientId("mc-server");
        assertThat(stored.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(stored.getRedirectUris()).isEmpty();
        assertThat(stored.getScopes()).containsExactly("users.read");
    }

    /** Возвращаться некуда: в потоке client_credentials человека нет. */
    @Test
    void refusesServiceClientWithRedirectUri() {
        assertThatThrownBy(() -> service.register(new ClientSpec(
                "mc-server", "Minecraft-сервер", ClientKind.SERVICE,
                Set.of("https://mc.college.ru/callback"), Set.of(), Set.of("users.read"))))
                .isInstanceOf(InvalidClientSpecException.class);
    }

    /** Умолчания openid/profile/email сервисному клиенту не подходят: профиля у него нет. */
    @Test
    void refusesServiceClientWithoutScopes() {
        assertThatThrownBy(() -> service.register(new ClientSpec(
                "mc-server", "Minecraft-сервер", ClientKind.SERVICE, Set.of(), Set.of(), Set.of())))
                .isInstanceOf(InvalidClientSpecException.class)
                .hasMessageContaining("право");
    }

    // ─────────────────────────── проверки ───────────────────────────

    @Test
    void refusesDuplicateClientId() {
        service.register(nativeSpec("planner"));

        assertThatThrownBy(() -> service.register(nativeSpec("planner")))
                .isInstanceOf(ClientAlreadyExistsException.class);
    }

    @Test
    void refusesMalformedClientId() {
        assertThatThrownBy(() -> service.register(nativeSpec("Планировщик")))
                .isInstanceOf(InvalidClientSpecException.class);
        assertThatThrownBy(() -> service.register(nativeSpec("a")))
                .isInstanceOf(InvalidClientSpecException.class);
    }

    /** По фрагменту сравнивать нельзя: браузер его серверу не отправляет. */
    @Test
    void refusesRedirectUriWithFragmentOrRelative() {
        assertThatThrownBy(() -> service.register(new ClientSpec(
                "planner", "Планировщик", ClientKind.NATIVE,
                Set.of("https://planner.college.ru/callback#token"), Set.of(), Set.of())))
                .isInstanceOf(InvalidClientSpecException.class);

        assertThatThrownBy(() -> service.register(new ClientSpec(
                "planner", "Планировщик", ClientKind.NATIVE, Set.of("/callback"), Set.of(), Set.of())))
                .isInstanceOf(InvalidClientSpecException.class);
    }

    @Test
    void refusesMalformedScope() {
        assertThatThrownBy(() -> service.register(new ClientSpec(
                "planner", "Планировщик", ClientKind.NATIVE,
                Set.of("https://planner.college.ru/callback"), Set.of(), Set.of("Права Админа"))))
                .isInstanceOf(InvalidClientSpecException.class);
    }

    // ─────────────────────────── ротация и снятие регистрации ───────────────────────────

    @Test
    void rotatingSecretInvalidatesThePreviousOne() {
        ClientCredentials first = service.register(new ClientSpec(
                "board", "Доска", ClientKind.CONFIDENTIAL,
                Set.of("https://board.college.ru/callback"), Set.of(), Set.of()));

        ClientCredentials second = service.rotateSecret("board");

        assertThat(second.secret()).isNotEqualTo(first.secret());
        String storedHash = clients.findByClientId("board").getClientSecret();
        assertThat(passwordEncoder.matches(second.secret(), storedHash)).isTrue();
        assertThat(passwordEncoder.matches(first.secret(), storedHash)).isFalse();
    }

    /** Секрета нет только у вкладки — ей и отказываем. */
    @Test
    void refusesToRotateSecretOfBrowserClient() {
        service.register(new ClientSpec("spa", "Вкладка", ClientKind.BROWSER,
                Set.of("https://spa.college.ru/callback"), Set.of(), Set.of()));

        assertThatThrownBy(() -> service.rotateSecret("spa"))
                .isInstanceOf(InvalidClientSpecException.class)
                .hasMessageContaining("BROWSER");
    }

    /** Настольному приложению секрет выдан, значит его можно и заменить. */
    @Test
    void rotatesSecretOfNativeClient() {
        String firstSecret = service.register(nativeSpec("launcher")).secret();

        String secondSecret = service.rotateSecret("launcher").secret();

        assertThat(secondSecret).isNotBlank().isNotEqualTo(firstSecret);
        RegisteredClient stored = clients.findByClientId("launcher");
        assertThat(passwordEncoder.matches(secondSecret, stored.getClientSecret())).isTrue();
        assertThat(passwordEncoder.matches(firstSecret, stored.getClientSecret())).isFalse();
    }

    @Test
    void unregistersClient() {
        service.register(nativeSpec("planner"));

        service.unregister("planner");

        assertThat(clients.findByClientId("planner")).isNull();
    }

    @Test
    void refusesUnknownClient() {
        assertThatThrownBy(() -> service.get("nobody")).isInstanceOf(ClientNotFoundException.class);
        assertThatThrownBy(() -> service.rotateSecret("nobody")).isInstanceOf(ClientNotFoundException.class);
        assertThatThrownBy(() -> service.unregister("nobody")).isInstanceOf(ClientNotFoundException.class);
    }

    @Test
    void confidentialAndServiceClientsGetNoRefreshToken() {
        service.register(new ClientSpec("board", "Доска", ClientKind.CONFIDENTIAL,
                Set.of("https://board.college.ru/callback"), Set.of(), Set.of()));
        service.register(new ClientSpec("mc-server", "Minecraft", ClientKind.SERVICE,
                Set.of(), Set.of(), Set.of("users.read")));

        assertThat(clients.findByClientId("board").getAuthorizationGrantTypes())
                .doesNotContain(AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(clients.findByClientId("mc-server").getAuthorizationGrantTypes())
                .doesNotContain(AuthorizationGrantType.REFRESH_TOKEN);
    }

    // ─────────────────────────── список ───────────────────────────

    @Test
    void listsClientsWithTheirKind() {
        service.register(nativeSpec("planner"));
        service.register(new ClientSpec("board", "Доска", ClientKind.CONFIDENTIAL,
                Set.of("https://board.college.ru/callback"), Set.of(), Set.of()));
        service.register(new ClientSpec("mc-server", "Minecraft", ClientKind.SERVICE,
                Set.of(), Set.of(), Set.of("users.read")));

        assertThat(service.list())
                .extracting(ClientSummary::clientId, ClientSummary::kind)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("planner", ClientKind.NATIVE),
                        org.assertj.core.groups.Tuple.tuple("board", ClientKind.CONFIDENTIAL),
                        org.assertj.core.groups.Tuple.tuple("mc-server", ClientKind.SERVICE));
    }

    private static ClientSpec nativeSpec(String clientId) {
        return new ClientSpec(clientId, "Планировщик", ClientKind.NATIVE,
                Set.of("https://planner.college.ru/callback"), Set.of(), Set.of());
    }
}
