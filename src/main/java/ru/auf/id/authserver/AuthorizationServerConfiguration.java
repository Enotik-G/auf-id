package ru.auf.id.authserver;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationServerMetadata;
import org.springframework.security.oauth2.server.authorization.oidc.OidcProviderConfiguration;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
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
            AuthorizationServerSettings authorizationServerSettings,
            @Value("${auth.cors.allowed-origins:}") List<String> allowedOrigins) throws Exception {
        http
                .oauth2AuthorizationServer(authorizationServer -> {
                    // Эта цепочка — только для адресов сервера авторизации.
                    // Плюс предварительные запросы браузера (OPTIONS): ручка токена принимает только POST,
                    // и без этого preflight попал бы в основную цепочку, где CORS нет.
                    http.securityMatcher(new OrRequestMatcher(
                            authorizationServer.getEndpointsMatcher(),
                            preflightToAuthorizationServer()));
                    // Метаданные OAuth 2.0: /.well-known/oauth-authorization-server.
                    authorizationServer.authorizationServerMetadataEndpoint(endpoint ->
                            endpoint.authorizationServerMetadataCustomizer(
                                    AuthorizationServerConfiguration::announcePublicClients));
                    // OpenID Connect: id_token, /userinfo, /.well-known/openid-configuration.
                    authorizationServer.oidc(oidc -> oidc.providerConfigurationEndpoint(endpoint ->
                            endpoint.providerConfigurationCustomizer(configuration -> {
                                announceEs256(configuration);
                                announcePublicClients(configuration);
                            })));
                })
                // Браузерные клиенты (SPA) с других адресов обменивают код на токен через fetch —
                // без CORS-заголовков браузер не отдаст им ответ.
                .cors(cors -> cors.configurationSource(corsConfigurationSource(allowedOrigins)))
                .authorizeHttpRequests(requests -> requests
                        // Молчаливое продление входа (prompt=none) должно дойти до Spring, а не
                        // упереться в правило ниже: см. silentAuthorizationRequest.
                        .requestMatchers(silentAuthorizationRequest(authorizationServerSettings)).permitAll()
                        .anyRequest().authenticated())
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
     * Сообщает клиентам в {@code /.well-known/openid-configuration}, что id_token подписан ES256.
     *
     * <p>Spring по умолчанию пишет туда RS256, а мы подписываем все токены ключом EC
     * ({@link JwtConfiguration}). Строгий клиент сверяет алгоритм токена с этим списком
     * и отверг бы наш id_token.
     */
    private static void announceEs256(OidcProviderConfiguration.Builder configuration) {
        configuration.idTokenSigningAlgorithms(algorithms -> {
            algorithms.clear();
            algorithms.add(SignatureAlgorithm.ES256.getName());
        });
    }

    /**
     * Запрос «продли вход, если человек уже вошёл, и не показывай форму» — {@code prompt=none}
     * из OpenID Connect Core (3.1.2.1). Так браузерное приложение (планировщик) обновляет
     * истёкший access-токен, потому что refresh-токена у него нет.
     *
     * <p><b>Зачем отдельное правило.</b> По стандарту не вошедшему человеку сервер обязан вернуть
     * на {@code redirect_uri} ошибку {@code login_required} — приложение по ней понимает, что пора
     * отправлять человека входить по-настоящему. Spring это умеет
     * ({@code OAuth2AuthorizationCodeRequestAuthenticationProvider}), но до него запрос не доходил:
     * {@code anyRequest().authenticated()} отвечал редиректом на страницу входа. Вместо ошибки
     * приложение получало HTML-страницу и разобрать её не могло.
     *
     * <p><b>Почему это не дыра.</b> {@code permitAll} здесь не выдаёт ничего: запрос доходит до
     * Spring, тот проверяет клиента, {@code redirect_uri} и PKCE, видит, что человек не вошёл, и
     * отдаёт ошибку. Код авторизации выдаётся только вошедшему — это решает Spring, а не это
     * правило. А вошедший и так проходил по {@code authenticated()}.
     */
    private static RequestMatcher silentAuthorizationRequest(AuthorizationServerSettings settings) {
        RequestMatcher authorizationEndpoint = PathPatternRequestMatcher.withDefaults()
                .matcher(settings.getAuthorizationEndpoint());
        return request -> authorizationEndpoint.matches(request) && requestsNoPrompt(request);
    }

    /** {@code prompt} — список через пробел, нас интересует значение {@code none} в нём. */
    private static boolean requestsNoPrompt(HttpServletRequest request) {
        String prompt = request.getParameter("prompt");
        return prompt != null && Set.of(prompt.trim().split("\\s+")).contains("none");
    }

    /**
     * Сообщает в метаданных, что сервер принимает клиентов <b>без секрета</b> — способ
     * аутентификации {@code none}.
     *
     * <p>Spring перечисляет только способы с секретом или сертификатом, и {@code none} в список не
     * попадает. При этом публичные клиенты у нас есть — вид {@code BROWSER} (приложение в браузерной
     * вкладке) ходит на {@code /oauth2/token} вообще без секрета, его защищает PKCE.
     *
     * <p>Без этой строчки discovery описывает сервер неверно: строгая библиотека OIDC-клиента
     * прочитает список, не найдёт там своего способа и либо откажется идти за токеном, либо выберет
     * способ, для которого у неё нет секрета. Ошибка того же рода, что подпись {@code RS256}
     * в {@link #announceEs256} — сервер умеет одно, а рассказывает о себе другое.
     *
     * <p>Правится в двух местах: метаданные отдают <b>две</b> ручки, OpenID Connect и OAuth 2.0,
     * и список в них свой у каждой.
     */
    private static void announcePublicClients(OidcProviderConfiguration.Builder configuration) {
        configuration.tokenEndpointAuthenticationMethod(ClientAuthenticationMethod.NONE.getValue());
    }

    /**
     * То же для метаданных OAuth 2.0 — см. {@link #announcePublicClients(OidcProviderConfiguration.Builder)}.
     *
     * <p>Двух перегрузок не избежать: общий родитель обоих построителей
     * ({@code AbstractOAuth2AuthorizationServerMetadata.AbstractBuilder}) объявлен {@code protected},
     * и взять его параметром снаружи нельзя.
     */
    private static void announcePublicClients(OAuth2AuthorizationServerMetadata.Builder metadata) {
        metadata.tokenEndpointAuthenticationMethod(ClientAuthenticationMethod.NONE.getValue());
    }

    /** Предварительный запрос (OPTIONS) к адресам сервера авторизации, которые читают из JavaScript. */
    private static RequestMatcher preflightToAuthorizationServer() {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                paths.matcher(HttpMethod.OPTIONS, "/oauth2/**"),
                paths.matcher(HttpMethod.OPTIONS, "/userinfo"),
                paths.matcher(HttpMethod.OPTIONS, "/.well-known/**"));
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
