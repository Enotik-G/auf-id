package ru.auf.id.captcha;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Отсюда виджет ALTCHA на странице входа забирает задачку (атрибут {@code challenge}). */
@RestController
@RequiredArgsConstructor
@Hidden // служебный адрес для виджета, в документации API ему не место
public class CaptchaChallengeController {

    static final String CHALLENGE_PATH = "/captcha/challenge";

    private final CaptchaService captchaService;

    @GetMapping(value = CHALLENGE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> challenge() {
        return ResponseEntity.ok()
                // Каждая задачка одноразовая — кешировать её браузеру нельзя.
                .cacheControl(CacheControl.noStore())
                .body(captchaService.createChallenge().toJson());
    }
}
