package com.example.planner.login;

import com.example.planner.SecurityConfiguration;
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
    void showsHintForUnconfirmedEmail() throws Exception {
        mockMvc.perform(get("/login?unconfirmed"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Почта ещё не подтверждена")));
    }

    @Test
    void showsTooManyAttemptsMessage() throws Exception {
        mockMvc.perform(get("/login?tooManyAttempts"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Слишком много попыток входа")));
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
