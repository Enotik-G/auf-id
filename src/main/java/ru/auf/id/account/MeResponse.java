package ru.auf.id.account;

import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Своя учётная запись глазами её владельца.
 *
 * <p>Статуса здесь нет, в отличие от ответа админки: смотреть на себя может только вошедший, а войти
 * может только {@code ACTIVE} — поле всегда было бы одинаковым.
 */
@Schema(description = "Моя учётная запись")
public record MeResponse(

        @Schema(example = "0199bc42-8f31-7a1e-9c55-2b7d4e6a1f90")
        UUID id,

        @Schema(example = "ivan.petrov@sinhub.ru")
        String email,

        @Schema(example = "Иван Петров")
        String fullName,

        @Schema(example = "[\"STUDENT\"]")
        Set<Role> roles,

        @Schema(description = "Когда учётку завёл администратор")
        Instant createdAt,

        @Schema(description = "Последний успешный вход — то есть, как правило, текущий")
        Instant lastLoginAt
) {
    public static MeResponse of(User user) {
        return new MeResponse(user.getId(), user.getEmail().value(), user.getFullName(),
                user.getRoles(), user.getCreatedAt(), user.getLastLoginAt());
    }
}
