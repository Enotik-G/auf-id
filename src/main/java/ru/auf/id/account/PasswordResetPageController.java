package ru.auf.id.account;

import ru.auf.id.onetimetoken.InvalidOneTimeTokenException;
import ru.auf.id.provisioning.OneTimeLinks;
import ru.auf.id.provisioning.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Страница, на которую ведёт ссылка сброса пароля от администратора: человек забыл пароль и задаёт
 * новый. Устроена как страница активации ({@code ActivationPageController}), только без согласия на
 * ПДн — его уже дали при активации.
 *
 * <p>GET только показывает форму: токен гасится при отправке, иначе предпросмотр ссылки в
 * мессенджере сжёг бы её раньше, чем человек введёт пароль.
 */
@Controller
@RequiredArgsConstructor
public class PasswordResetPageController {

    public static final String DONE_PATH = OneTimeLinks.PASSWORD_RESET_PATH + "/done";

    private static final String PAGE = "reset-password";

    private final PasswordService passwordService;

    @GetMapping(OneTimeLinks.PASSWORD_RESET_PATH)
    public String showPasswordForm(@RequestParam(required = false) String token, Model model) {
        if (token == null || token.isBlank()) {
            return showState(model, "invalid", null);
        }
        return showState(model, "form", token);
    }

    @PostMapping(OneTimeLinks.PASSWORD_RESET_PATH)
    public String setPassword(@RequestParam String token,
                              @RequestParam String password,
                              @RequestParam String passwordConfirmation,
                              Model model) {
        if (!password.equals(passwordConfirmation)) {
            return showState(model, "mismatch", token);
        }
        try {
            passwordService.resetPassword(token, password);
        } catch (WeakPasswordException e) {
            // Правила пароля проверяет сервис; здесь — только какое сообщение показать.
            String state = e.verdict() == PasswordPolicy.Verdict.COMMON ? "common" : "weak";
            return showState(model, state, token);
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
        model.addAttribute("minPasswordLength", PasswordPolicy.MIN_LENGTH);
        model.addAttribute("maxPasswordLength", PasswordPolicy.MAX_LENGTH);
        return PAGE;
    }
}
