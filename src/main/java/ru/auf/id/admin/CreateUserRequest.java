package ru.auf.id.admin;

import ru.auf.id.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Тело запроса POST /api/v1/admin/users. Формат почты проверяет {@link ru.auf.id.user.EmailAddress}. */
@Schema(description = "Данные новой учётной записи")
public record CreateUserRequest(

        @Schema(description = "Почта колледжа (домен sinhub.ru). Регистр и пробелы по краям не важны.", example = "ivan.petrov@sinhub.ru")
        @NotBlank
        @Size(max = 254)
        String email,

        @Schema(description = "ФИО полностью", example = "Иван Петров")
        @NotBlank
        @Size(max = 255)
        String fullName,

        @Schema(description = "Роли, выдаваемые сразу. Можно не указывать.", example = "[\"STUDENT\"]")
        Set<Role> roles
) {
    /** Роли необязательны: учётку без ролей тоже бывает нужно завести. */
    public CreateUserRequest {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
