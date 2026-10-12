package ru.auf.id.consent;

import ru.auf.id.SecurityConfiguration;
import ru.auf.id.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsentPageController.class)
@Import(SecurityConfiguration.class)
class ConsentPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsentService consentService;

    /** Нужен фильтру перепроверки сессии, который ставит SecurityConfiguration. */
    @MockitoBean
    private UserRepository userRepository;

    /** Текст читают до активации — значит, без входа. И на странице видно, какая это версия. */
    @Test
    void personalDataConsentIsOpenWithoutLoginAndShowsVersion() throws Exception {
        when(consentService.personalDataVersion()).thenReturn("draft-2026-10-10");

        mockMvc.perform(get("/consent/personal-data"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Согласие на обработку персональных данных")))
                .andExpect(content().string(containsString("draft-2026-10-10")));
    }
}
