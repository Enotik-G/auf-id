package com.example.planner.authserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Spring Authorization Server: выдаёт сервисам экосистемы токены по стандарту OAuth 2.1 / OpenID Connect.
 *
 * <p>Своя цепочка фильтров, раньше основной ({@code @Order(1)}): она отвечает только за адреса сервера
 * авторизации ({@code /oauth2/*}, {@code /.well-known/*}, {@code /userinfo} …), всё остальное —
 * по-прежнему {@link com.example.planner.SecurityConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .oauth2AuthorizationServer(authorizationServer -> {
                    // Эта цепочка — только для адресов сервера авторизации.
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());
                    // OpenID Connect: id_token, /userinfo, /.well-known/openid-configuration.
                    authorizationServer.oidc(Customizer.withDefaults());
                })
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                // /userinfo принимает access token в заголовке Authorization — проверяем его как JWT.
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                // Не вошедшего человека (браузер) отправляем на нашу страницу входа,
                // а после входа Spring вернёт его обратно в процесс авторизации.
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }
}
