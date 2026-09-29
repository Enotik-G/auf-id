package com.example.planner.registration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Тело запроса POST /api/v1/registrations. Формат email проверяет {@link com.example.planner.user.EmailAddress}. */
public record RegistrationRequest(

        @NotBlank
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(max = 255)
        String fullName,

        // Минимум 8 — рекомендация NIST; максимум — защита от запросов с мегабайтным «паролем».
        @NotNull
        @Size(min = 8, max = 128)
        String password
) {
}
