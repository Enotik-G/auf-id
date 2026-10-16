package ru.auf.id.account;

import ru.auf.id.user.UserNotFoundException;
import ru.auf.id.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * API «моя учётная запись» для страниц {@code /account} веб-интерфейса AUF ID.
 *
 * <p>Входит по cookie сессии, как и админка: id учётки берётся из сессии, а не из адреса. Поэтому
 * посмотреть чужую учётку здесь нельзя в принципе — в запросе просто негде указать чужой id.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Моя учётная запись", description = "Профиль и пароль вошедшего пользователя. Доступно любому вошедшему.")
public class MeController {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final Clock clock;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    @GetMapping
    @Operation(summary = "Мой профиль")
    @ApiResponse(responseCode = "401", description = "Не выполнен вход", content = @Content)
    public MeResponse me(Authentication authentication) {
        // Имя вошедшего — его id (см. AccountUserDetailsService).
        UUID userId = UUID.fromString(authentication.getName());
        return MeResponse.of(userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId)));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Сменить свой пароль",
            description = """
                    Нужен текущий пароль. После смены отзываются все выданные токены (лаунчер, \
                    планировщик и т.п. попросят войти заново) и закрываются другие открытые сессии. \
                    Эта сессия остаётся.""")
    @ApiResponse(responseCode = "400",
            description = "`reason`: `WRONG_CURRENT_PASSWORD`, `WRONG_LENGTH` (нужно 12–128 символов) "
                    + "или `COMMON` (пароль из списка утёкших)")
    @ApiResponse(responseCode = "401", description = "Не выполнен вход", content = @Content)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body, Authentication authentication,
                               HttpServletRequest request, HttpServletResponse response) {
        UUID userId = UUID.fromString(authentication.getName());
        passwordService.changePassword(userId, body.currentPassword(), body.newPassword());
        keepThisSession((UsernamePasswordAuthenticationToken) authentication, request, response);
    }

    /**
     * После смены пароля фильтр сессий закрывает все сессии, открытые раньше неё, — в том числе эту.
     * Человек только что доказал, что знает пароль, поэтому его сессии ставится новая отметка входа
     * паролем ({@link FactorGrantedAuthority}) — не раньше времени смены.
     *
     * <p>Заодно меняется id сессии: если старый кто-то подсмотрел, с ним уже ничего не сделать.
     */
    private void keepThisSession(UsernamePasswordAuthenticationToken login,
                                 HttpServletRequest request, HttpServletResponse response) {
        List<GrantedAuthority> authorities = new ArrayList<>(login.getAuthorities());
        authorities.removeIf(authority -> FactorGrantedAuthority.PASSWORD_AUTHORITY.equals(authority.getAuthority()));
        authorities.add(FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)
                .issuedAt(Instant.now(clock))
                .build());

        UsernamePasswordAuthenticationToken renewed =
                UsernamePasswordAuthenticationToken.authenticated(login.getPrincipal(), null, authorities);
        renewed.setDetails(login.getDetails());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(renewed);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        request.changeSessionId();
    }

    @ExceptionHandler(WrongCurrentPasswordException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail wrongCurrentPassword(WrongCurrentPasswordException e) {
        return badRequest(e.getMessage(), "WRONG_CURRENT_PASSWORD");
    }

    @ExceptionHandler(WeakPasswordException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail weakPassword(WeakPasswordException e) {
        return badRequest(e.getMessage(), e.verdict().name());
    }

    /** {@code reason} — для интерфейса: по нему он выбирает, что написать под полем. */
    private static ProblemDetail badRequest(String detail, String reason) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty("reason", reason);
        return problem;
    }
}
