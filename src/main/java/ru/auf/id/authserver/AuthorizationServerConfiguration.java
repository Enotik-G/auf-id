package ru.auf.id.authserver;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Authorization Server: выдаёт сервисам экосистемы токены по стандарту OAuth 2.1 / OpenID Connect.
 *
 * <p>Своя цепочка фильтров, раньше основной ({@code @Order(1)}): она отвечает только за адреса сервера
 * авторизации ({@code /oauth2/*}, {@code /.well-known/*}, {@code /userinfo} …), всё остальное —
 * по-прежнему {@link ru.auf.id.SecurityConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
public class AuthorizationServerConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            @Value("${auth.cors.allowed-origins:}") List<String> allowedOrigins) throws Exception {
        http
                .oauth2AuthorizationServer(authorizationServer -> {
                    // Эта цепочка — только для адресов сервера авторизации.
                    http.securityMatcher(authorizationServer.getEndpointsMatcher());
                    // OpenID Connect: id_token, /userinfo, /.well-known/openid-configuration.
                    authorizationServer.oidc(Customizer.withDefaults());
                })
                // Браузерные клиенты (SPA) с других адресов обменивают код на токен через fetch —
                // без CORS-заголовков браузер не отдаст им ответ.
                .cors(cors -> cors.configurationSource(corsConfigurationSource(allowedOrigins)))
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

    /**
     * Какие чужие сайты (origin — схема, домен и порт, например {@code https://planner.example.ru})
     * могут читать ответы сервера авторизации из JavaScript.
     *
     * <p>Намеренно не бин: увидев в контексте единственный {@link CorsConfigurationSource}, Spring Security
     * сам включает CORS во <b>всех</b> цепочках фильтров — в том числе на странице входа и в админке,
     * где он не нужен. Здесь источник подключён только к цепочке сервера авторизации.
     *
     * <p>Список пуст — CORS выключен: без настроек браузер не пустит ни один чужой сайт.
     */
    static CorsConfigurationSource corsConfigurationSource(List<String> allowedOrigins) {
        List<String> origins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.contains("*")) {
            // «*» пустил бы любой сайт в интернете — сервисы экосистемы надо перечислять явно.
            throw new IllegalArgumentException("auth.cors.allowed-origins: «*» запрещён, перечислите адреса явно");
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (origins.isEmpty()) {
            // Ни одного правила → для любого запроса настроек CORS нет, заголовки не добавляются.
            return source;
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST"));
        // Authorization — access token для /userinfo; Content-Type — тело запроса на /oauth2/token.
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Cookie этим ручкам не нужны: клиент предъявляет код, PKCE и токены явно, а не сессией.
        configuration.setAllowCredentials(false);
        // Сколько браузер может помнить ответ на предварительный запрос (preflight) и не повторять его.
        configuration.setMaxAge(Duration.ofHours(1));
        // Цепочка и так видит только адреса сервера авторизации, поэтому правило — на все пути.
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
