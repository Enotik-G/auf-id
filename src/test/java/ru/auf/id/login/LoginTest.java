package ru.auf.id.login;

import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.captcha.CaptchaService;
import ru.auf.id.captcha.CaptchaTestSupport;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.PasswordCredential;
import ru.auf.id.user.PasswordCredentialRepository;
import ru.auf.id.user.PasswordHasher;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Вход через настоящую форму логина — всё приложение целиком, настоящий Postgres. */
@SpringBootTest
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

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private LoginAttemptService loginAttempts;

    @Autowired
    private CaptchaService captchaService;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void wrongPasswordsNoLongerLockTheAccount() throws Exception {
        saveUser("ivan@sinhub.ru", true);
        wrongPasswordTimes("ivan@sinhub.ru", 10);

        // Блокировки больше нет (её заменила капча): чужой аккаунт так не закрыть —
        // владелец решит капчу и войдёт.
        mockMvc.perform(loginWithCaptcha("ivan@sinhub.ru", PASSWORD, CaptchaTestSupport.solve(captchaService.createChallenge())))
                .andExpect(authenticated());
    }

    @Test
    void threeWrongPasswordsMakeCaptchaRequired() throws Exception {
        saveUser("ivan@sinhub.ru", true);
        wrongPasswordTimes("Ivan@Sinhub.ru", 3);

        assertThat(loginAttempts.isCaptchaRequired(new EmailAddress("ivan@sinhub.ru"))).isTrue();
    }

    /** Главное свойство счётчика: для выдуманной почты — ровно то же, что для настоящей. */
    @Test
    void unknownEmailGetsCaptchaTheSameWay() throws Exception {
        wrongPasswordTimes("nobody@sinhub.ru", 3);

        assertThat(loginAttempts.isCaptchaRequired(new EmailAddress("nobody@sinhub.ru"))).isTrue();
    }

    @Test
    void successfulLoginResetsFailureCount() throws Exception {
        saveUser("ivan@sinhub.ru", true);
        wrongPasswordTimes("ivan@sinhub.ru", 3);

        mockMvc.perform(loginWithCaptcha("ivan@sinhub.ru", PASSWORD, CaptchaTestSupport.solve(captchaService.createChallenge())))
                .andExpect(authenticated());

        assertThat(loginAttempts.isCaptchaRequired(new EmailAddress("ivan@sinhub.ru"))).isFalse();
    }

    @Test
    void afterThreeFailuresCorrectPasswordWithoutCaptchaIsNotEnough() throws Exception {
        saveUser("ivan@sinhub.ru", true);
        wrongPasswordTimes("ivan@sinhub.ru", 3);

        mockMvc.perform(formLogin().user("ivan@sinhub.ru").password(PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?captcha"));
    }

    @Test
    void afterThreeFailuresCorrectPasswordWithSolvedCaptchaLogsIn() throws Exception {
        saveUser("ivan@sinhub.ru", true);
        wrongPasswordTimes("ivan@sinhub.ru", 3);

        mockMvc.perform(loginWithCaptcha("ivan@sinhub.ru", PASSWORD, CaptchaTestSupport.solve(captchaService.createChallenge())))
                .andExpect(authenticated());
    }

    @Test
    void usedCaptchaSolutionDoesNotWorkTwice() throws Exception {
        saveUser("ivan@sinhub.ru", true);
        wrongPasswordTimes("ivan@sinhub.ru", 3);
        String solution = CaptchaTestSupport.solve(captchaService.createChallenge());
        mockMvc.perform(loginWithCaptcha("ivan@sinhub.ru", "wrong password", solution))
                .andExpect(redirectedUrl("/login?error"));

        mockMvc.perform(loginWithCaptcha("ivan@sinhub.ru", PASSWORD, solution))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?captcha"));
    }

    /** Для выдуманной почты — ровно то же требование: по капче не понять, есть ли аккаунт. */
    @Test
    void unknownEmailAlsoRequiresCaptchaAfterThreeFailures() throws Exception {
        wrongPasswordTimes("nobody@sinhub.ru", 3);

        mockMvc.perform(formLogin().user("nobody@sinhub.ru").password(PASSWORD))
                .andExpect(redirectedUrl("/login?captcha"));
    }

    @Test
    void captchaChallengeIsAvailableWithoutLogin() throws Exception {
        mockMvc.perform(get("/captcha/challenge"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"signature\"")));
    }

    private MockHttpServletRequestBuilder loginWithCaptcha(String email, String password, String captchaSolution) {
        return post("/login")
                .param("username", email)
                .param("password", password)
                .param("altcha", captchaSolution)
                .with(csrf());
    }

    private void wrongPasswordTimes(String email, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            mockMvc.perform(formLogin().user(email).password("wrong password " + i))
                    .andExpect(unauthenticated());
        }
    }

    @Test
    void activeUserLogsInIsIdentifiedByIdAndGoesToHomePage() throws Exception {
        User user = saveUser("ivan@sinhub.ru", true);

        mockMvc.perform(formLogin().user("Ivan@Sinhub.ru").password(PASSWORD))
                .andExpect(authenticated().withUsername(user.getId().toString()))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void successfulLoginIsRecorded() throws Exception {
        User user = saveUser("ivan@sinhub.ru", true);

        mockMvc.perform(formLogin().user("ivan@sinhub.ru").password(PASSWORD))
                .andExpect(authenticated());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void failedLoginIsNotRecorded() throws Exception {
        User user = saveUser("ivan@sinhub.ru", true);

        mockMvc.perform(formLogin().user("ivan@sinhub.ru").password("wrong password"))
                .andExpect(unauthenticated());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastLoginAt()).isNull();
    }

    @Test
    void wrongPasswordGivesGeneralError() throws Exception {
        saveUser("ivan@sinhub.ru", true);

        mockMvc.perform(formLogin().user("ivan@sinhub.ru").password("wrong password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void blockedAccountWithCorrectPasswordGetsHint() throws Exception {
        User user = saveUser("ivan@sinhub.ru", true);
        user.block();
        userRepository.save(user);

        mockMvc.perform(formLogin().user("ivan@sinhub.ru").password(PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?blocked"));
    }

    /** Главная проверка безопасности этой задачи: без пароля статус чужой почты не узнать. */
    @Test
    void blockedAccountWithWrongPasswordLooksLikeAnyOtherError() throws Exception {
        User user = saveUser("ivan@sinhub.ru", true);
        user.block();
        userRepository.save(user);

        mockMvc.perform(formLogin().user("ivan@sinhub.ru").password("wrong password"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void unknownEmailGivesGeneralError() throws Exception {
        mockMvc.perform(formLogin().user("nobody@sinhub.ru").password(PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    /** Чужой домен с верным паролем — то же общее сообщение, что и для несуществующей почты. */
    @Test
    void addressOutsideTheCollegeDomainGivesGeneralError() throws Exception {
        saveUser("ivan@gmail.com", true);

        mockMvc.perform(formLogin().user("ivan@gmail.com").password(PASSWORD))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void homePageGreetsLoggedInUser() throws Exception {
        User user = saveUser("ivan@sinhub.ru", true);

        mockMvc.perform(get("/").with(user(user.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Здравствуйте, Иван Петров!")))
                .andExpect(content().string(containsString("ivan@sinhub.ru")));
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
        User user = User.invited(new EmailAddress(email), "Иван Петров");
        if (confirmed) {
            user.activate();
        }
        userRepository.save(user);
        credentialRepository.save(PasswordCredential.forUser(user, passwordHasher.hash(PASSWORD)));
        return user;
    }
}
