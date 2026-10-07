package ru.auf.id.admin;

import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.authserver.AuthorizationStoreConfiguration;
import ru.auf.id.login.PepperedPasswordEncoder;
import ru.auf.id.user.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestcontainersConfiguration.class, AuthorizationStoreConfiguration.class,
        AdminClientService.class, PepperedPasswordEncoder.class, PasswordHasher.class})
class AdminClientServiceTest {

    @Autowired
    private AdminClientService service;

    @Autowired
    private RegisteredClientRepository clients;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ─────────────────────────── публичный клиент ───────────────────────────

    @Test
    void registersPublicClientWithoutSecretAndWithPkce() {
        ClientCredentials credentials = service.register(publicSpec("planner"));

        assertThat(credentials.secret()).isNull();
        RegisteredClient stored = clients.findByClientId("planner");
        assertThat(stored.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(stored.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(stored.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(stored.getRedirectUris()).containsExactly("https://planner.college.ru/callback");
        assertThat(stored.getScopes()).containsExactlyInAnyOrder("openid", "profile", "email");
    }

    @Test
    void refusesPublicClientWithoutRedirectUri() {
        assertThatThrownBy(() -> service.register(new ClientSpec(
                "planner", "Планировщик", ClientKind.PUBLIC, Set.of(), Set.of(), Set.of())))
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
        service.register(publicSpec("planner"));

        assertThatThrownBy(() -> service.register(publicSpec("planner")))
                .isInstanceOf(ClientAlreadyExistsException.class);
    }

    @Test
    void refusesMalformedClientId() {
        assertThatThrownBy(() -> service.register(publicSpec("Планировщик")))
                .isInstanceOf(InvalidClientSpecException.class);
        assertThatThrownBy(() -> service.register(publicSpec("a")))
                .isInstanceOf(InvalidClientSpecException.class);
    }

    /** По фрагменту сравнивать нельзя: браузер его серверу не отправляет. */
    @Test
    void refusesRedirectUriWithFragmentOrRelative() {
        assertThatThrownBy(() -> service.register(new ClientSpec(
                "planner", "Планировщик", ClientKind.PUBLIC,
                Set.of("https://planner.college.ru/callback#token"), Set.of(), Set.of())))
                .isInstanceOf(InvalidClientSpecException.class);

        assertThatThrownBy(() -> service.register(new ClientSpec(
                "planner", "Планировщик", ClientKind.PUBLIC, Set.of("/callback"), Set.of(), Set.of())))
                .isInstanceOf(InvalidClientSpecException.class);
    }

    @Test
    void refusesMalformedScope() {
        assertThatThrownBy(() -> service.register(new ClientSpec(
                "planner", "Планировщик", ClientKind.PUBLIC,
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

    @Test
    void refusesToRotateSecretOfPublicClient() {
        service.register(publicSpec("planner"));

        assertThatThrownBy(() -> service.rotateSecret("planner"))
                .isInstanceOf(InvalidClientSpecException.class);
    }

    @Test
    void unregistersClient() {
        service.register(publicSpec("planner"));

        service.unregister("planner");

        assertThat(clients.findByClientId("planner")).isNull();
    }

    @Test
    void refusesUnknownClient() {
        assertThatThrownBy(() -> service.get("nobody")).isInstanceOf(ClientNotFoundException.class);
        assertThatThrownBy(() -> service.rotateSecret("nobody")).isInstanceOf(ClientNotFoundException.class);
        assertThatThrownBy(() -> service.unregister("nobody")).isInstanceOf(ClientNotFoundException.class);
    }

    // ─────────────────────────── список ───────────────────────────

    @Test
    void listsClientsWithTheirKind() {
        service.register(publicSpec("planner"));
        service.register(new ClientSpec("board", "Доска", ClientKind.CONFIDENTIAL,
                Set.of("https://board.college.ru/callback"), Set.of(), Set.of()));
        service.register(new ClientSpec("mc-server", "Minecraft", ClientKind.SERVICE,
                Set.of(), Set.of(), Set.of("users.read")));

        assertThat(service.list())
                .extracting(ClientSummary::clientId, ClientSummary::kind)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("planner", ClientKind.PUBLIC),
                        org.assertj.core.groups.Tuple.tuple("board", ClientKind.CONFIDENTIAL),
                        org.assertj.core.groups.Tuple.tuple("mc-server", ClientKind.SERVICE));
    }

    private static ClientSpec publicSpec(String clientId) {
        return new ClientSpec(clientId, "Планировщик", ClientKind.PUBLIC,
                Set.of("https://planner.college.ru/callback"), Set.of(), Set.of());
    }
}
