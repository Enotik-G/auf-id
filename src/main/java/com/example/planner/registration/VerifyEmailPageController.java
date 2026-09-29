package com.example.planner.registration;

import com.example.planner.onetimetoken.InvalidOneTimeTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Страница, на которую ведёт ссылка из письма. GET только показывает кнопку и ничего не меняет:
 * почтовые антивирусы заранее открывают ссылки из писем, и токен сгорел бы до клика человека.
 * Подтверждение — по нажатию кнопки (POST).
 */
@Controller
@RequiredArgsConstructor
public class VerifyEmailPageController {

    private static final String PAGE = "verify-email";

    private final RegistrationService registrationService;

    @GetMapping(VerificationEmailSender.VERIFY_PATH)
    public String showConfirmButton(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank()) {
            return showState(model, "invalid");
        }
        model.addAttribute("token", token);
        return showState(model, "confirm");
    }

    @PostMapping(VerificationEmailSender.VERIFY_PATH)
    public String confirm(@RequestParam String token, Model model) {
        try {
            registrationService.confirmEmail(token);
        } catch (InvalidOneTimeTokenException e) {
            return showState(model, "invalid");
        }
        // Редирект, а не страница сразу: F5 на странице успеха не отправит форму повторно.
        return "redirect:" + VerificationEmailSender.VERIFY_PATH + "/done";
    }

    @GetMapping(VerificationEmailSender.VERIFY_PATH + "/done")
    public String showSuccess(Model model) {
        return showState(model, "success");
    }

    private static String showState(Model model, String state) {
        model.addAttribute("state", state);
        return PAGE;
    }
}
