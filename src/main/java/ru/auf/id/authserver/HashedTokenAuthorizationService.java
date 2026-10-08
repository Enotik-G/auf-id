package ru.auf.id.authserver;

import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Хранилище выданных авторизаций, которое кладёт в БД не сами коды и токены, а их хеши SHA-256.
 *
 * <p>Зачем: без этого в {@code oauth2_authorization} лежат живые access-токены и коды. Утёк дамп
 * или бэкап — и у чужих людей на руках рабочий доступ. С хешем из дампа войти нельзя: по хешу не
 * восстановить токен, а «предъявить хеш вместо токена» не выйдет — предъявленное значение мы сами
 * хешируем ещё раз.
 *
 * <p><b>Персональные данные это не закрывает.</b> Рядом со значением Spring хранит метаданные
 * токена, а в них — его claims обычным JSON: колонки {@code access_token_metadata} и
 * {@code oidc_id_token_metadata} содержат {@code name} и {@code email} открытым текстом. Выбросить
 * их нельзя: из них {@code /userinfo} отдаёт профиль. Закрывать это надо отдельно — либо собирать
 * {@code /userinfo} из базы пользователей вместо сохранённых claims, либо шифровать колонки
 * (шифрование в документе — фаза 2, нужен Vault).
 *
 * <p>Как работает. Это обёртка: всю работу с таблицей делает стандартный
 * {@code JdbcOAuth2AuthorizationService}, а мы только подменяем значения на входе и на выходе.
 * <ul>
 *   <li>{@link #save} — перед записью заменяет значения всех токенов на {@code sha256:<hex>};</li>
 *   <li>{@link #findByToken} — ищет по хешу предъявленного значения, а в найденной авторизации
 *       возвращает на место <b>сырое значение, которое нам только что предъявили</b>. Это важно:
 *       отзыв токена и introspection после поиска сравнивают значения строками
 *       ({@code authorization.getToken(значение)}), и с хешем внутри они бы ничего не нашли.</li>
 * </ul>
 *
 * <p>Остальным токенам найденной авторизации хеш оставляем: Spring берёт их по классу
 * ({@code getAccessToken()}, {@code getToken(OidcIdToken.class)}) и смотрит только сроки, «отозван ли»
 * и claims — сами значения ему не нужны.
 *
 * <p>Префикс {@code sha256:} нужен, чтобы не захешировать хеш повторно: авторизацию часто читают
 * и сохраняют снова (например, при обмене кода на токены), и часть значений в ней уже хеши.
 * Настоящий токен с такого префикса начаться не может — коды у Spring в base64url, JWT начинается с {@code eyJ}.
 *
 * <p>{@code state} не хешируем: это не доступ, а метка незавершённого входа для экрана согласия,
 * и хранится она в отдельной колонке, а не среди токенов.
 */
public class HashedTokenAuthorizationService implements OAuth2AuthorizationService {

    private static final String HASH_PREFIX = "sha256:";

    private static final OAuth2TokenType STATE = new OAuth2TokenType(OAuth2ParameterNames.STATE);

    /** Все виды токенов, которые Spring может положить в авторизацию. */
    private static final List<Class<? extends OAuth2Token>> TOKEN_CLASSES = List.of(
            OAuth2AuthorizationCode.class,
            OAuth2AccessToken.class,
            OAuth2RefreshToken.class,
            OidcIdToken.class,
            OAuth2UserCode.class,
            OAuth2DeviceCode.class);

    private final OAuth2AuthorizationService delegate;
    private final RefreshTokenReuseDetector reuseDetector;

    public HashedTokenAuthorizationService(OAuth2AuthorizationService delegate,
                                           RefreshTokenReuseDetector reuseDetector) {
        this.delegate = delegate;
        this.reuseDetector = reuseDetector;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        rememberRotatedRefreshToken(authorization);
        delegate.save(withHashedTokens(authorization));
    }

    /**
     * Перед записью смотрит, не вытесняется ли прежний refresh-токен новым, и если да — отдаёт его
     * хеш в реестр погашенных ({@link RefreshTokenReuseDetector}).
     *
     * <p>Это единственное место, где прежнее значение ещё можно увидеть: сохранение его затрёт, и
     * дальше повторное предъявление украденного токена было бы не отличить от опечатки.
     *
     * <p>Лишний запрос в БД на каждое сохранение — осознанная цена. Сохранения редки: выдача кода,
     * обмен кода, обновление токена, отзыв. На пути проверки токена сервисами этого нет вовсе —
     * они в базу не ходят.
     */
    private void rememberRotatedRefreshToken(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2RefreshToken> incoming = authorization.getRefreshToken();
        if (incoming == null) {
            // Авторизация без refresh-токена: вытеснять нечего (например, только что выдан код).
            return;
        }
        OAuth2Authorization stored = delegate.findById(authorization.getId());
        if (stored == null || stored.getRefreshToken() == null) {
            // Первая выдача refresh-токена этой авторизации — это не ротация.
            return;
        }

        String previousValue = stored.getRefreshToken().getToken().getTokenValue();
        if (previousValue.equals(hashed(incoming.getToken().getTokenValue()))) {
            // Тот же токен сохраняют снова (например, при отзыве access-токена) — ротации не было.
            return;
        }
        reuseDetector.remember(
                previousValue, authorization.getPrincipalName(), authorization.getRegisteredClientId());
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        // Удаление идёт по id строки, значения токенов не участвуют.
        delegate.remove(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        if (STATE.equals(tokenType)) {
            return delegate.findByToken(token, tokenType);
        }
        String hashedToken = hash(token);
        OAuth2Authorization found = delegate.findByToken(hashedToken, tokenType);
        if (found != null) {
            return withRawValue(found, token);
        }

        // Токена в базе нет. Для refresh-токена это может означать не опечатку, а кражу: его уже
        // погасили ротацией, а предъявляют снова. Проверяем только здесь, в самом потоке обновления.
        // Отзыв и introspection (tokenType == null) сюда не включены намеренно: честный клиент
        // вправе попросить отозвать токен, который уже не действует, и выбрасывать за это человека
        // из приложения было бы неверно.
        if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
            reuseDetector.revokeIfReused(hashedToken);
        }
        if (tokenType == null) {
            // «Любой тип» (так ищут отзыв и introspection) — это может быть и state, он лежит как есть.
            return delegate.findByToken(token, STATE);
        }
        return null;
    }

    /** Копия авторизации, где значения всех токенов заменены на хеши. Метаданные (сроки, claims, «отозван») те же. */
    private static OAuth2Authorization withHashedTokens(OAuth2Authorization authorization) {
        OAuth2Authorization.Builder builder = OAuth2Authorization.from(authorization);
        for (Class<? extends OAuth2Token> tokenClass : TOKEN_CLASSES) {
            OAuth2Authorization.Token<? extends OAuth2Token> stored = authorization.getToken(tokenClass);
            if (stored != null && !isHash(stored.getToken().getTokenValue())) {
                builder.token(withValue(stored.getToken(), hash(stored.getToken().getTokenValue())));
            }
        }
        return builder.build();
    }

    /** Копия найденной авторизации, где у предъявленного токена вместо хеша снова его настоящее значение. */
    private static OAuth2Authorization withRawValue(OAuth2Authorization authorization, String rawValue) {
        String hashedValue = hash(rawValue);
        OAuth2Authorization.Builder builder = OAuth2Authorization.from(authorization);
        for (Class<? extends OAuth2Token> tokenClass : TOKEN_CLASSES) {
            OAuth2Authorization.Token<? extends OAuth2Token> stored = authorization.getToken(tokenClass);
            if (stored != null && stored.getToken().getTokenValue().equals(hashedValue)) {
                builder.token(withValue(stored.getToken(), rawValue));
            }
        }
        return builder.build();
    }

    /**
     * Тот же токен (тот же класс, сроки, scopes, claims), но с другим значением.
     *
     * <p>Класс должен остаться прежним: авторизация хранит токены по классу, и {@code builder.token(...)}
     * заменяет токен того же класса, сохраняя его метаданные.
     */
    private static OAuth2Token withValue(OAuth2Token token, String value) {
        return switch (token) {
            case OAuth2AuthorizationCode code -> new OAuth2AuthorizationCode(value, code.getIssuedAt(), code.getExpiresAt());
            case OAuth2AccessToken access -> new OAuth2AccessToken(
                    access.getTokenType(), value, access.getIssuedAt(), access.getExpiresAt(), access.getScopes());
            case OAuth2RefreshToken refresh -> new OAuth2RefreshToken(value, refresh.getIssuedAt(), refresh.getExpiresAt());
            case OidcIdToken idToken -> new OidcIdToken(value, idToken.getIssuedAt(), idToken.getExpiresAt(), idToken.getClaims());
            case OAuth2UserCode userCode -> new OAuth2UserCode(value, userCode.getIssuedAt(), userCode.getExpiresAt());
            case OAuth2DeviceCode deviceCode -> new OAuth2DeviceCode(value, deviceCode.getIssuedAt(), deviceCode.getExpiresAt());
            // Лучше упасть, чем молча записать неизвестный токен открытым текстом.
            default -> throw new IllegalStateException("Неизвестный вид токена: " + token.getClass().getName());
        };
    }

    private static boolean isHash(String value) {
        return value.startsWith(HASH_PREFIX);
    }

    /** Значение в том виде, в каком оно лежит в БД: уже хеш — как есть, сырое — хешируем. */
    private static String hashed(String value) {
        return isHash(value) ? value : hash(value);
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HASH_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 обязана быть в любой Java — сюда не попадём.
            throw new IllegalStateException(e);
        }
    }
}
