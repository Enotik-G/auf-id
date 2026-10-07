package ru.auf.id.ratelimit;

import ru.auf.id.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** Лимиты по IP на настоящих запросах ко всему приложению. MockMvc отправляет запросы с адреса 127.0.0.1. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

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

    /** Своё ведро на каждый адрес: исчерпавший лимит не мешает остальным. */
    @Test
    void anotherIpIsNotAffected() throws Exception {
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(formLogin().user("guess" + i + "@mail.ru").password("guess " + i));
        }

        // formLogin() даёт специализированный билдер без .with(...), поэтому запрос собираем сами:
        // имена полей у него те же, что по умолчанию у Spring Security.
        mockMvc.perform(post("/login")
                        .param("username", "other@mail.ru")
                        .param("password", "guess")
                        .with(csrf())
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.1");
                            return request;
                        }))
                .andExpect(redirectedUrl("/login?error"));
    }
}
