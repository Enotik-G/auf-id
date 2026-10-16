package ru.auf.id.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Повтора нового пароля здесь нет: он защищает от опечатки, а это забота формы в браузере. Серверу
 * сравнивать две строки, присланные одним и тем же запросом, незачем.
 */
@Schema(description = "Смена своего пароля")
public record ChangePasswordRequest(

        @Schema(description = "Текущий пароль", example = "старый-пароль-студента")
        @NotNull
        String currentPassword,

        @Schema(description = "Новый пароль: от 12 до 128 символов, не из списка утёкших",
                example = "новый-пароль-студента")
        @NotNull
        String newPassword
) {
}
