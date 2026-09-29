package com.example.planner.onetimetoken;

import com.example.planner.TestcontainersConfiguration;
import com.example.planner.user.EmailAddress;
import com.example.planner.user.User;
import com.example.planner.user.UserRepository;
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
        tokenRepository.save(OneTimeToken.issue(user, TokenPurpose.EMAIL_VERIFY, HASH, Instant.now()));
        entityManager.flush();
        entityManager.clear();

        OneTimeToken loaded = tokenRepository.findByTokenHashAndPurpose(HASH, TokenPurpose.EMAIL_VERIFY).orElseThrow();

        assertThat(loaded.getUser().getId()).isEqualTo(user.getId());
        assertThat(Duration.between(loaded.getCreatedAt(), loaded.getExpiresAt())).isEqualTo(Duration.ofHours(24));
        assertThat(loaded.getUsedAt()).isNull();
    }

    @Test
    void tokenOfOnePurposeIsNotFoundForAnother() {
        User user = saveUser();
        tokenRepository.saveAndFlush(OneTimeToken.issue(user, TokenPurpose.EMAIL_VERIFY, HASH, Instant.now()));

        assertThat(tokenRepository.findByTokenHashAndPurpose(HASH, TokenPurpose.PASSWORD_RESET)).isEmpty();
    }

    private User saveUser() {
        return userRepository.save(User.selfRegistered(new EmailAddress("ivan@mail.ru"), "Иван Петров"));
    }
}
