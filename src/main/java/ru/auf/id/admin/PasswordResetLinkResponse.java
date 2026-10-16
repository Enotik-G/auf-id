package ru.auf.id.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record PasswordResetLinkResponse(

        @Schema(example = "0199bc42-8f31-7a1e-9c55-2b7d4e6a1f90")
        UUID userId,

        @Schema(description = "Ссылка, по которой человек задаст новый пароль. Действует сутки, одноразовая. "
                + "Передайте её лично: она открывает уже работающую учётку.",
                example = "http://localhost:8080/reset-password?token=xT9...")
        String passwordResetLink
) {
}
