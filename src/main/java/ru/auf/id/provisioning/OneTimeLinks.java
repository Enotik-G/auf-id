package ru.auf.id.provisioning;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Собирает одноразовые ссылки из токенов: {@code <публичный адрес>/<страница>?token=...}.
 *
 * <p>Ссылок два вида — активация и сброс пароля, а выдают их несколько мест (админка, создание первых
 * администраторов при старте). Поэтому правило сборки живёт здесь, а не повторяется у каждого.
 */
@Component
public class OneTimeLinks {

    /** Страница, где задают новый пароль по ссылке сброса. */
    public static final String PASSWORD_RESET_PATH = "/reset-password";

    private final String publicUrl;

    public OneTimeLinks(@Value("${auth.public-url}") String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String activationLinkFor(String activationToken) {
        return linkTo(ActivationPageController.ACTIVATE_PATH, activationToken);
    }

    public String passwordResetLinkFor(String resetToken) {
        return linkTo(PASSWORD_RESET_PATH, resetToken);
    }

    private String linkTo(String path, String token) {
        return UriComponentsBuilder.fromUriString(publicUrl)
                .path(path)
                .queryParam("token", token)
                .build()
                .toUriString();
    }
}
