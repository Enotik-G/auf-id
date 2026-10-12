package ru.auf.id.onetimetoken;

import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
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
        user = userRepository.save(User.invited(new EmailAddress("ivan@sinhub.ru"), "Иван Петров"));
    }

    @Test
    void issuedTokenIsUrlSafeAndStoredOnlyAsHash() {
        String rawToken = serviceAt(ISSUED_AT).issue(user, TokenPurpose.INVITE);

        assertThat(rawToken).hasSize(43).matches("[A-Za-z0-9_-]+");

        OneTimeToken stored = tokenRepository.findAll().getFirst();
        assertThat(stored.getTokenHash()).hasSize(64).isNotEqualTo(rawToken);
        assertThat(stored.getExpiresAt()).isEqualTo(ISSUED_AT.plus(Duration.ofDays(7)));
    }

    @Test
    void twoIssuedTokensAreDifferent() {
        OneTimeTokenService service = serviceAt(ISSUED_AT);

        assertThat(service.issue(user, TokenPurpose.INVITE))
                .isNotEqualTo(service.issue(user, TokenPurpose.INVITE));
    }

    @Test
    void consumeReturnsOwnerAndMarksTokenUsed() {
        String rawToken = serviceAt(ISSUED_AT).issue(user, TokenPurpose.INVITE);
        Instant clickedAt = ISSUED_AT.plus(Duration.ofHours(1));

        User owner = serviceAt(clickedAt).consume(rawToken, TokenPurpose.INVITE);

        assertThat(owner.getId()).isEqualTo(user.getId());
        assertThat(tokenRepository.findAll().getFirst().getUsedAt()).isEqualTo(clickedAt);
    }

    @Test
    void tokenCannotBeUsedTwice() {
        String rawToken = serviceAt(ISSUED_AT).issue(user, TokenPurpose.INVITE);
        OneTimeTokenService later = serviceAt(ISSUED_AT.plus(Duration.ofHours(1)));
        later.consume(rawToken, TokenPurpose.INVITE);

        assertThatThrownBy(() -> later.consume(rawToken, TokenPurpose.INVITE))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        String rawToken = serviceAt(ISSUED_AT).issue(user, TokenPurpose.INVITE);
        OneTimeTokenService afterExpiry = serviceAt(ISSUED_AT.plus(Duration.ofDays(7)).plusSeconds(60));

        assertThatThrownBy(() -> afterExpiry.consume(rawToken, TokenPurpose.INVITE))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    @Test
    void tokenOfAnotherPurposeIsRejected() {
        String rawToken = serviceAt(ISSUED_AT).issue(user, TokenPurpose.INVITE);

        assertThatThrownBy(() -> serviceAt(ISSUED_AT).consume(rawToken, TokenPurpose.PASSWORD_RESET))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    @Test
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> serviceAt(ISSUED_AT).consume("made-up-token", TokenPurpose.INVITE))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    /** Сервис с часами, которые всегда показывают заданный момент. */
    @Test
    void revokeAllMakesLiveLinkUnusable() {
        OneTimeTokenService service = serviceAt(ISSUED_AT);
        String rawToken = service.issue(user, TokenPurpose.INVITE);

        int revoked = service.revokeAll(user, TokenPurpose.INVITE);

        assertThat(revoked).isEqualTo(1);
        assertThatThrownBy(() -> serviceAt(ISSUED_AT.plus(Duration.ofMinutes(1)))
                .consume(rawToken, TokenPurpose.INVITE))
                .isInstanceOf(InvalidOneTimeTokenException.class);
    }

    @Test
    void revokeAllTouchesOnlyTheGivenPurpose() {
        OneTimeTokenService service = serviceAt(ISSUED_AT);
        String resetToken = service.issue(user, TokenPurpose.PASSWORD_RESET);
        service.issue(user, TokenPurpose.INVITE);

        service.revokeAll(user, TokenPurpose.INVITE);

        assertThat(serviceAt(ISSUED_AT.plus(Duration.ofMinutes(1)))
                .consume(resetToken, TokenPurpose.PASSWORD_RESET).getId())
                .isEqualTo(user.getId());
    }

    @Test
    void revokeAllTouchesOnlyTheGivenUser() {
        User another = userRepository.save(User.invited(new EmailAddress("oleg@sinhub.ru"), "Олег Сидоров"));
        OneTimeTokenService service = serviceAt(ISSUED_AT);
        String othersToken = service.issue(another, TokenPurpose.INVITE);
        service.issue(user, TokenPurpose.INVITE);

        service.revokeAll(user, TokenPurpose.INVITE);

        assertThat(serviceAt(ISSUED_AT.plus(Duration.ofMinutes(1)))
                .consume(othersToken, TokenPurpose.INVITE).getId())
                .isEqualTo(another.getId());
    }

    /** Уже использованную ссылку отзывать нечего — она и так не годна. */
    @Test
    void revokeAllCountsOnlyLiveLinks() {
        OneTimeTokenService service = serviceAt(ISSUED_AT);
        String rawToken = service.issue(user, TokenPurpose.INVITE);
        serviceAt(ISSUED_AT.plus(Duration.ofMinutes(1))).consume(rawToken, TokenPurpose.INVITE);

        assertThat(service.revokeAll(user, TokenPurpose.INVITE)).isZero();
    }

    @Test
    void revokeAllOnUserWithoutLinksChangesNothing() {
        assertThat(serviceAt(ISSUED_AT).revokeAll(user, TokenPurpose.INVITE)).isZero();
    }

    private OneTimeTokenService serviceAt(Instant now) {
        return new OneTimeTokenService(tokenRepository, Clock.fixed(now, ZoneOffset.UTC));
    }
}
