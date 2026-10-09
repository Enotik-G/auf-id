package ru.auf.id.login;

import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import ru.auf.id.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Сверяет вошедшего человека с базой <b>на каждом запросе</b>, а не только в момент ввода пароля.
 *
 * <p>Без этого сессия жила своей жизнью: Spring Security запоминает пользователя и его роли при
 * входе и дальше в БД не смотрит. Заблокированный продолжал ходить на {@code /oauth2/authorize} и
 * получать коды, а заблокированный или разжалованный администратор — пользоваться админкой
 * (в том числе разблокировать сам себя) до конца сессии, которая при активной работе не кончается.
 *
 * <p>Что делает фильтр:
 * <ul>
 *   <li>пользователя больше нет или он не {@code ACTIVE} — сессия закрывается, и запрос идёт дальше
 *       как от не вошедшего (браузер попадёт на страницу входа, API получит 401);</li>
 *   <li>роли в БД изменились — в сессию кладутся новые, и правило {@code hasRole("ADMIN")} видит их
 *       уже на этом запросе.</li>
 * </ul>
 *
 * <p>Цена — один запрос к БД на каждый запрос вошедшего человека. Зато правда хранится в одном
 * месте (в БД), и это работает при любом числе копий приложения — в отличие от «найти и закрыть
 * чужие сессии», которое пришлось бы переделывать с приходом Spring Session.
 *
 * <p>Касается только сессий от формы входа ({@link UsernamePasswordAuthenticationToken}). Запросы с
 * access token (например, {@code /userinfo}) идут без сессии: такой токен живёт 10 минут, а новый
 * заблокированному не выдадут (см. {@code UserClaims.addTo}).
 *
 * <p>Не бин ({@code @Component}) намеренно: иначе Spring Boot поставил бы его ещё и общим фильтром
 * сервлета, вне цепочек Spring Security. Его создают обе цепочки сами.
 */
@RequiredArgsConstructor
public class SessionUserRevalidationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof UsernamePasswordAuthenticationToken login) {
            userIdOf(login).ifPresent(userId -> {
                Optional<User> user = userRepository.findById(userId)
                        .filter(found -> found.getStatus() == UserStatus.ACTIVE);
                if (user.isEmpty()) {
                    endSession(request);
                } else if (rolesChanged(login, user.get())) {
                    replaceRoles(login, user.get(), request, response);
                }
            });
        }
        chain.doFilter(request, response);
    }

    /**
     * Наша форма входа всегда называет вошедшего его id ({@link AccountUserDetailsService}), поэтому
     * сессия с другим именем создана не ею — в рабочем приложении таких нет. Встречаются они только в
     * тестах контроллеров ({@code @WithMockUser} с именем {@code user}), где проверяют правила
     * доступа, а не эту перепроверку; её проверяет {@code SessionUserRevalidationTest}.
     */
    private static Optional<UUID> userIdOf(Authentication login) {
        try {
            return Optional.of(UUID.fromString(login.getName()));
        } catch (IllegalArgumentException notAUserId) {
            return Optional.empty();
        }
    }

    /** Как выход, только без перенаправления: остаток запроса обрабатывается как от не вошедшего. */
    private static void endSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private static boolean rolesChanged(Authentication login, User user) {
        return !new HashSet<>(roleAuthoritiesOf(login)).equals(new HashSet<>(AccountUserDetailsService.roleAuthorities(user)));
    }

    /**
     * Меняет в сессии только роли. Остальные полномочия сохраняются как есть — среди них
     * {@code FactorGrantedAuthority}, отметка о том, что человек вошёл паролем и когда: по ней Spring
     * ставит {@code auth_time} в id_token. Потеряй её — клиенты увидели бы неверное время входа.
     */
    private void replaceRoles(UsernamePasswordAuthenticationToken login, User user,
                              HttpServletRequest request, HttpServletResponse response) {
        List<GrantedAuthority> authorities = new ArrayList<>(login.getAuthorities());
        authorities.removeIf(SessionUserRevalidationFilter::isRole);
        authorities.addAll(AccountUserDetailsService.roleAuthorities(user));

        UsernamePasswordAuthenticationToken updated =
                UsernamePasswordAuthenticationToken.authenticated(login.getPrincipal(), null, authorities);
        updated.setDetails(login.getDetails());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(updated);
        SecurityContextHolder.setContext(context);
        // Без явного сохранения новые роли жили бы только до конца этого запроса.
        contextRepository.saveContext(context, request, response);
    }

    private static List<GrantedAuthority> roleAuthoritiesOf(Authentication login) {
        return login.getAuthorities().stream()
                .filter(SessionUserRevalidationFilter::isRole)
                .map(authority -> (GrantedAuthority) authority)
                .toList();
    }

    private static boolean isRole(GrantedAuthority authority) {
        return authority.getAuthority() != null && authority.getAuthority().startsWith(AccountUserDetailsService.ROLE_PREFIX);
    }
}
