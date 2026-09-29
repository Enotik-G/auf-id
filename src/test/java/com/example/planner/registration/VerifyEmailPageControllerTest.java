package com.example.planner.registration;

import com.example.planner.onetimetoken.InvalidOneTimeTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VerifyEmailPageController.class)
class VerifyEmailPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

    @Test
    void openingLinkShowsButtonAndDoesNotConfirmAnything() throws Exception {
        mockMvc.perform(get("/verify-email").param("token", "abc_DEF-123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Подтвердить почту")))
                .andExpect(content().string(containsString("value=\"abc_DEF-123\"")));

        verifyNoInteractions(registrationService);
    }

    @Test
    void linkWithoutTokenIsInvalid() throws Exception {
        mockMvc.perform(get("/verify-email"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ссылка недействительна")));
    }

    @Test
    void pressingButtonConfirmsAndRedirectsToSuccess() throws Exception {
        mockMvc.perform(post("/verify-email").param("token", "abc_DEF-123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/verify-email/done"));

        verify(registrationService).confirmEmail("abc_DEF-123");
    }

    @Test
    void usedTokenShowsInvalidPage() throws Exception {
        doThrow(new InvalidOneTimeTokenException()).when(registrationService).confirmEmail("used-token");

        mockMvc.perform(post("/verify-email").param("token", "used-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ссылка недействительна")));
    }

    @Test
    void successPageSaysEmailConfirmed() throws Exception {
        mockMvc.perform(get("/verify-email/done"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Почта подтверждена")))
                .andExpect(content().string(not(containsString("<form"))));
    }

    @Test
    void tokenIsHtmlEscaped() throws Exception {
        mockMvc.perform(get("/verify-email").param("token", "\"><script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }
}
