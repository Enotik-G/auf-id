package ru.auf.id.account;

import ru.auf.id.TestTime;
import ru.auf.id.TestcontainersConfiguration;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Смена пароля целиком, на настоящем приложении: вход формой, {@code POST /api/v1/me/password},
 * фильтр сессий, вход новым паролем.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordChangeTest {

    private static final String EMAIL = "ivan@sinhub.ru";
    private static final String OLD_PASSWORD = "старый-пароль-студента";
    private static final String NEW_PASSWORD = "новый-пароль-студента";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordCredentialRepository credentialRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void createStudent() {
        User student = User.invited(new EmailAddress(EMAIL), "Иван Петров", TestTime.NOW);
        student.activate();
        userRepository.save(student);
        credentialRepository.save(PasswordCredential.forUser(student, passwordHasher.hash(OLD_PASSWORD), TestTime.NOW));
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        // Неудачные входы старым паролем копят счётчик капчи в Redis.
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void changedPasswordWorksAndTheOldOneDoesNot() throws Exception {
        MockHttpSession session = logIn(OLD_PASSWORD);

        changePassword(session, OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isNoContent());

        mockMvc.perform(formLogin().user(EMAIL).password(NEW_PASSWORD)).andExpect(authenticated());
        mockMvc.perform(formLogin().user(EMAIL).password(OLD_PASSWORD)).andExpect(unauthenticated());
    }

    /** Фильтр закрывает сессии, открытые до смены, — но не ту, из которой пароль сменили. */
    @Test
    void thisSessionStaysOpen() throws Exception {
        MockHttpSession session = logIn(OLD_PASSWORD);

        changePassword(session, OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk());
    }

    /** Например, сессия того, кто подсмотрел старый пароль. */
    @Test
    void otherSessionsAreClosed() throws Exception {
        MockHttpSession mine = logIn(OLD_PASSWORD);
        MockHttpSession someoneElses = logIn(OLD_PASSWORD);

        changePassword(mine, OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/me").session(someoneElses))
                .andExpect(status().isUnauthorized());
    }

    /** Без текущего пароля открытая сессия не даёт забрать учётку. */
    @Test
    void wrongCurrentPasswordIsRefusedAndNothingChanges() throws Exception {
        MockHttpSession session = logIn(OLD_PASSWORD);

        changePassword(session, "не-тот-пароль-совсем", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("WRONG_CURRENT_PASSWORD"));

        mockMvc.perform(formLogin().user(EMAIL).password(OLD_PASSWORD)).andExpect(authenticated());
    }

    @Test
    void weakNewPasswordIsRefusedWithTheReason() throws Exception {
        MockHttpSession session = logIn(OLD_PASSWORD);

        changePassword(session, OLD_PASSWORD, "короткий")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("WRONG_LENGTH"));
    }

    /**
     * Брошенная открытой сессия не годится для подбора: после 5 неверных текущих паролей отказ
     * получает даже верный — иначе подбирающий просто дождался бы своего «угадал».
     */
    @Test
    void afterFiveWrongAttemptsEvenTheRightPasswordIsRefused() throws Exception {
        MockHttpSession session = logIn(OLD_PASSWORD);
        for (int attempt = 1; attempt <= 5; attempt++) {
            changePassword(session, "не-тот-пароль-" + attempt, NEW_PASSWORD).andExpect(status().isBadRequest());
        }

        changePassword(session, OLD_PASSWORD, NEW_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reason").value("TOO_MANY_ATTEMPTS"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    /** Удачная смена обнуляет счётчик: прошлые ошибки не копятся до блокировки. */
    @Test
    void successfulChangeResetsTheCounter() throws Exception {
        MockHttpSession session = logIn(OLD_PASSWORD);
        for (int attempt = 1; attempt <= 4; attempt++) {
            changePassword(session, "не-тот-пароль-" + attempt, NEW_PASSWORD).andExpect(status().isBadRequest());
        }
        changePassword(session, OLD_PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        // Без обнуления это были бы пятая и шестая ошибки подряд, и вторая получила бы 429.
        changePassword(session, "не-тот-пароль-5", "ещё-один-новый-пароль").andExpect(status().isBadRequest());
        changePassword(session, "не-тот-пароль-6", "ещё-один-новый-пароль").andExpect(status().isBadRequest());
    }

    private MockHttpSession logIn(String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin().user(EMAIL).password(password))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession(false);
    }

    private ResultActions changePassword(MockHttpSession session, String current, String next) throws Exception {
        return mockMvc.perform(post("/api/v1/me/password").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"%s","newPassword":"%s"}""".formatted(current, next)));
    }
}
