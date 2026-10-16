package ru.auf.id.account;

import ru.auf.id.TestTime;
import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.provisioning.ProvisioningService;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.PasswordCredential;
import ru.auf.id.user.PasswordCredentialRepository;
import ru.auf.id.user.PasswordHasher;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сброс забытого пароля целиком, на настоящем приложении: админ выдаёт ссылку, человек без входа
 * открывает страницу, задаёт новый пароль и входит с ним.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordResetTest {

    private static final String EMAIL = "ivan@sinhub.ru";
    private static final String OLD_PASSWORD = "забытый-пароль-студента";
    private static final String NEW_PASSWORD = "новый-пароль-студента";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProvisioningService provisioning;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordCredentialRepository credentialRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private StringRedisTemplate redis;

    private User student;

    @BeforeEach
    void createStudent() {
        student = User.invited(new EmailAddress(EMAIL), "Иван Петров", TestTime.NOW);
        student.activate();
        userRepository.save(student);
        credentialRepository.save(PasswordCredential.forUser(student, passwordHasher.hash(OLD_PASSWORD), TestTime.NOW));
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    /** Страница открыта без входа — забывший пароль войти и не может. */
    @Test
    void formOpensWithoutLoggingIn() throws Exception {
        mockMvc.perform(get("/reset-password").param("token", provisioning.issuePasswordReset(student.getId())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("state", "form"));
    }

    @Test
    void newPasswordWorksAndTheOldOneDoesNot() throws Exception {
        String token = provisioning.issuePasswordReset(student.getId());

        submit(token, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(redirectedUrl("/reset-password/done"));

        mockMvc.perform(formLogin().user(EMAIL).password(NEW_PASSWORD)).andExpect(authenticated());
        mockMvc.perform(formLogin().user(EMAIL).password(OLD_PASSWORD)).andExpect(unauthenticated());
    }

    /** Сброс обрывает сессию того, кто вошёл со старым паролем, — как и смена пароля. */
    @Test
    void sessionOpenedWithTheOldPasswordIsClosed() throws Exception {
        MockHttpSession someoneElses = (MockHttpSession) mockMvc.perform(formLogin().user(EMAIL).password(OLD_PASSWORD))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession(false);

        submit(provisioning.issuePasswordReset(student.getId()), NEW_PASSWORD, NEW_PASSWORD);

        mockMvc.perform(get("/api/v1/me").session(someoneElses))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void linkCannotBeUsedTwice() throws Exception {
        String token = provisioning.issuePasswordReset(student.getId());
        submit(token, NEW_PASSWORD, NEW_PASSWORD);

        submit(token, "ещё-один-новый-пароль", "ещё-один-новый-пароль")
                .andExpect(model().attribute("state", "invalid"));
    }

    /** Плохой пароль не тратит ссылку: транзакция откатывается вместе с погашением токена. */
    @Test
    void weakPasswordKeepsTheLinkUsable() throws Exception {
        String token = provisioning.issuePasswordReset(student.getId());

        submit(token, "короткий", "короткий")
                .andExpect(model().attribute("state", "weak"));

        submit(token, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(redirectedUrl("/reset-password/done"));
    }

    @Test
    void passwordFromLeakListsIsRefused() throws Exception {
        String token = provisioning.issuePasswordReset(student.getId());

        submit(token, "password1234", "password1234")
                .andExpect(model().attribute("state", "common"));
    }

    @Test
    void mismatchedConfirmationIsRefused() throws Exception {
        String token = provisioning.issuePasswordReset(student.getId());

        submit(token, NEW_PASSWORD, NEW_PASSWORD + "!")
                .andExpect(model().attribute("state", "mismatch"));
    }

    /** Заблокировали после выдачи ссылки — ссылка не должна стать обходом блокировки. */
    @Test
    void linkStopsWorkingOnceTheAccountIsBlocked() throws Exception {
        String token = provisioning.issuePasswordReset(student.getId());
        User stored = userRepository.findById(student.getId()).orElseThrow();
        stored.block();
        userRepository.save(stored);

        submit(token, NEW_PASSWORD, NEW_PASSWORD)
                .andExpect(model().attribute("state", "invalid"));
    }

    private ResultActions submit(String token, String password, String confirmation) throws Exception {
        return mockMvc.perform(post("/reset-password").with(csrf())
                .param("token", token)
                .param("password", password)
                .param("passwordConfirmation", confirmation));
    }
}
