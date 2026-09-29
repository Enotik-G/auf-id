package com.example.planner.registration;

import com.example.planner.SecurityConfiguration;
import com.example.planner.user.EmailAddress;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegistrationController.class)
@Import(SecurityConfiguration.class)
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

    @Test
    void acceptsValidRegistrationAndPassesNormalizedData() throws Exception {
        postRegistration("""
                {"email": " Ivan@Mail.RU ", "fullName": "  Иван Петров ", "password": "correct horse"}
                """)
                .andExpect(status().isAccepted());

        verify(registrationService).register(new EmailAddress("ivan@mail.ru"), "Иван Петров", "correct horse");
    }

    @Test
    void concurrentDuplicateLooksLikeSuccess() throws Exception {
        doThrow(new DataIntegrityViolationException("users_email_key"))
                .when(registrationService).register(any(), anyString(), anyString());

        postRegistration("""
                {"email": "ivan@mail.ru", "fullName": "Иван Петров", "password": "correct horse"}
                """)
                .andExpect(status().isAccepted());
    }

    @Test
    void rejectsShortPassword() throws Exception {
        postRegistration("""
                {"email": "ivan@mail.ru", "fullName": "Иван Петров", "password": "1234567"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(registrationService);
    }

    @Test
    void rejectsBlankFullName() throws Exception {
        postRegistration("""
                {"email": "ivan@mail.ru", "fullName": "   ", "password": "correct horse"}
                """)
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrationService);
    }

    @Test
    void rejectsMalformedEmail() throws Exception {
        postRegistration("""
                {"email": "ivan.mail.ru", "fullName": "Иван Петров", "password": "correct horse"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(registrationService);
    }

    private org.springframework.test.web.servlet.ResultActions postRegistration(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
