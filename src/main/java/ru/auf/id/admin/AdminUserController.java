package ru.auf.id.admin;

import ru.auf.id.provisioning.ActivationPageController;
import ru.auf.id.provisioning.EmailAlreadyTakenException;
import ru.auf.id.provisioning.Invitation;
import ru.auf.id.provisioning.ProvisioningService;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.InvalidEmailException;
import ru.auf.id.user.WrongUserStatusException;
import ru.auf.id.user.Role;
import ru.auf.id.user.UserNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * Управление учётными записями. Доступ только роли {@code ADMIN} — правило в
 * {@code SecurityConfiguration}, а не на методах: так его видно в одном месте вместе с остальными.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Админка: пользователи", description = "Выдача учёток, блокировка, роли. Только для ADMIN.")
public class AdminUserController {

    private final ProvisioningService provisioning;
    private final AdminUserService adminUsers;
    private final String publicUrl;

    public AdminUserController(ProvisioningService provisioning,
                               AdminUserService adminUsers,
                               @Value("${auth.public-url}") String publicUrl) {
        this.provisioning = provisioning;
        this.adminUsers = adminUsers;
        this.publicUrl = publicUrl;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Завести учётную запись",
            description = """
                    Создаёт учётку в статусе `INVITED` и возвращает одноразовую ссылку активации.

                    Писем сервис не отправляет — ссылку передайте человеку лично. Показать её можно
                    **только сейчас**: в базе хранится лишь хеш токена. Потерялась — выдайте новую
                    через `POST /api/v1/admin/users/{id}/activation-link`.""")
    @ApiResponse(responseCode = "201", description = "Учётка создана, ссылка в ответе")
    @ApiResponse(responseCode = "409", description = "Учётка с такой почтой уже есть", content = @Content)
    @ApiResponse(responseCode = "400", description = "Почта некорректна или не в домене колледжа, не указано имя", content = @Content)
    public InvitationResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        Invitation invitation = provisioning.invite(
                new EmailAddress(request.email()), request.fullName().strip(), request.roles());
        return toResponse(invitation);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Посмотреть учётную запись")
    @ApiResponse(responseCode = "404", description = "Учётки нет", content = @Content)
    public UserResponse getUser(@PathVariable UUID userId) {
        return UserResponse.of(adminUsers.get(userId));
    }

    @PostMapping("/{userId}/activation-link")
    @Operation(
            summary = "Выдать новую ссылку активации",
            description = "Прежние ссылки этой учётки перестают работать — старая могла уйти не туда.")
    @ApiResponse(responseCode = "409", description = "Учётка уже активирована или заблокирована", content = @Content)
    @ApiResponse(responseCode = "404", description = "Учётки нет", content = @Content)
    public InvitationResponse reissueActivationLink(@PathVariable UUID userId) {
        return toResponse(provisioning.reissueInvitation(userId));
    }

    @PostMapping("/{userId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Заблокировать",
            description = """
                    Закрывает вход и обесценивает ожидающие ссылки.

                    Уже выданные access-токены продолжают работать до истечения — до 10 минут.""")
    @ApiResponse(responseCode = "409", description = "Это последний действующий администратор", content = @Content)
    @ApiResponse(responseCode = "404", description = "Учётки нет", content = @Content)
    public void block(@PathVariable UUID userId) {
        adminUsers.block(userId);
    }

    @PostMapping("/{userId}/unblock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Снять блокировку",
            description = """
                    Учётка с паролем возвращается в `ACTIVE`, без пароля — в `INVITED`:
                    во вторую всё равно нельзя войти, пока не выдана ссылка активации.""")
    @ApiResponse(responseCode = "409", description = "Учётка не заблокирована", content = @Content)
    @ApiResponse(responseCode = "404", description = "Учётки нет", content = @Content)
    public void unblock(@PathVariable UUID userId) {
        adminUsers.unblock(userId);
    }

    @PutMapping("/{userId}/roles/{role}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Выдать роль", description = "Повторная выдача той же роли ничего не меняет.")
    @ApiResponse(responseCode = "404", description = "Учётки нет", content = @Content)
    public void grantRole(@PathVariable UUID userId, @PathVariable Role role) {
        adminUsers.grantRole(userId, role);
    }

    @DeleteMapping("/{userId}/roles/{role}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Снять роль",
            description = "Снять `ADMIN` у последнего действующего администратора нельзя.")
    @ApiResponse(responseCode = "409", description = "Это последний действующий администратор", content = @Content)
    @ApiResponse(responseCode = "404", description = "Учётки нет", content = @Content)
    public void revokeRole(@PathVariable UUID userId, @PathVariable Role role) {
        adminUsers.revokeRole(userId, role);
    }

    private InvitationResponse toResponse(Invitation invitation) {
        String link = UriComponentsBuilder.fromUriString(publicUrl)
                .path(ActivationPageController.ACTIVATE_PATH)
                .queryParam("token", invitation.activationToken())
                .build()
                .toUriString();
        return new InvitationResponse(invitation.userId(), link);
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail userNotFound(UserNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(EmailAlreadyTakenException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail emailTaken(EmailAlreadyTakenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(LastAdminException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail lastAdmin(LastAdminException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Учётка не в том состоянии: активировать активную, разблокировать незаблокированную и т.п. */
    @ExceptionHandler(WrongUserStatusException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail wrongState(WrongUserStatusException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidEmailException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail invalidEmail(InvalidEmailException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
