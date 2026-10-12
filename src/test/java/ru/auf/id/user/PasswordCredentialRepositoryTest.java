package ru.auf.id.user;

import ru.auf.id.TestcontainersConfiguration;
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
class PasswordCredentialRepositoryTest {

    private static final String FAKE_HASH = "$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordCredentialRepository credentialRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesPasswordUnderUsersId() {
        User user = userRepository.save(User.invited(new EmailAddress("ivan@sinhub.ru"), "Иван Петров"));
        credentialRepository.save(PasswordCredential.forUser(user, FAKE_HASH));
        entityManager.flush();
        entityManager.clear();

        PasswordCredential loaded = credentialRepository.findById(user.getId()).orElseThrow();

        assertThat(loaded.getUserId()).isEqualTo(user.getId());
        assertThat(loaded.getPasswordHash()).isEqualTo(FAKE_HASH);
        assertThat(loaded.isMustChange()).isFalse();
        assertThat(loaded.getChangedAt()).isNotNull();
    }

    @Test
    void userWithoutPasswordHasNoCredential() {
        User user = userRepository.saveAndFlush(User.invited(new EmailAddress("ivan@sinhub.ru"), "Иван Петров"));

        assertThat(credentialRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void rejectsSecondPasswordForSameUser() {
        User user = userRepository.save(User.invited(new EmailAddress("ivan@sinhub.ru"), "Иван Петров"));
        credentialRepository.saveAndFlush(PasswordCredential.forUser(user, FAKE_HASH));
        entityManager.clear();

        PasswordCredential second = PasswordCredential.forUser(user, FAKE_HASH);

        assertThatThrownBy(() -> credentialRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
