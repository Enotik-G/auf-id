package ru.auf.id.provisioning;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Собирает ссылку активации из одноразового токена: {@code <публичный адрес>/activate?token=...}.
 *
 * <p>Ссылку выдают двое — админка и создание первых администраторов при старте, — поэтому правило
 * сборки живёт в одном месте, а не повторяется у каждого.
 */
@Component
public class ActivationLinks {

    private final String publicUrl;

    public ActivationLinks(@Value("${auth.public-url}") String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String linkFor(String activationToken) {
        return UriComponentsBuilder.fromUriString(publicUrl)
                .path(ActivationPageController.ACTIVATE_PATH)
                .queryParam("token", activationToken)
                .build()
                .toUriString();
    }
}
