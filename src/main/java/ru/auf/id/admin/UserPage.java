package ru.auf.id.admin;

import ru.auf.id.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Страница списка пользователей. Свой ответ, а не {@code Page} из Spring Data: тот при отдаче в
 * JSON меняет форму от версии к версии, а клиенту админки нужна стабильная.
 */
@Schema(description = "Страница пользователей")
public record UserPage(

        List<UserResponse> items,

        @Schema(description = "Номер страницы, с нуля", example = "0")
        int page,

        @Schema(description = "Размер страницы", example = "20")
        int size,

        @Schema(description = "Сколько всего пользователей подходит под фильтр", example = "137")
        long total
) {
    static UserPage of(Page<User> users) {
        return new UserPage(users.map(UserResponse::of).getContent(),
                users.getNumber(), users.getSize(), users.getTotalElements());
    }
}
