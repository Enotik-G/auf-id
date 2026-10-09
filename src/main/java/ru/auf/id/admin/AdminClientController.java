package ru.auf.id.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Регистрация сервисов экосистемы. Доступ только роли {@code ADMIN} — правило в
 * {@code SecurityConfiguration} вместе с остальными.
 *
 * <p>Тело запроса — {@link ClientSpec} как есть: вся проверка настроек живёт в сервисе, с понятными
 * сообщениями, и дублировать её аннотациями значило бы иметь два источника правды.
 */
@RestController
@RequestMapping("/api/v1/admin/clients")
@RequiredArgsConstructor
@Tag(name = "Админка: клиенты", description = "Подключение сервисов к SSO. Только для ADMIN.")
public class AdminClientController {

    private final AdminClientService clients;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Зарегистрировать сервис",
            description = """
                    Создаёт клиента. Для `CONFIDENTIAL`, `SERVICE` и `NATIVE` возвращает секрет —
                    **он виден только сейчас**, в базе остаётся только его хеш. Потерялся —
                    выдайте новый через `POST /api/v1/admin/clients/{clientId}/secret`.

                    Секрет `NATIVE` (настольное приложение) спрятать в программе невозможно — он
                    нужен Spring для обмена refresh-токена, а защищает вход PKCE.

                    Refresh-токены получает только `NATIVE`: живут 30 дней от последнего
                    обновления, при каждом обновлении выдаётся новый, а повторно предъявленный
                    старый обрывает всю цепочку (признак кражи).""")
    @ApiResponse(responseCode = "201", description = "Клиент зарегистрирован")
    @ApiResponse(responseCode = "400", description = "Несочетаемые или неполные настройки", content = @Content)
    @ApiResponse(responseCode = "409", description = "Клиент с таким clientId уже есть", content = @Content)
    public ClientCredentials register(@RequestBody ClientSpec spec) {
        return clients.register(spec);
    }

    @GetMapping
    @Operation(summary = "Список зарегистрированных сервисов")
    public List<ClientSummary> list() {
        return clients.list();
    }

    @GetMapping("/{clientId}")
    @Operation(summary = "Посмотреть сервис")
    @ApiResponse(responseCode = "404", description = "Клиент не зарегистрирован", content = @Content)
    public ClientSummary get(@PathVariable String clientId) {
        return clients.get(clientId);
    }

    @PostMapping("/{clientId}/secret")
    @Operation(
            summary = "Выдать новый секрет",
            description = "Прежний секрет перестаёт работать сразу — сервис придётся перенастроить.")
    @ApiResponse(responseCode = "400", description = "У публичного клиента секрета нет", content = @Content)
    @ApiResponse(responseCode = "404", description = "Клиент не зарегистрирован", content = @Content)
    public ClientCredentials rotateSecret(@PathVariable String clientId) {
        return clients.rotateSecret(clientId);
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Снять регистрацию",
            description = """
                    Удаляет клиента вместе с выданными ему авторизациями: его токены перестают
                    обновляться, а уже выданные доживают свои 10 минут.""")
    @ApiResponse(responseCode = "404", description = "Клиент не зарегистрирован", content = @Content)
    public void unregister(@PathVariable String clientId) {
        clients.unregister(clientId);
    }

    @ExceptionHandler(ClientNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail notFound(ClientNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ClientAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail alreadyExists(ClientAlreadyExistsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidClientSpecException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail invalidSpec(InvalidClientSpecException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
