package ru.auf.id.login;

import ru.auf.id.user.UserRepository;
import ru.auf.id.SecurityConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginPageController.class)
@Import(SecurityConfiguration.class)
class LoginPageControllerTest {

    /** Нужен фильтру перепроверки сессии, который ставит SecurityConfiguration. */
    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageIsOpenAndHasFormWithCsrf() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>Вход</h1>")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("Неверная почта или пароль"))));
    }

    @Test
    void showsGeneralErrorAfterFailedLogin() throws Exception {
        mockMvc.perform(get("/login?error"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Неверная почта или пароль.")));
    }

    @Test
    void showsHintForBlockedAccount() throws Exception {
        mockMvc.perform(get("/login?blocked"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Доступ к аккаунту закрыт")));
    }

    @Test
    void showsTooManyAttemptsMessage() throws Exception {
        mockMvc.perform(get("/login?tooManyAttempts"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Слишком много попыток входа")));
    }

    @Test
    void pageAlwaysHasCaptchaWidgetThatSolvesItself() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(content().string(containsString("<altcha-widget")))
                .andExpect(content().string(containsString("challenge=\"/captcha/challenge\"")))
                .andExpect(content().string(containsString("auto=\"onload\"")));
    }

    @Test
    void asksToPassCaptcha() throws Exception {
        mockMvc.perform(get("/login?captcha"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Подтвердите, что вы не робот")));
    }

    @Test
    void captchaScriptIsAvailableWithoutLogin() throws Exception {
        mockMvc.perform(get("/js/altcha-3.2.3.i18n.min.js"))
                .andExpect(status().isOk());
    }

    @Test
    void confirmsLogout() throws Exception {
        mockMvc.perform(get("/login?logout"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Вы вышли из аккаунта.")));
    }

    @Test
    void stylesAreAvailableWithoutLogin() throws Exception {
        mockMvc.perform(get("/css/auth.css"))
                .andExpect(status().isOk());
    }
}
