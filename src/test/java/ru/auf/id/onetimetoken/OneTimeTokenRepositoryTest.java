package ru.auf.id.onetimetoken;

import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class OneTimeTokenRepositoryTest {

    private static final String HASH = "a".repeat(64);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OneTimeTokenRepository tokenRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesTokenAndFindsItByHashAndPurpose() {
        User user = saveUser();
        tokenRepository.save(OneTimeToken.issue(user, TokenPurpose.INVITE, HASH, Instant.now()));
        entityManager.flush();
        entityManager.clear();

        OneTimeToken loaded = tokenRepository.findByTokenHashAndPurpose(HASH, TokenPurpose.INVITE).orElseThrow();

        assertThat(loaded.getUser().getId()).isEqualTo(user.getId());
        assertThat(Duration.between(loaded.getCreatedAt(), loaded.getExpiresAt())).isEqualTo(Duration.ofDays(7));
        assertThat(loaded.getUsedAt()).isNull();
    }

    @Test
    void tokenOfOnePurposeIsNotFoundForAnother() {
        User user = saveUser();
        tokenRepository.saveAndFlush(OneTimeToken.issue(user, TokenPurpose.INVITE, HASH, Instant.now()));

        assertThat(tokenRepository.findByTokenHashAndPurpose(HASH, TokenPurpose.PASSWORD_RESET)).isEmpty();
    }

    private User saveUser() {
        return userRepository.save(User.invited(new EmailAddress("ivan@sinhub.ru"), "Иван Петров"));
    }
}
