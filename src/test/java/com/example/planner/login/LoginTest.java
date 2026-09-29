package com.example.planner.login;

import com.example.planner.TestcontainersConfiguration;
import com.example.planner.user.EmailAddress;
import com.example.planner.user.PasswordCredential;
import com.example.planner.user.PasswordCredentialRepository;
import com.example.planner.user.PasswordHasher;
import com.example.planner.user.User;
import com.example.planner.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Вход через настоящую форму логина — всё приложение целиком, настоящий Postgres. */
@SpringBootTest(properties = "auth.password.pepper=test-pepper-only-for-tests-0123456789")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordCredentialRepository credentialRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @MockitoBean
    private JavaMailSender mailSender;

    @AfterEach
    void deleteUsers() {
        userRepository.deleteAll();
    }

    @Test
    void activeUserLogsInIsIdentifiedByIdAndGoesToHomePage() throws Exception {
        User user = saveUser("ivan@mail.ru", true);

        mockMvc.perform(formLogin().user("Ivan@Mail.ru").password(PASSWORD))
                .andExpect(authenticated().withUsername(user.getId().toString()))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void successfulLoginIsRecorded() throws Exception {
        User user = saveUser("ivan@mail.ru", true);

        mockMvc.perform(formLogin().user("ivan@mail.ru").password(PASSWORD))
                .andExpect(authenticated());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void failedLoginIsNotRecorded() throws Exception {
        User user = saveUser("ivan@mail.ru", true);

        mockMvc.perform(formLogin().user("ivan@mail.ru").password("wrong password"))
                .andExpect(unauthenticated());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt()).isNull();
    }

    @Test
    void wrongPasswordGivesGeneralError() throws Exception {
        saveUser("ivan@mail.ru", true);

        mockMvc.perform(formLogin().user("ivan@mail.ru").password("wrong password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void unconfirmedEmailWithCorrectPasswordGetsHint() throws Exception {
        saveUser("ivan@mail.ru", false);

        mockMvc.perform(formLogin().user("ivan@mail.ru").password(PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?unconfirmed"));
    }

    /** Главная проверка безопасности этой задачи: без пароля статус чужой почты не узнать. */
    @Test
    void unconfirmedEmailWithWrongPasswordLooksLikeAnyOtherError() throws Exception {
        saveUser("ivan@mail.ru", false);

        mockMvc.perform(formLogin().user("ivan@mail.ru").password("wrong password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void unknownEmailGivesGeneralError() throws Exception {
        mockMvc.perform(formLogin().user("nobody@mail.ru").password(PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void homePageGreetsLoggedInUser() throws Exception {
        User user = saveUser("ivan@mail.ru", true);

        mockMvc.perform(get("/").with(user(user.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Здравствуйте, Иван Петров!")))
                .andExpect(content().string(containsString("ivan@mail.ru")));
    }

    @Test
    void homePageRequiresLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void logoutReturnsToLoginPage() throws Exception {
        mockMvc.perform(logout())
                .andExpect(redirectedUrl("/login?logout"));
    }

    private User saveUser(String email, boolean confirmed) {
        User user = User.selfRegistered(new EmailAddress(email), "Иван Петров");
        if (confirmed) {
            user.verifyEmail();
        }
        userRepository.save(user);
        credentialRepository.save(PasswordCredential.forUser(user, passwordHasher.hash(PASSWORD)));
        return user;
    }
}
