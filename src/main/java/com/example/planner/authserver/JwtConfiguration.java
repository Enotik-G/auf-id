package com.example.planner.authserver;

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

/** Чем подписываются токены. */
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
     * Алгоритм подписи — ES256 для всех токенов. По умолчанию access token подписывается RS256
     * (id token — по настройке клиента), а RSA-ключа у нас нет.
     */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> es256TokenCustomizer() {
        return context -> context.getJwsHeader().algorithm(SignatureAlgorithm.ES256);
    }
}
