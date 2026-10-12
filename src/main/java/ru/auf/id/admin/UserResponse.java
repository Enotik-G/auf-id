package ru.auf.id.admin;

import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Schema(description = "Учётная запись глазами администратора")
public record UserResponse(

        @Schema(example = "0199bc42-8f31-7a1e-9c55-2b7d4e6a1f90")
        UUID id,

        @Schema(example = "ivan.petrov@sinhub.ru")
        String email,

        @Schema(example = "Иван Петров")
        String fullName,

        @Schema(description = "INVITED — ждёт активации, ACTIVE — работает, BLOCKED — доступ закрыт администратором, "
                + "LOCKED — вход закрыт (пока не используется), DELETED — удалена",
                example = "ACTIVE")
        String status,

        @Schema(example = "[\"STUDENT\"]")
        Set<Role> roles,

        @Schema(description = "Когда учётку завёл администратор")
        Instant createdAt,

        @Schema(description = "Последний успешный вход; null, если ни разу не входил")
        Instant lastLoginAt
) {
    public static UserResponse of(User user) {
        return new UserResponse(user.getId(), user.getEmail().value(), user.getFullName(),
                user.getStatus().name(), user.getRoles(), user.getCreatedAt(), user.getLastLoginAt());
    }
}
