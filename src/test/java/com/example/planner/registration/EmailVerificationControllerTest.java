package com.example.planner.registration;

import com.example.planner.onetimetoken.InvalidOneTimeTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailVerificationController.class)
class EmailVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

    @Test
    void validTokenConfirmsEmail() throws Exception {
        postVerification("""
                {"token": "abc_DEF-123"}
                """)
                .andExpect(status().isNoContent());

        verify(registrationService).confirmEmail("abc_DEF-123");
    }

    @Test
    void invalidTokenGivesProblemWithHumanMessage() throws Exception {
        doThrow(new InvalidOneTimeTokenException()).when(registrationService).confirmEmail("used-token");

        postVerification("""
                {"token": "used-token"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Ссылка недействительна или устарела"));
    }

    @Test
    void blankTokenIsRejectedWithoutCallingService() throws Exception {
        postVerification("""
                {"token": ""}
                """)
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrationService);
    }

    private ResultActions postVerification(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/email-verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
