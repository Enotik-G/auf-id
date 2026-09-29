package com.example.planner.onetimetoken;

import com.example.planner.TestcontainersConfiguration;
import com.example.planner.user.EmailAddress;
import com.example.planner.user.User;
import com.example.planner.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class OneTimeTokenServiceTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-09-01T10:00:00Z");

    @Autowired
    private OneTimeTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void createUser() {
        user = userRepository.save(User.selfRegistered(new EmailAddress("ivan@mail.ru"), "Иван Петров"));
    }

    @Test
    void issuedTokenIsUrlSafeAndStoredOnlyAsHash() {
        String rawToken = serviceAt(ISSUED_AT).issue(user, TokenPurpose.EMAIL_VERIFY);

        assertThat(rawToken).hasSize(43).matches("[A-Za-z0-9_-]+");

        OneTimeToken stored = tokenRepository.findAll().getFirst();
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(rawToken);
        assertThat(stored.getExpiresAt()).isEqualTo(ISSUED_AT.plus(Duration.ofHours(24)));
    }

    @Test
    void twoIssuedTokensAreDifferent() {
        OneTimeTokenService service = serviceAt(ISSUED_AT);

        assertThat(service.issue(user, TokenPurpose.EMAIL_VERIFY))
                .isNotEqualTo(service.issue(user, TokenPurpose.EMAIL_VERIFY));
    }

    @Test
    void consumeReturnsOwnerAndMarksTokenUsed() {
        String rawToken = serviceAt(ISSUED_AT).issue(user, TokenPurpose.EMAIL_VERIFY);
        Instant clickedAt = ISSUED_AT.plus(Duration.ofHours(1));

        User owner = serviceAt(clickedAt).consume(rawToken, TokenPurpose.EMAIL_VERIFY);

        assertThat(owner.getId()).isEqualTo(user.getId());
        assertThat(tokenRepository.findAll().getFirst().getUsedAt()).isEqualTo(clickedAt);
    }

    @Test
    void tokenCannotBeUsedTwice() {
        String rawToken = serviceAt(ISSUED_AT).issue(user, TokenPurpose.EMAIL_VERIFY);
        OneTimeTokenService later = serviceAt(ISSUED_AT.plus(Duration.ofHours(1)));
        later.consume(rawToken, TokenPurpose.EMAIL_VERIFY);

        assertThatThrownBy(() -> later.consume(rawToken, TokenPurpose.EMAIL_VERIFY))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        String rawToken = serviceAt(ISSUED_AT).issue(user, TokenPurpose.EMAIL_VERIFY);
        OneTimeTokenService dayAndMinuteLater = serviceAt(ISSUED_AT.plus(Duration.ofHours(24)).plusSeconds(60));

        assertThatThrownBy(() -> dayAndMinuteLater.consume(rawToken, TokenPurpose.EMAIL_VERIFY))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    @Test
    void tokenOfAnotherPurposeIsRejected() {
        String rawToken = serviceAt(ISSUED_AT).issue(user, TokenPurpose.EMAIL_VERIFY);

        assertThatThrownBy(() -> serviceAt(ISSUED_AT).consume(rawToken, TokenPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    @Test
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> serviceAt(ISSUED_AT).consume("made-up-token", TokenPurpose.EMAIL_VERIFY))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    /** Сервис с часами, которые всегда показывают заданный момент. */
    private OneTimeTokenService serviceAt(Instant now) {
        return new OneTimeTokenService(tokenRepository, Clock.fixed(now, ZoneOffset.UTC));
    }
}
