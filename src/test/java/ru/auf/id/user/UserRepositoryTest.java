package ru.auf.id.user;

import ru.auf.id.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

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
        User saved = userRepository.save(User.invited(new EmailAddress("ivan@sinhub.ru"), "Иван Петров"));
        entityManager.flush();
        entityManager.clear();

        User loaded = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getEmail()).isEqualTo(new EmailAddress("ivan@sinhub.ru"));
        assertThat(loaded.getFullName()).isEqualTo("Иван Петров");
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.INVITED);
        assertThat(loaded.isEmailVerified()).isFalse();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getLastLoginAt()).isNull();
    }

    @Test
    void findsUserByEmailTypedDifferently() {
        userRepository.save(User.invited(new EmailAddress("ivan@sinhub.ru"), "Иван Петров"));
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findByEmail(new EmailAddress("  IVAN@Sinhub.RU "))).isPresent();
        assertThat(userRepository.existsByEmail(new EmailAddress("Ivan@sinhub.ru"))).isTrue();
        assertThat(userRepository.existsByEmail(new EmailAddress("petr@sinhub.ru"))).isFalse();
    }

    @Test
    void rejectsSecondUserWithSameEmail() {
        userRepository.saveAndFlush(User.invited(new EmailAddress("ivan@sinhub.ru"), "Иван Петров"));

        User duplicate = User.invited(new EmailAddress("IVAN@sinhub.ru"), "Другой Иван");

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rolesSurviveSaveAndLoad() {
        User user = User.invited(new EmailAddress("curator@sinhub.ru"), "Олег Сидоров");
        user.grantRole(Role.CURATOR);
        user.grantRole(Role.STUDENT);
        User saved = userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        User loaded = userRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getRoles()).containsExactlyInAnyOrder(Role.CURATOR, Role.STUDENT);
    }

    @Test
    void revokedRoleDisappearsFromDatabase() {
        User user = User.invited(new EmailAddress("admin@sinhub.ru"), "Анна Петрова");
        user.grantRole(Role.ADMIN);
        User saved = userRepository.save(user);
        entityManager.flush();
        entityManager.clear();

        User loaded = userRepository.findById(saved.getId()).orElseThrow();
        loaded.revokeRole(Role.ADMIN);
        userRepository.save(loaded);
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findById(saved.getId()).orElseThrow().getRoles()).isEmpty();
    }

    /** Роли читаются сразу с пользователем, поэтому их видно и за пределами транзакции репозитория. */
    @Test
    void rolesAreLoadedEagerly() {
        User user = User.invited(new EmailAddress("eager@sinhub.ru"), "Иван Иванов");
        user.grantRole(Role.STUDENT);
        UUID id = userRepository.save(user).getId();
        entityManager.flush();
        entityManager.clear();

        User detached = userRepository.findById(id).orElseThrow();
        entityManager.clear();

        assertThat(detached.getRoles()).containsExactly(Role.STUDENT);
    }
}
