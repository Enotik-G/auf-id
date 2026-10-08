package ru.auf.id.admin;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Регистрация сервисов экосистемы как клиентов сервера авторизации.
 *
 * <p>Это тот рубеж, после которого новый сервис подключается без правок кода Auth — только записью
 * в базе.
 */
@Service
public class AdminClientService {

    /** Access token живёт 10 минут: сервисы проверяют его сами, без запроса в Auth (решение архитектуры). */
    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(10);

    /**
     * Refresh-токен живёт 30 дней — и срок считается заново от каждого обновления (решение 2026-10-08).
     *
     * <p>Скользящее окно получается само: при ротации выдаётся новый токен, а ему Spring берёт срок
     * из этой же настройки. Пользуешься — срок продлевается, забросил на месяц — вход заново.
     */
    private static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(30);

    private static final Set<String> DEFAULT_SCOPES =
            Set.of(OidcScopes.OPENID, OidcScopes.PROFILE, OidcScopes.EMAIL);

    private static final Pattern CLIENT_ID = Pattern.compile("[a-z][a-z0-9-]{1,62}[a-z0-9]");
    private static final Pattern SCOPE = Pattern.compile("[a-z][a-z0-9._:-]{0,62}");

    private static final int SECRET_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RegisteredClientRepository clients;
    private final JdbcOperations jdbc;
    private final PasswordEncoder passwordEncoder;

    public AdminClientService(RegisteredClientRepository clients,
                              JdbcOperations jdbc,
                              PasswordEncoder passwordEncoder) {
        this.clients = clients;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * @return секрет, если вид клиента его предполагает; виден только в этот момент
     * @throws ClientAlreadyExistsException если клиент с таким id уже есть
     * @throws InvalidClientSpecException   если настройки несочетаемые или неполные
     */
    @Transactional
    public ClientCredentials register(ClientSpec spec) {
        validate(spec);
        if (clients.findByClientId(spec.clientId()) != null) {
            throw new ClientAlreadyExistsException(spec.clientId());
        }

        String secret = spec.kind().isPublic() ? null : generateSecret();
        clients.save(build(spec, secret));
        return new ClientCredentials(spec.clientId(), secret);
    }

    /**
     * Выдаёт новый секрет взамен прежнего: прежний перестаёт работать сразу.
     *
     * @throws InvalidClientSpecException если у клиента секрета нет по виду
     */
    @Transactional
    public ClientCredentials rotateSecret(String clientId) {
        RegisteredClient existing = require(clientId);
        if (existing.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
            throw new InvalidClientSpecException(
                    "Клиент " + clientId + " публичный (NATIVE или BROWSER), секрета у него нет");
        }

        String secret = generateSecret();
        clients.save(RegisteredClient.from(existing)
                .clientSecret(passwordEncoder.encode(secret))
                .build());
        return new ClientCredentials(clientId, secret);
    }

    /**
     * Снимает регистрацию. Выданные клиенту авторизации уходят вместе с ним
     * ({@code ON DELETE CASCADE} в миграции 005), то есть его токены перестают обновляться,
     * а уже выданные доживают свои 10 минут.
     */
    @Transactional
    public void unregister(String clientId) {
        RegisteredClient existing = require(clientId);
        jdbc.update("DELETE FROM oauth2_registered_client WHERE id = ?", existing.getId());
    }

    /**
     * Все клиенты, по одному запросу на каждого.
     *
     * <p>{@code RegisteredClientRepository} умеет искать только по id, а разбирать колонки со
     * списками через запятую самому — хуже, чем сделать несколько запросов: клиентов в экосистеме
     * единицы, а готовый разбор Spring гарантированно совпадает с тем, как он же эти строки писал.
     */
    @Transactional(readOnly = true)
    public List<ClientSummary> list() {
        return jdbc.queryForList("SELECT client_id FROM oauth2_registered_client ORDER BY client_id", String.class)
                .stream()
                .map(clients::findByClientId)
                .filter(Objects::nonNull)
                .map(ClientSummary::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientSummary get(String clientId) {
        return ClientSummary.of(require(clientId));
    }

    private RegisteredClient build(ClientSpec spec, String secret) {
        Set<String> scopes = spec.scopes().isEmpty() ? DEFAULT_SCOPES : spec.scopes();

        RegisteredClient.Builder client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(spec.clientId())
                .clientName(spec.name())
                .tokenSettings(tokenSettings(spec.kind()));

        if (spec.kind() == ClientKind.SERVICE) {
            client.authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .clientSettings(ClientSettings.builder().build());
            // У сервисного клиента нет человека, поэтому ни openid, ни профиля: только свои права.
            spec.scopes().forEach(client::scope);
        } else {
            client.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .clientSettings(ClientSettings.builder()
                            // PKCE обязателен для всех видов с человеком: так требует OAuth 2.1.
                            .requireProofKey(true)
                            // Свои сервисы: спрашивать «разрешить доступ?» незачем.
                            .requireAuthorizationConsent(false)
                            .build());
            scopes.forEach(client::scope);
            spec.redirectUris().forEach(client::redirectUri);
            spec.postLogoutRedirectUris().forEach(client::postLogoutRedirectUri);
        }

        // Refresh-токен — только настольным и мобильным приложениям (решение 2026-10-08).
        // Браузерной вкладке его негде спрятать от XSS, серверному приложению он пока не нужен,
        // а сервисному бессмысленен: за новым access-токеном оно приходит со своим секретом.
        if (spec.kind() == ClientKind.NATIVE) {
            client.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        }

        client.clientAuthenticationMethod(spec.kind().isPublic()
                ? ClientAuthenticationMethod.NONE
                : ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        if (secret != null) {
            client.clientSecret(passwordEncoder.encode(secret));
        }

        return client.build();
    }

    /**
     * Сроки жизни токенов и ротация refresh-токена.
     *
     * <p>Умолчания Spring здесь не годятся: {@code reuseRefreshTokens} у него {@code true}
     * (на обновление возвращается <b>тот же</b> refresh-токен), а срок refresh-токена — 60 минут.
     * Нам нужно обратное: каждый обмен выдаёт новый токен, а старый перестаёт работать. Без ротации
     * украденный токен работал бы все 30 дней и кража ничем бы себя не выдала.
     */
    private static TokenSettings tokenSettings(ClientKind kind) {
        TokenSettings.Builder settings = TokenSettings.builder()
                .accessTokenTimeToLive(ACCESS_TOKEN_LIFETIME);
        if (kind == ClientKind.NATIVE) {
            settings.reuseRefreshTokens(false)
                    .refreshTokenTimeToLive(REFRESH_TOKEN_LIFETIME);
        }
        return settings.build();
    }

    private void validate(ClientSpec spec) {
        if (spec.clientId() == null || !CLIENT_ID.matcher(spec.clientId()).matches()) {
            throw new InvalidClientSpecException(
                    "clientId должен быть строчными латинскими буквами, цифрами и дефисами: planner, meet");
        }
        if (spec.name() == null || spec.name().isBlank()) {
            throw new InvalidClientSpecException("Клиенту нужно название");
        }
        spec.scopes().forEach(scope -> {
            if (!SCOPE.matcher(scope).matches()) {
                throw new InvalidClientSpecException("Недопустимое право: " + scope);
            }
        });

        if (spec.kind() == ClientKind.SERVICE) {
            if (!spec.redirectUris().isEmpty() || !spec.postLogoutRedirectUris().isEmpty()) {
                throw new InvalidClientSpecException(
                        "Сервисному клиенту адреса возврата не нужны: в его потоке нет человека");
            }
            // Умолчания openid/profile/email ему не подходят: человека нет, профиля тоже.
            if (spec.scopes().isEmpty()) {
                throw new InvalidClientSpecException("Сервисному клиенту нужно указать хотя бы одно право");
            }
            return;
        }

        if (spec.redirectUris().isEmpty()) {
            throw new InvalidClientSpecException("Нужен хотя бы один адрес возврата");
        }
        spec.redirectUris().forEach(AdminClientService::validateRedirectUri);
        spec.postLogoutRedirectUris().forEach(AdminClientService::validateRedirectUri);
    }

    /**
     * Адрес должен быть абсолютным и без фрагмента: по фрагменту сравнение адресов возврата не
     * работает — браузер его серверу не отправляет.
     */
    private static void validateRedirectUri(String uri) {
        try {
            URI parsed = new URI(uri);
            if (!parsed.isAbsolute() || parsed.getFragment() != null) {
                throw new InvalidClientSpecException(
                        "Адрес возврата должен быть абсолютным и без фрагмента: " + uri);
            }
        } catch (java.net.URISyntaxException e) {
            throw new InvalidClientSpecException("Адрес возврата не разбирается: " + uri);
        }
    }

    private RegisteredClient require(String clientId) {
        RegisteredClient client = clients.findByClientId(clientId);
        if (client == null) {
            throw new ClientNotFoundException(clientId);
        }
        return client;
    }

    private static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
