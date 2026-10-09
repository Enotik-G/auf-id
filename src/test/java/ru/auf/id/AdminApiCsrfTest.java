package ru.auf.id;

import ru.auf.id.admin.AdminUserController;
import ru.auf.id.admin.AdminUserService;
import ru.auf.id.provisioning.ProvisioningService;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Защита админки от CSRF так, как ею пользуется браузер: ответ ставит cookie {@code XSRF-TOKEN},
 * скрипт на нашей странице (Swagger UI, админ-панель) читает её и возвращает значение заголовком
 * {@code X-XSRF-TOKEN}. Чужой сайт прочитать cookie нашего адреса не может — в этом и защита.
 *
 * <p>Отдельный класс со своим, свежим контекстом ({@code @DirtiesContext}) — не прихоть. Помощник
 * {@code .with(csrf())} из spring-security-test подменяет хранилище токенов прямо внутри фильтра
 * CSRF, и подмена остаётся в закешированном контексте для следующих тестов: после неё cookie
 * больше не ставится. Здесь {@code csrf()} не используется ни разу.
 */
@WebMvcTest(AdminUserController.class)
@Import(SecurityConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AdminApiCsrfTest {

    private static final UUID USER_ID = UUID.fromString("0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProvisioningService provisioning;

    @MockitoBean
    private AdminUserService adminUsers;

    /** Нужен фильтру перепроверки сессии, который ставит SecurityConfiguration. */
    @MockitoBean
    private UserRepository userRepository;

    /**
     * Без {@link CsrfCookieFilter} cookie на ответе JSON не было бы, и первый же POST из Swagger
     * получал бы 403: Spring создаёт токен лениво, только когда к нему обращаются.
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void jsonResponseSetsCsrfCookieReadableByScripts() throws Exception {
        when(adminUsers.get(USER_ID)).thenReturn(User.invited(new EmailAddress("ivan@sinhub.ru"), "Иван Петров", TestTime.NOW));

        Cookie csrfCookie = mockMvc.perform(get("/api/v1/admin/users/" + USER_ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getValue()).isNotBlank();
        // Скрипт на нашей странице должен её прочитать, иначе не положит значение в заголовок.
        assertThat(csrfCookie.isHttpOnly()).isFalse();
    }

    /** Значение из cookie, возвращённое заголовком, принимается. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void tokenFromCookieIsAcceptedInHeader() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/unblock")
                        .cookie(new Cookie("XSRF-TOKEN", "token-from-cookie"))
                        .header("X-XSRF-TOKEN", "token-from-cookie"))
                .andExpect(status().isNoContent());

        verify(adminUsers).unblock(USER_ID);
    }

    /** Одной cookie мало: её браузер приложит и к запросу с чужой страницы. Нужен заголовок. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void cookieWithoutHeaderIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/unblock")
                        .cookie(new Cookie("XSRF-TOKEN", "token-from-cookie")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminUsers);
    }
}
