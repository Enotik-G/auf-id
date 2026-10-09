package ru.auf.id.login;

import ru.auf.id.TestTime;
import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.user.AllowedEmailDomains;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.InvalidEmailException;
import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({TestcontainersConfiguration.class, BootstrapAdminGranter.class, AllowedEmailDomains.class})
// Нарочно с другим регистром и пробелом: так проверяется нормализация адреса.
@TestPropertySource(properties = "auth.bootstrap.admin-emails=Director@Sinhub.RU, boss@sinhub.ru")
class BootstrapAdminGranterTest {

    private static final AllowedEmailDomains COLLEGE_DOMAIN = new AllowedEmailDomains(List.of("sinhub.ru"));

    @Autowired
    private BootstrapAdminGranter granter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void grantsAdminToListedAddressOnLogin() {
        User director = activeUser("director@sinhub.ru");

        granter.onLoginSuccess(loginEventFor(director.getId()));

        assertThat(reload(director).getRoles()).containsExactly(Role.ADMIN);
    }

    @Test
    void matchesAddressRegardlessOfCaseAndSpaces() {
        User boss = activeUser("boss@sinhub.ru");

        granter.onLoginSuccess(loginEventFor(boss.getId()));

        assertThat(reload(boss).hasRole(Role.ADMIN)).isTrue();
    }

    @Test
    void doesNotGrantAdminToAnyoneElse() {
        User student = activeUser("ivan@sinhub.ru");

        granter.onLoginSuccess(loginEventFor(student.getId()));

        assertThat(reload(student).getRoles()).isEmpty();
    }

    /**
     * Когда администратор уже есть, список больше ничего не выдаёт: иначе роль, снятую через
     * админку, человек получал бы обратно при каждом входе.
     */
    @Test
    void grantsNothingOnceAnAdminExists() {
        User existingAdmin = activeUser("anna@sinhub.ru");
        existingAdmin.grantRole(Role.ADMIN);
        userRepository.save(existingAdmin);
        User director = activeUser("director@sinhub.ru");

        granter.onLoginSuccess(loginEventFor(director.getId()));

        assertThat(reload(director).getRoles()).isEmpty();
    }

    /** Заблокированный админ — не администратор: если других нет, список снова срабатывает. */
    @Test
    void blockedAdminDoesNotCountAsExistingAdmin() {
        User blockedAdmin = activeUser("anna@sinhub.ru");
        blockedAdmin.grantRole(Role.ADMIN);
        blockedAdmin.block();
        userRepository.save(blockedAdmin);
        User director = activeUser("director@sinhub.ru");

        granter.onLoginSuccess(loginEventFor(director.getId()));

        assertThat(reload(director).hasRole(Role.ADMIN)).isTrue();
    }

    /** Роль добавляется, а не заменяет уже выданные. */
    @Test
    void keepsRolesTheUserAlreadyHas() {
        User director = activeUser("director@sinhub.ru");
        director.grantRole(Role.CURATOR);
        userRepository.save(director);

        granter.onLoginSuccess(loginEventFor(director.getId()));

        assertThat(reload(director).getRoles()).containsExactlyInAnyOrder(Role.ADMIN, Role.CURATOR);
    }

    @Test
    void grantingTwiceLeavesOneRole() {
        User director = activeUser("director@sinhub.ru");

        granter.onLoginSuccess(loginEventFor(director.getId()));
        granter.onLoginSuccess(loginEventFor(director.getId()));

        assertThat(reload(director).getRoles()).containsExactly(Role.ADMIN);
    }

    /**
     * То же событие публикуется, когда токен получает сервис-клиент: там имя — это {@code client_id},
     * а не id пользователя, и разбор его как UUID упал бы.
     */
    @Test
    void ignoresLoginsThatAreNotHuman() {
        AuthenticationSuccessEvent serviceLogin = new AuthenticationSuccessEvent(
                new TestingAuthenticationToken("planner-dev", "n/a", "ROLE_CLIENT"));

        granter.onLoginSuccess(serviceLogin);
        // Проверка в том, что исключения не было.
    }

    @Test
    void doesNothingWhenNobodyIsConfigured() {
        UserRepository untouched = Mockito.mock(UserRepository.class);

        new BootstrapAdminGranter(untouched, COLLEGE_DOMAIN, List.of()).onLoginSuccess(loginEventFor(UUID.randomUUID()));

        Mockito.verifyNoInteractions(untouched);
    }

    /** Кривой адрес в настройке должен уронить приложение при старте, а не молчать. */
    @Test
    void refusesToStartWithAMalformedAddress() {
        UserRepository any = Mockito.mock(UserRepository.class);

        assertThatThrownBy(() -> new BootstrapAdminGranter(any, COLLEGE_DOMAIN, List.of("не-похоже-на-почту")))
                .isInstanceOf(InvalidEmailException.class);
    }

    /** Адрес чужого домена никогда не войдёт, а значит, и роль не получит — тоже ошибка при старте. */
    @Test
    void refusesToStartWithAnAddressOutsideTheCollegeDomain() {
        UserRepository any = Mockito.mock(UserRepository.class);

        assertThatThrownBy(() -> new BootstrapAdminGranter(any, COLLEGE_DOMAIN, List.of("director@gmail.com")))
                .isInstanceOf(InvalidEmailException.class);
    }

    private User activeUser(String email) {
        User user = User.invited(new EmailAddress(email), "Кто-то", TestTime.NOW);
        user.activate();
        User saved = userRepository.save(user);
        entityManager.flush();
        return saved;
    }

    private User reload(User user) {
        entityManager.flush();
        entityManager.clear();
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private AuthenticationSuccessEvent loginEventFor(UUID userId) {
        return new AuthenticationSuccessEvent(
                new UsernamePasswordAuthenticationToken(userId.toString(), "n/a", List.of()));
    }
}
