package ru.auf.id.authserver;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

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
}
