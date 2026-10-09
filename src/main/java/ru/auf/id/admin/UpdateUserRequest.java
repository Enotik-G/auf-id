package ru.auf.id.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Тело запроса PATCH /api/v1/admin/users/{id}. Поле не передано ({@code null}) — не меняется.
 * Формат и домен почты проверяет сервис ({@link ru.auf.id.user.EmailAddress}, домен колледжа).
 */
@Schema(description = "Что исправить в учётной записи. Не переданное поле не меняется")
public record UpdateUserRequest(

        @Schema(description = "ФИО полностью", example = "Иван Сидоров", nullable = true)
        @Size(max = 255)
        // Не пустое и не из одних пробелов, если передано.
        @Pattern(regexp = ".*\\S.*")
        String fullName,

        @Schema(description = "Новая почта колледжа (домен sinhub.ru). Это и логин: входить придётся с ней",
                example = "ivan.sidorov@sinhub.ru", nullable = true)
        @Size(max = 254)
        String email
) {
    /** Пустой запрос — скорее ошибка клиента, чем намерение «ничего не менять». */
    @AssertTrue(message = "Нужно указать fullName или email")
    @Schema(hidden = true)
    public boolean isAnythingToChange() {
        return fullName != null || email != null;
    }
}
