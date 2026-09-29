package com.example.planner.registration;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Тело запроса POST /api/v1/registrations. Формат email проверяет {@link com.example.planner.user.EmailAddress}. */
@Schema(description = "Данные для саморегистрации")
public record RegistrationRequest(

        @Schema(description = "Любая почта, к которой у пользователя есть доступ. Регистр и пробелы по краям не важны.",
                example = "ivan.petrov@mail.ru")
        @NotBlank
        @Size(max = 254)
        String email,

        @Schema(description = "ФИО полностью", example = "Иван Петров")
        @NotBlank
        @Size(max = 255)
        String fullName,

        // Минимум 8 — рекомендация NIST; максимум — защита от запросов с мегабайтным «паролем».
        @Schema(description = "Пароль, от 8 до 128 символов", example = "correct horse battery staple")
        @NotNull
        @Size(min = 8, max = 128)
        String password
) {
}
