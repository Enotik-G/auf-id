package ru.auf.id.authserver;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Clock;

/** Чем подписываются токены и что в них лежит. */
@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {

    /**
     * Наш постоянный ключ. Пока такого бина нет, Spring Boot генерирует RSA-ключ заново при каждом запуске —
     * и после перезапуска все выданные токены перестают проверяться.
     */
    @Bean
    JWKSource<SecurityContext> jwkSource(@Value("${auth.jwt.signing-key}") String signingKey) {
        return new ImmutableJWKSet<>(new JWKSet(EcSigningKey.parse(signingKey)));
    }

    /**
     * Что и как попадает в каждый выпускаемый JWT. Spring Authorization Server принимает
     * <b>ровно один</b> бин этого типа, поэтому здесь собрано всё, что мы дописываем в токен:
     * алгоритм подписи и личные поля пользователя.
     *
     * <p>Алгоритм — ES256 для всех токенов. По умолчанию access token подписывается RS256
     * (id token — по настройке клиента), а RSA-ключа у нас нет.
     */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(UserClaims userClaims) {
        return context -> {
            context.getJwsHeader().algorithm(SignatureAlgorithm.ES256);
            userClaims.addTo(context);
        };
    }

    /**
     * Чем выпускаются токены. Такую же цепочку Spring Authorization Server собирает сам, если этого
     * бина нет — здесь она повторена, чтобы подменить в ней <b>один</b> генератор.
     *
     * <p>Причина: штатный генератор refresh-токенов не выдаёт их публичным клиентам, а наш лаунчер —
     * публичный клиент, которому refresh-токен необходим. Подробно — в
     * {@link NativeClientRefreshTokenGenerator}.
     *
     * <p>Состав цепочки — порядок важен, {@code DelegatingOAuth2TokenGenerator} берёт первый
     * генератор, вернувший не {@code null}:
     * <ul>
     *   <li>{@link JwtGenerator} — самодостаточные токены (JWT): access token и id_token.
     *       Ему же передаётся наш {@link #tokenCustomizer}: иначе подпись ES256 и личные поля
     *       в токен не попадут, ведь customizer Spring подключает к генератору, которого больше нет;</li>
     *   <li>{@link OAuth2AccessTokenGenerator} — непрозрачные access-токены, для клиентов с форматом
     *       {@code reference}. Таких у нас нет, но генератор оставлен, чтобы цепочка совпадала
     *       со штатной: иначе такой клиент тихо перестал бы работать;</li>
     *   <li>{@link NativeClientRefreshTokenGenerator} — refresh-токены.</li>
     * </ul>
     */
    @Bean
    OAuth2TokenGenerator<?> tokenGenerator(JWKSource<SecurityContext> jwkSource,
                                           OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer,
                                           Clock clock) {
        JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        jwtGenerator.setJwtCustomizer(tokenCustomizer);
        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator,
                new OAuth2AccessTokenGenerator(),
                new NativeClientRefreshTokenGenerator(clock));
    }
}
