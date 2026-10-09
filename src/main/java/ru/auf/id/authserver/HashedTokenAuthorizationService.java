package ru.auf.id.authserver;

import ru.auf.id.Sha256;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Хранилище выданных авторизаций, которое кладёт в БД не сами коды и токены, а их хеши SHA-256.
 *
 * <p>Зачем: без этого в {@code oauth2_authorization} лежат живые access-токены и коды. Утёк дамп
 * или бэкап — и у чужих людей на руках рабочий доступ. С хешем из дампа войти нельзя: по хешу не
 * восстановить токен, а «предъявить хеш вместо токена» не выйдет — предъявленное значение мы сами
 * хешируем ещё раз.
 *
 * <p><b>Личные данные тоже убираются</b> (шаг 24). Рядом со значением Spring хранит метаданные
 * токена, а в них — его claims обычным JSON: в колонках {@code access_token_metadata} и
 * {@code oidc_id_token_metadata} лежали {@code name} и {@code email} открытым текстом, и
 * хеширование значений их не закрывало. Теперь перед записью они вырезаются
 * ({@link #removePersonalClaims}); служебные claims остаются, по ним работают выход и introspection.
 * Профиль для {@code /userinfo} собирается из таблицы {@code users} ({@code UserClaims.userInfo}).
 *
 * <p>Как работает. Это обёртка: всю работу с таблицей делает стандартный
 * {@code JdbcOAuth2AuthorizationService}, а мы только подменяем значения на входе и на выходе.
 * <ul>
 *   <li>{@link #save} — перед записью заменяет значения всех токенов на {@code sha256:<hex>}
 *       и вырезает из их claims личные поля;</li>
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

    /** Claims, которые не должны лежать в БД: это персональные данные, а не работа сервера (шаг 24). */
    private static final Set<String> PERSONAL_CLAIMS =
            Set.of(StandardClaimNames.NAME, StandardClaimNames.EMAIL);

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
        delegate.save(forStorage(authorization));
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
        if (previousValue.equals(asStored(incoming.getToken().getTokenValue()))) {
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
        String hashedToken = hashOf(token);
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

    /**
     * Копия авторизации, готовая к записи: значения токенов заменены на хеши, а из их claims убраны
     * ФИО и почта. Сроки, «отозван» и служебные claims — те же.
     */
    private static OAuth2Authorization forStorage(OAuth2Authorization authorization) {
        OAuth2Authorization.Builder builder = OAuth2Authorization.from(authorization);
        for (Class<? extends OAuth2Token> tokenClass : TOKEN_CLASSES) {
            OAuth2Authorization.Token<? extends OAuth2Token> stored = authorization.getToken(tokenClass);
            if (stored == null) {
                continue;
            }
            OAuth2Token token = stored.getToken();
            String value = token.getTokenValue();
            // Уже захешированное не хешируем повторно, но claims проверяем у всех токенов:
            // «значение сырое» и «claims свежие» — разные условия, и совпадают они не всегда.
            OAuth2Token storedToken = isHash(value) ? token : withValue(token, hashOf(value));
            builder.token(storedToken, HashedTokenAuthorizationService::removePersonalClaims);
        }
        return builder.build();
    }

    /**
     * Убирает ФИО и почту из claims токена перед записью в БД (шаг 24, зачем — в описании класса).
     *
     * <p><b>Убираем только личное.</b> Служебные claims ({@code sub}, {@code aud}, {@code auth_time},
     * {@code sid} и прочие) остаются: по ним работает выход ({@code /connect/logout} сверяет
     * {@code aud} и {@code sid}) и introspection. Проверено по исходникам Spring AS 7.1.1 — читают
     * claims только эти двое, остальные провайдеры их лишь перезаписывают, генерируя токен заново.
     *
     * <p>Профиль в {@code /userinfo} от этого не страдает: он собирается из таблицы
     * {@code users} ({@code UserClaims.userInfo}), а не из сохранённых claims.
     *
     * <p>Новая карта оборачивается в {@code unmodifiableMap} намеренно — это <b>тот же класс</b>,
     * который Spring здесь и хранил. Подменить его на {@code HashMap} значило бы проверять, умеет ли
     * Jackson восстанавливать новый тип при чтении авторизации обратно; на этом проект уже
     * спотыкался (см. {@code UserClaims.roleNames}).
     */
    private static void removePersonalClaims(Map<String, Object> metadata) {
        Object claims = metadata.get(OAuth2Authorization.Token.CLAIMS_METADATA_NAME);
        if (!(claims instanceof Map<?, ?> storedClaims)) {
            return;
        }

        Map<String, Object> kept = new HashMap<>();
        storedClaims.forEach((name, value) -> kept.put(String.valueOf(name), value));
        if (!kept.keySet().removeAll(PERSONAL_CLAIMS)) {
            // Личного и не было — не трогаем, чтобы не менять класс карты без нужды.
            return;
        }
        metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, Collections.unmodifiableMap(kept));
    }

    /** Копия найденной авторизации, где у предъявленного токена вместо хеша снова его настоящее значение. */
    private static OAuth2Authorization withRawValue(OAuth2Authorization authorization, String rawValue) {
        String hashedValue = hashOf(rawValue);
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
    private static String asStored(String value) {
        return isHash(value) ? value : hashOf(value);
    }

    /** Хеш с пометкой {@value #HASH_PREFIX} — по ней хеш отличается от сырого значения. */
    static String hashOf(String value) {
        return HASH_PREFIX + Sha256.hex(value);
    }
}
