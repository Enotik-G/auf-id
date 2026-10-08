package ru.auf.id.authserver;

import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

/**
 * Выдаёт refresh-токены, в том числе <b>публичным</b> клиентам — настольным приложениям вроде нашего
 * лаунчера.
 *
 * <p><b>Зачем свой класс.</b> Штатный {@code OAuth2RefreshTokenGenerator} публичному клиенту
 * refresh-токен не выдаёт: внутри у него проверка {@code isPublicClientForAuthorizationCodeGrant},
 * и для клиента с {@code ClientAuthenticationMethod.NONE} в потоке с кодом он возвращает
 * {@code null}. Проверка приватная и настройкой не отключается, поэтому заменить можно только
 * генератор целиком.
 *
 * <p><b>Почему это не нарушение стандарта.</b> Запрет в Spring — осторожное умолчание, а не
 * требование. OAuth 2.0 Security Best Current Practice (§4.13.2) прямо разрешает выдавать
 * refresh-токены публичным клиентам при условии, что они либо привязаны к отправителю
 * (sender-constrained), либо <b>ротируются</b>. У нас включена ротация
 * ({@code reuseRefreshTokens(false)} в {@code AdminClientService}), то есть условие выполнено.
 * RFC 8252 («OAuth 2.0 для нативных приложений») на этом и построен: настольному приложению
 * refresh-токен нужен, а секрет клиента ему спрятать негде.
 *
 * <p><b>Своей криптографии здесь нет.</b> Значение токена делает {@code Base64StringKeyGenerator}
 * из Spring Security — тот же класс и те же 96 байт случайных данных, что у штатного генератора.
 * Отличие ровно одно: убрана проверка на публичного клиента.
 *
 * <p>Из-за этого ответственность за «кому можно» переезжает в
 * {@link ru.auf.id.admin.AdminClientService}: grant {@code refresh_token} получает только вид
 * {@code NATIVE}, а без этого grant Spring к генератору вообще не обратится.
 */
public class NativeClientRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {

    /** 96 байт случайных данных в base64url — как у штатного генератора Spring. */
    private final StringKeyGenerator tokenValueGenerator =
            new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96);

    private final Clock clock;

    public NativeClientRefreshTokenGenerator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        // Генератор в цепочке один на все виды токенов: чужие просто пропускаем,
        // их обработает следующий в DelegatingOAuth2TokenGenerator.
        if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
            return null;
        }
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(
                context.getRegisteredClient().getTokenSettings().getRefreshTokenTimeToLive());
        return new OAuth2RefreshToken(tokenValueGenerator.generateKey(), issuedAt, expiresAt);
    }
}
