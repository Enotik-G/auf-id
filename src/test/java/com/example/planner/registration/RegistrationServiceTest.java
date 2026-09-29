package com.example.planner.registration;

import com.example.planner.ClockConfiguration;
import com.example.planner.TestcontainersConfiguration;
import com.example.planner.onetimetoken.OneTimeTokenRepository;
import com.example.planner.onetimetoken.OneTimeTokenService;
import com.example.planner.onetimetoken.TokenPurpose;
import com.example.planner.user.EmailAddress;
import com.example.planner.user.PasswordCredentialRepository;
import com.example.planner.user.PasswordHasher;
import com.example.planner.user.User;
import com.example.planner.user.UserRepository;
import com.example.planner.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "auth.password.pepper=test-pepper-only-for-tests-0123456789")
@Import({TestcontainersConfiguration.class, ClockConfiguration.class,
        RegistrationService.class, OneTimeTokenService.class, PasswordHasher.class})
@RecordApplicationEvents
class RegistrationServiceTest {

    private static final EmailAddress EMAIL = new EmailAddress("ivan@mail.ru");

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordCredentialRepository credentialRepository;

    @Autowired
    private OneTimeTokenRepository tokenRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ApplicationEvents events;

    @Test
    void createsPendingUserWithPasswordAndVerificationToken() {
        registrationService.register(EMAIL, "Иван Петров", "correct horse battery staple");

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(user.isEmailVerified()).isFalse();

        String storedHash = credentialRepository.findById(user.getId()).orElseThrow().getPasswordHash();
        assertThat(passwordHasher.matches("correct horse battery staple", storedHash)).isTrue();

        assertThat(tokenRepository.findAll())
                .singleElement()
                .satisfies(token -> assertThat(token.getPurpose()).isEqualTo(TokenPurpose.EMAIL_VERIFY));
    }

    @Test
    void requestsVerificationEmailWithTheIssuedToken() {
        registrationService.register(EMAIL, "Иван Петров", "correct horse battery staple");

        VerificationEmailRequested event = events.stream(VerificationEmailRequested.class)
                .findFirst().orElseThrow();
        assertThat(event.email()).isEqualTo(EMAIL);
        assertThat(event.fullName()).isEqualTo("Иван Петров");
        assertThat(event.rawToken()).hasSize(43);
    }

    @Test
    void takenEmailIsSilentlyIgnored() {
        registrationService.register(EMAIL, "Иван Петров", "correct horse battery staple");

        registrationService.register(new EmailAddress("IVAN@mail.ru"), "Самозванец", "another password 123");

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getFullName()).isEqualTo("Иван Петров");
        assertThat(events.stream(VerificationEmailRequested.class)).hasSize(1);
    }
}
