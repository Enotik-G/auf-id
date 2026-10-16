package ru.auf.id.webui;

import ru.auf.id.SecurityConfiguration;
import ru.auf.id.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebInterfaceController.class)
@Import(SecurityConfiguration.class)
class WebInterfaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** Нужен фильтру перепроверки сессии, который ставит SecurityConfiguration. */
    @MockitoBean
    private UserRepository userRepository;

    /** Браузер без входа отправляется на страницу входа — а после неё вернётся сюда. */
    @Test
    void anonymousIsSentToLoginPage() throws Exception {
        mockMvc.perform(get("/account"))
                .andExpect(redirectedUrl("/login"));
    }

    /** На любой адрес интерфейса — та же страница: что показать, решает React. */
    @Test
    @WithMockUser(roles = "STUDENT")
    void anyAccountAddressServesTheSamePage() throws Exception {
        mockMvc.perform(get("/account")).andExpect(forwardedUrl("/index.html"));
        mockMvc.perform(get("/account/password")).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotOpenAdminPanel() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminOpensAdminPanel() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(forwardedUrl("/index.html"));
        mockMvc.perform(get("/admin/users/0199bc42-8f31-7a1e-9c55-2b7d4e6a1f90")).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void webInterfaceIsServedWithContentSecurityPolicy() throws Exception {
        mockMvc.perform(get("/account"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));
    }

    /** Файлы интерфейса — код без данных, их грузит и ещё не вошедший браузер. */
    @Test
    void assetsAreNotBehindLogin() throws Exception {
        int status = mockMvc.perform(get("/assets/index.js")).andReturn().getResponse().getStatus();

        assertThat(status).isNotEqualTo(302);
    }
}
