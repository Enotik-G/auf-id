package ru.auf.id.provisioning;

import ru.auf.id.onetimetoken.InvalidOneTimeTokenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Страница, на которую ведёт ссылка активации от администратора: здесь владелец задаёт пароль.
 *
 * <p>GET только показывает форму и ничего не меняет — состояние меняет POST. Токен при этом не
 * гасится до отправки формы, иначе предпросмотр ссылки в мессенджере сжёг бы её до того, как человек
 * введёт пароль.
 */
@Controller
@RequiredArgsConstructor
public class ActivationPageController {

    public static final String ACTIVATE_PATH = "/activate";
    public static final String DONE_PATH = ACTIVATE_PATH + "/done";

    /** Минимальная длина пароля по рекомендации NIST (SP 800-63B): 8 символов, без требований к составу. */
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private static final String PAGE = "activate";

    private final ProvisioningService provisioning;

    @GetMapping(ACTIVATE_PATH)
    public String showPasswordForm(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank()) {
            return showState(model, "invalid", null);
        }
        return showState(model, "form", token);
    }

    @PostMapping(ACTIVATE_PATH)
    public String setPassword(@RequestParam String token,
                              @RequestParam String password,
                              @RequestParam String passwordConfirmation,
                              Model model) {
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            return showState(model, "weak", token);
        }
        // Восстановить пароль самостоятельно нельзя — писем нет, нужна новая ссылка от администратора.
        // Поэтому опечатку дешевле не допустить, чем потом разбирать.
        if (!password.equals(passwordConfirmation)) {
            return showState(model, "mismatch", token);
        }

        try {
            provisioning.activate(token, password);
        } catch (InvalidOneTimeTokenException e) {
            return showState(model, "invalid", null);
        }
        // Редирект, а не страница сразу: F5 на странице успеха не отправит форму повторно.
        return "redirect:" + DONE_PATH;
    }

    @GetMapping(DONE_PATH)
    public String showSuccess(Model model) {
        return showState(model, "success", null);
    }

    private static String showState(Model model, String state, String token) {
        model.addAttribute("state", state);
        model.addAttribute("token", token);
        model.addAttribute("minPasswordLength", MIN_PASSWORD_LENGTH);
        model.addAttribute("maxPasswordLength", MAX_PASSWORD_LENGTH);
        return PAGE;
    }
}
