package ru.auf.id.account;

import ru.auf.id.ClockConfiguration;
import ru.auf.id.SecurityConfiguration;
import ru.auf.id.TestTime;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@Import({SecurityConfiguration.class, ClockConfiguration.class})
class MeControllerTest {

    private static final UUID USER_ID = UUID.fromString("0199bc42-8f31-7a1e-9c55-2b7d4e6a1f90");

    @Autowired
    private MockMvc mockMvc;

    /** Нужен и контроллеру, и фильтру перепроверки сессии, который ставит SecurityConfiguration. */
    @MockitoBean
    private UserRepository userRepository;

    /** Смену пароля целиком проверяет PasswordChangeTest; здесь — только профиль и доступ. */
    @MockitoBean
    private PasswordService passwordService;

    @Test
    void anonymousGetsUnauthorizedInsteadOfRedirectToLoginPage() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Имя вошедшего — его id: по нему контроллер и находит учётку. Заглушка отвечает только на этот
     * id — найди контроллер учётку по чему-то другому, тест упал бы.
     */
    @Test
    void showsProfileOfTheLoggedInUser() throws Exception {
        User ivan = User.invited(new EmailAddress("ivan.petrov@sinhub.ru"), "Иван Петров", TestTime.NOW);
        ivan.activate();
        ivan.grantRole(Role.STUDENT);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(ivan));

        mockMvc.perform(get("/api/v1/me").with(user(USER_ID.toString()).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ivan.petrov@sinhub.ru"))
                .andExpect(jsonPath("$.fullName").value("Иван Петров"))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
                // Статуса в ответе нет: вошедший всегда ACTIVE.
                .andExpect(jsonPath("$.status").doesNotExist());
    }
}
