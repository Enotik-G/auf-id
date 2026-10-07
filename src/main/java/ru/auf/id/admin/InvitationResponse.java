package ru.auf.id.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Созданная учётка и ссылка активации для неё.
 *
 * <p>Ссылку администратор передаёт человеку лично: писем сервис не отправляет. Показать её можно
 * только сейчас — в базе лежит лишь SHA-256 от токена, и восстановить ссылку оттуда невозможно.
 * Потерялась — выдать новую через {@code POST /api/v1/admin/users/{id}/activation-link}.
 */
@Schema(description = "Учётная запись и одноразовая ссылка активации")
public record InvitationResponse(

        @Schema(description = "Идентификатор учётной записи. Он же приходит в claim sub.",
                example = "0199bc42-8f31-7a1e-9c55-2b7d4e6a1f90")
        UUID userId,

        @Schema(description = "Ссылка для установки пароля. Действует 7 дней, одноразовая.",
                example = "http://localhost:8080/activate?token=xT9...")
        String activationLink
) {
}
