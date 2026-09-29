package com.example.planner.registration;

import com.example.planner.onetimetoken.TokenPurpose;
import com.example.planner.user.EmailAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

/** Отправляет письмо «подтвердите почту» со ссылкой, в которой лежит одноразовый токен. */
@Component
public class VerificationEmailSender {

    static final String VERIFY_PATH = "/verify-email";

    private final JavaMailSender mailSender;
    private final String from;
    private final String publicUrl;

    public VerificationEmailSender(
            JavaMailSender mailSender,
            @Value("${auth.mail.from}") String from,
            @Value("${auth.public-url}") String publicUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.publicUrl = publicUrl;
    }

    /**
     * Письмо уходит только после успешного коммита регистрации: если транзакция откатилась,
     * письма со ссылкой на несуществующий токен не будет, а соединение с БД
     * не держится, пока идёт медленный разговор с почтовым сервером.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationEmailRequested(VerificationEmailRequested event) {
        send(event.email(), event.fullName(), event.rawToken());
    }

    public void send(EmailAddress to, String fullName, String rawToken) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to.value());
        message.setSubject("Подтвердите почту");
        message.setText(body(fullName, verificationLink(rawToken)));
        mailSender.send(message);
    }

    private String verificationLink(String rawToken) {
        return UriComponentsBuilder.fromUriString(publicUrl)
                .path(VERIFY_PATH)
                .queryParam("token", rawToken)
                .toUriString();
    }

    private static String body(String fullName, String link) {
        long hours = TokenPurpose.EMAIL_VERIFY.lifetime().toHours();
        return """
                Здравствуйте, %s!

                Чтобы завершить регистрацию, перейдите по ссылке:
                %s

                Ссылка действует %d часа и сработает один раз.
                Если вы не регистрировались — просто проигнорируйте это письмо.
                """.formatted(fullName, link, hours);
    }
}
