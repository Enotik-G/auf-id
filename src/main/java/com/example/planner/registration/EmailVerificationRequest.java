package com.example.planner.registration;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Тело запроса POST /api/v1/email-verifications. */
@Schema(description = "Токен из ссылки в письме")
public record EmailVerificationRequest(

        @Schema(description = "Значение параметра token из ссылки вида /verify-email?token=...",
                example = "kR7xQm2Zp0vW8nYbT3sLq9fHj4dC6aE1uI5oN2gB7xM")
        @NotBlank
        @Size(max = 100)
        String token
) {
}
