package ru.auf.id.login;

import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.PasswordCredential;
import ru.auf.id.user.PasswordCredentialRepository;
import ru.auf.id.user.PasswordHasher;
import ru.auf.id.user.Role;
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

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Уже открытая сессия подчиняется изменениям в БД сразу, а не после повторного входа
 * ({@link SessionUserRevalidationFilter}).
 *
 * <p>Все изменения — прямо в БД, мимо {@code AdminUserService}: проверяется именно фильтр.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SessionUserRevalidationTest {

    private static final String EMAIL = "admin@sinhub.ru";
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

    private User admin;

    @BeforeEach
    void createAdmin() {
        admin = User.invited(new EmailAddress(EMAIL), "Анна Админова");
        admin.activate();
        admin.grantRole(Role.ADMIN);
        userRepository.save(admin);
        credentialRepository.save(PasswordCredential.forUser(admin, passwordHasher.hash(PASSWORD)));
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void activeUserKeepsWorkingInTheSameSession() throws Exception {
        MockHttpSession session = logIn();

        mockMvc.perform(get("/api/v1/admin/users/{id}", admin.getId()).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/users/{id}", admin.getId()).session(session))
                .andExpect(status().isOk());
    }

    /** Заблокированного выпускает из сессии на следующем же запросе: браузер — на страницу входа. */
    @Test
    void blockedUserIsLoggedOutOnTheNextRequest() throws Exception {
        MockHttpSession session = logIn();
        changeInDatabase(User::block);

        mockMvc.perform(get("/").session(session))
                .andExpect(redirectedUrl("/login"));
        assertThat(session.isInvalid()).isTrue();
    }

    /**
     * Заблокированный администратор не может разблокировать сам себя из старой сессии — раньше
     * роль ADMIN жила в сессии до её конца.
     */
    @Test
    void blockedAdminCannotUnblockThemselves() throws Exception {
        MockHttpSession session = logIn();
        changeInDatabase(User::block);

        mockMvc.perform(post("/api/v1/admin/users/{id}/unblock", admin.getId()).session(session).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    /** Снятая роль перестаёт действовать сразу, без повторного входа. */
    @Test
    void revokedAdminRoleStopsWorkingImmediately() throws Exception {
        MockHttpSession session = logIn();
        changeInDatabase(user -> user.revokeRole(Role.ADMIN));

        mockMvc.perform(get("/api/v1/admin/users/{id}", admin.getId()).session(session))
                .andExpect(status().isForbidden());
    }

    /** И наоборот: выданная роль начинает действовать без повторного входа. */
    @Test
    void grantedAdminRoleWorksWithoutLoggingInAgain() throws Exception {
        changeInDatabase(user -> user.revokeRole(Role.ADMIN));
        MockHttpSession session = logIn();
        mockMvc.perform(get("/api/v1/admin/users/{id}", admin.getId()).session(session))
                .andExpect(status().isForbidden());

        changeInDatabase(user -> user.grantRole(Role.ADMIN));

        mockMvc.perform(get("/api/v1/admin/users/{id}", admin.getId()).session(session))
                .andExpect(status().isOk());
    }

    private MockHttpSession logIn() throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin().user(EMAIL).password(PASSWORD))
                .andExpect(authenticated())
                .andReturn().getRequest().getSession(false);
    }

    private void changeInDatabase(Consumer<User> change) {
        User stored = userRepository.findById(admin.getId()).orElseThrow();
        change.accept(stored);
        userRepository.save(stored);
    }
}
