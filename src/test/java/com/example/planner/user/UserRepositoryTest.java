package com.example.planner.user;

import com.example.planner.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesAndLoadsSelfRegisteredUser() {
        User saved = userRepository.save(User.selfRegistered(new EmailAddress("ivan@mail.ru"), "Иван Петров"));
        entityManager.flush();
        entityManager.clear();

        User loaded = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getEmail()).isEqualTo(new EmailAddress("ivan@mail.ru"));
        assertThat(loaded.getFullName()).isEqualTo("Иван Петров");
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(loaded.isEmailVerified()).isFalse();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getLastLoginAt()).isNull();
    }

    @Test
    void findsUserByEmailTypedDifferently() {
        userRepository.save(User.selfRegistered(new EmailAddress("ivan@mail.ru"), "Иван Петров"));
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findByEmail(new EmailAddress("  IVAN@Mail.RU "))).isPresent();
        assertThat(userRepository.existsByEmail(new EmailAddress("Ivan@mail.ru"))).isTrue();
        assertThat(userRepository.existsByEmail(new EmailAddress("petr@mail.ru"))).isFalse();
    }

    @Test
    void rejectsSecondUserWithSameEmail() {
        userRepository.saveAndFlush(User.selfRegistered(new EmailAddress("ivan@mail.ru"), "Иван Петров"));

        User duplicate = User.selfRegistered(new EmailAddress("IVAN@mail.ru"), "Другой Иван");

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
