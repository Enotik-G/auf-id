package ru.auf.id.account;

import ru.auf.id.ClockConfiguration;
import ru.auf.id.TestTime;
import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.authserver.UserAuthorizationRevoker;
import ru.auf.id.provisioning.PasswordPolicy;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.PasswordCredential;
import ru.auf.id.user.PasswordCredentialRepository;
import ru.auf.id.user.PasswordHasher;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import({TestcontainersConfiguration.class, ClockConfiguration.class,
        PasswordService.class, PasswordPolicy.class, PasswordHasher.class})
class PasswordServiceTest {

    private static final String OLD_PASSWORD = "старый-пароль-студента";
    private static final String NEW_PASSWORD = "новый-пароль-студента";

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordCredentialRepository credentialRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private TestEntityManager entityManager;

    /** Сам отзыв проверен в своих тестах; здесь важно, что сервис его вызывает — и не вызывает зря. */
    @MockitoBean
    private UserAuthorizationRevoker authorizationRevoker;

    private User student;

    @BeforeEach
    void createStudentWithPassword() {
        student = User.invited(new EmailAddress("ivan@sinhub.ru"), "Иван Петров", TestTime.NOW);
        student.activate();
        userRepository.save(student);
        credentialRepository.save(PasswordCredential.forUser(student, passwordHasher.hash(OLD_PASSWORD), TestTime.NOW));
        entityManager.flush();
    }

    @Test
    void newPasswordReplacesTheOldOne() {
        passwordService.replacePassword(student.getId(), NEW_PASSWORD);

        PasswordCredential stored = reloadCredential();
        assertThat(passwordHasher.matches(NEW_PASSWORD, stored.getPasswordHash())).isTrue();
        assertThat(passwordHasher.matches(OLD_PASSWORD, stored.getPasswordHash())).isFalse();
    }

    /** По этому времени фильтр закрывает сессии, открытые до смены. */
    @Test
    void recordsWhenThePasswordWasChanged() {
        passwordService.replacePassword(student.getId(), NEW_PASSWORD);

        assertThat(reloadCredential().getChangedAt()).isAfter(TestTime.NOW);
    }

    @Test
    void revokesEverythingIssuedWithTheOldPassword() {
        passwordService.replacePassword(student.getId(), NEW_PASSWORD);

        verify(authorizationRevoker).revokeAll(student.getId());
    }

    /** Плохой пароль — ничего не меняется: ни хеш, ни выданные токены. */
    @Test
    void refusesWeakPasswordAndChangesNothing() {
        assertThatThrownBy(() -> passwordService.replacePassword(student.getId(), "короткий"))
                .isInstanceOf(WeakPasswordException.class)
                .extracting(e -> ((WeakPasswordException) e).verdict())
                .isEqualTo(PasswordPolicy.Verdict.WRONG_LENGTH);

        assertThat(passwordHasher.matches(OLD_PASSWORD, reloadCredential().getPasswordHash())).isTrue();
        verify(authorizationRevoker, never()).revokeAll(any());
    }

    private PasswordCredential reloadCredential() {
        entityManager.flush();
        entityManager.clear();
        return credentialRepository.findById(student.getId()).orElseThrow();
    }
}
