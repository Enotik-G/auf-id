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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Настройка CSRF как на сервере: cookie {@code __Host-XSRF-TOKEN}. Такую cookie соседний поддомен
 * колледжа не может подбросить (cookie tossing), поэтому сравнение «cookie = заголовок» снова
 * что-то доказывает. Свежий контекст — по той же причине, что в {@link AdminApiCsrfTest}.
 */
@WebMvcTest(AdminUserController.class)
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = "auth.csrf.cookie-name=__Host-XSRF-TOKEN")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class HostPrefixedCsrfCookieTest {

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

    /** Без Secure и Path=/ браузер cookie с префиксом __Host- не примет вовсе. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void cookieMeetsHostPrefixRequirements() throws Exception {
        when(adminUsers.get(USER_ID)).thenReturn(User.invited(new EmailAddress("ivan@sinhub.ru"), "Иван", TestTime.NOW));

        Cookie cookie = mockMvc.perform(get("/api/v1/admin/users/" + USER_ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("__Host-XSRF-TOKEN");

        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    /** Подброшенная поддоменом cookie со старым именем больше ничего не значит. */
    @Test
    @WithMockUser(roles = "ADMIN")
    void tossedCookieWithPlainNameIsIgnored() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/unblock")
                        .cookie(new Cookie("XSRF-TOKEN", "attacker-value"))
                        .header("X-XSRF-TOKEN", "attacker-value"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void tokenFromHostPrefixedCookieIsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/" + USER_ID + "/unblock")
                        .cookie(new Cookie("__Host-XSRF-TOKEN", "token-from-cookie"))
                        .header("X-XSRF-TOKEN", "token-from-cookie"))
                .andExpect(status().isNoContent());

        verify(adminUsers).unblock(USER_ID);
    }

    /** На сервере (cookie сессии только по HTTPS) забыть про __Host- нельзя — приложение не стартует. */
    @Test
    void plainCookieNameIsRefusedWhenCookiesAreSecure() {
        assertThatThrownBy(() -> SecurityConfiguration.csrfTokenRepository("XSRF-TOKEN", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("__Host-XSRF-TOKEN");
    }
}
