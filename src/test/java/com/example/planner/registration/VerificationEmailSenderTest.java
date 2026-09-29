package com.example.planner.registration;

import com.example.planner.user.EmailAddress;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VerificationEmailSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final VerificationEmailSender sender =
            new VerificationEmailSender(mailSender, "no-reply@auth.local", "https://auth.example.org");

    @Test
    void sendsLetterWithVerificationLink() {
        sender.send(new EmailAddress("ivan@mail.ru"), "Иван Петров", "abc_DEF-123");

        SimpleMailMessage message = sentMessage();
        assertThat(message.getFrom()).isEqualTo("no-reply@auth.local");
        assertThat(message.getTo()).containsExactly("ivan@mail.ru");
        assertThat(message.getSubject()).isEqualTo("Подтвердите почту");
        assertThat(message.getText())
                .contains("Иван Петров")
                .contains("https://auth.example.org/verify-email?token=abc_DEF-123")
                .contains("24 часа");
    }

    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}
