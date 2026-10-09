package ru.auf.id.provisioning;

import ru.auf.id.user.UserRepository;
import ru.auf.id.SecurityConfiguration;
import ru.auf.id.onetimetoken.InvalidOneTimeTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivationPageController.class)
@Import({SecurityConfiguration.class, PasswordPolicy.class})
class ActivationPageControllerTest {

    /** Нужен фильтру перепроверки сессии, который ставит SecurityConfiguration. */
    @MockitoBean
    private UserRepository userRepository;

    private static final String TOKEN = "activation-token";
    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProvisioningService provisioning;

    @Test
    void showsPasswordFormForALink() throws Exception {
        mockMvc.perform(get("/activate").param("token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Придумайте пароль")))
                .andExpect(content().string(containsString(TOKEN)));
    }

    /** Открытие страницы ничего не меняет: токен гасится только при отправке формы. */
    @Test
    void openingThePageDoesNotConsumeTheToken() throws Exception {
        mockMvc.perform(get("/activate").param("token", TOKEN)).andExpect(status().isOk());

        verifyNoInteractions(provisioning);
    }

    @Test
    void showsInvalidLinkWhenTokenIsMissing() throws Exception {
        mockMvc.perform(get("/activate"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ссылка недействительна")));
    }

    @Test
    void setsPasswordAndRedirectsToSuccess() throws Exception {
        mockMvc.perform(post("/activate").with(csrf())
                        .param("token", TOKEN)
                        .param("password", PASSWORD)
                        .param("passwordConfirmation", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/activate/done"));

        verify(provisioning).activate(TOKEN, PASSWORD);
    }

    @Test
    void refusesTooShortPasswordWithoutTouchingTheToken() throws Exception {
        mockMvc.perform(post("/activate").with(csrf())
                        .param("token", TOKEN)
                        .param("password", "short")
                        .param("passwordConfirmation", "short"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("не короче")));

        verifyNoInteractions(provisioning);
    }

    /** 11 символов — уже мало: минимум 12 (раздел 3 архитектурного документа). */
    @Test
    void refusesElevenCharacterPassword() throws Exception {
        mockMvc.perform(post("/activate").with(csrf())
                        .param("token", TOKEN)
                        .param("password", "elevenchars")
                        .param("passwordConfirmation", "elevenchars"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("не короче")));

        verifyNoInteractions(provisioning);
    }

    /** Длинный, но из списка утёкших — такой подберут первым. */
    @Test
    void refusesCommonPassword() throws Exception {
        mockMvc.perform(post("/activate").with(csrf())
                        .param("token", TOKEN)
                        .param("password", "Qwertyuiop123")
                        .param("passwordConfirmation", "Qwertyuiop123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("слишком распространён")));

        verifyNoInteractions(provisioning);
    }

    /** Самостоятельно восстановить пароль нельзя, поэтому опечатку лучше не допустить. */
    @Test
    void refusesMismatchedConfirmation() throws Exception {
        mockMvc.perform(post("/activate").with(csrf())
                        .param("token", TOKEN)
                        .param("password", PASSWORD)
                        .param("passwordConfirmation", PASSWORD + " typo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Пароли не совпадают")));

        verifyNoInteractions(provisioning);
    }

    @Test
    void showsInvalidLinkWhenServiceRejectsTheToken() throws Exception {
        doThrow(new InvalidOneTimeTokenException()).when(provisioning).activate(TOKEN, PASSWORD);

        mockMvc.perform(post("/activate").with(csrf())
                        .param("token", TOKEN)
                        .param("password", PASSWORD)
                        .param("passwordConfirmation", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ссылка недействительна")));
    }

    @Test
    void formWithoutCsrfTokenIsRefused() throws Exception {
        mockMvc.perform(post("/activate")
                        .param("token", TOKEN)
                        .param("password", PASSWORD)
                        .param("passwordConfirmation", PASSWORD))
                .andExpect(status().isForbidden());

        verifyNoInteractions(provisioning);
    }

    @Test
    void successPageIsOpenWithoutLogin() throws Exception {
        mockMvc.perform(get("/activate/done"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Аккаунт активирован")));
    }
}
