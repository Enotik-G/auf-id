package ru.auf.id.account;

import ru.auf.id.user.UserNotFoundException;
import ru.auf.id.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
@Tag(name = "Моя учётная запись", description = "Профиль вошедшего пользователя. Доступно любому вошедшему.")
public class MeController {

    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Мой профиль")
    @ApiResponse(responseCode = "401", description = "Не выполнен вход", content = @Content)
    public MeResponse me(Authentication authentication) {
        // Имя вошедшего — его id (см. AccountUserDetailsService).
        UUID userId = UUID.fromString(authentication.getName());
        return MeResponse.of(userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId)));
    }
}
