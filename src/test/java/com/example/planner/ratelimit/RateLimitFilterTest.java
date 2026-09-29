package com.example.planner.ratelimit;

import com.example.planner.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Лимиты по IP на настоящих запросах ко всему приложению. MockMvc отправляет запросы с адреса 127.0.0.1. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    @MockitoBean
    private JavaMailSender mailSender;

    @AfterEach
    void clearRedis() {
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    void twentyFirstLoginAttemptInAMinuteIsRejectedBeforePasswordCheck() throws Exception {
        for (int i = 0; i < 20; i++) {
            // Каждый раз новая почта: здесь проверяем лимит по IP, а не капчу по почте.
            mockMvc.perform(formLogin().user("guess" + i + "@mail.ru").password("guess " + i))
                    .andExpect(redirectedUrl("/login?error"));
        }

        mockMvc.perform(formLogin().user("guess21@mail.ru").password("guess 21"))
                .andExpect(redirectedUrl("/login?tooManyAttempts"))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void sixthRegistrationInAnHourGets429() throws Exception {
        for (int i = 0; i < 5; i++) {
            register("user" + i + "@mail.ru").andExpect(status().isAccepted());
        }

        register("user6@mail.ru")
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void anotherIpIsNotAffected() throws Exception {
        for (int i = 0; i < 5; i++) {
            register("user" + i + "@mail.ru");
        }

        mockMvc.perform(registration("other@mail.ru").with(request -> {
                    request.setRemoteAddr("198.51.100.1");
                    return request;
                }))
                .andExpect(status().isAccepted());
    }

    private ResultActions register(String email) throws Exception {
        return mockMvc.perform(registration(email));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registration(String email) {
        return post("/api/v1/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "fullName": "Иван Петров", "password": "correct horse"}
                        """.formatted(email));
    }
}
