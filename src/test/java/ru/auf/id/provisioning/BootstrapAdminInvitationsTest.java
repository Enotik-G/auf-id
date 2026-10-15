package ru.auf.id.provisioning;

import ru.auf.id.ClockConfiguration;
import ru.auf.id.TestTime;
import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.consent.ConsentService;
import ru.auf.id.onetimetoken.InvalidOneTimeTokenException;
import ru.auf.id.onetimetoken.OneTimeTokenService;
import ru.auf.id.user.AllowedEmailDomains;
import ru.auf.id.user.BootstrapAdminEmails;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.PasswordHasher;
import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import ru.auf.id.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Метод вызывается напрямую — как его вызывает Spring после запуска.
 *
 * <p>Ловушка: {@code ApplicationReadyEvent} публикуется и при запуске тестового контекста, то есть
 * механизм уже отработал на пустой базе — вне тестовой транзакции, с настоящим коммитом. Поэтому
 * перед каждым тестом учётки стираются ({@link #startFromEmptyDatabase()}).
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, AllowedEmailDomains.class, ClockConfiguration.class,
        ProvisioningService.class, OneTimeTokenService.class, PasswordHasher.class, ConsentService.class,
        BootstrapAdminEmails.class, ActivationLinks.class, BootstrapAdminInvitations.class})
@TestPropertySource(properties = "auth.bootstrap.admin-emails=director@sinhub.ru")
@ExtendWith(OutputCaptureExtension.class)
class BootstrapAdminInvitationsTest {

    private static final EmailAddress DIRECTOR = new EmailAddress("director@sinhub.ru");

    /**
     * Ссылка из лога: всё после {@code token=} до конца слова. Перехват вывода начинается до запуска
     * контекста, поэтому в нём есть и ссылка, напечатанная при старте, — проверки смотрят только на
     * то, что напечатано после вызова, а токен берут последний.
     */
    private static final Pattern LINK_TOKEN = Pattern.compile("/activate\\?token=(\\S+)");

    private static final String CLIENT_IP = "203.0.113.5";

    @Autowired
    private BootstrapAdminInvitations invitations;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProvisioningService provisioning;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Удаление идёт внутри транзакции теста и откатывается вместе с ней. Токены, пароли, роли и
     * согласия уходят следом сами: их внешние ключи на {@code users} объявлены {@code ON DELETE CASCADE}.
     */
    @BeforeEach
    void startFromEmptyDatabase() {
        jdbc.update("DELETE FROM users");
    }

    @Test
    void createsInvitedAccountWithoutRolesOnEmptyDatabase(CapturedOutput output) {
        int logBefore = output.getOut().length();
        invitations.inviteFirstAdmins();

        User director = userRepository.findByEmail(DIRECTOR).orElseThrow();
        assertThat(director.getStatus()).isEqualTo(UserStatus.INVITED);
        assertThat(director.getFullName()).isEqualTo(BootstrapAdminInvitations.PLACEHOLDER_FULL_NAME);
        // Роль выдаст BootstrapAdminGranter при входе — иначе неактивированная учётка
        // уже считалась бы администратором.
        assertThat(director.getRoles()).isEmpty();
        assertThat(output.getOut().substring(logBefore))
                .contains("director@sinhub.ru", "http://localhost:8080/activate?token=");
    }

    /** Ссылка из лога настоящая: по ней учётка активируется. */
    @Test
    void loggedLinkActivatesTheAccount(CapturedOutput output) {
        invitations.inviteFirstAdmins();

        provisioning.activate(lastLoggedToken(output), "очень-длинный-пароль-директора", CLIENT_IP);

        assertThat(userRepository.findByEmail(DIRECTOR).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    /** Перезапуск до активации: потерянная ссылка заменяется новой, старая больше не работает. */
    @Test
    void restartBeforeActivationReplacesTheLink(CapturedOutput output) {
        invitations.inviteFirstAdmins();
        String firstToken = lastLoggedToken(output);

        invitations.inviteFirstAdmins();
        String secondToken = lastLoggedToken(output);

        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThat(userRepository.findAll()).hasSize(1);
        assertThatThrownBy(() -> provisioning.activate(firstToken, "очень-длинный-пароль-директора", CLIENT_IP))
                .isInstanceOf(InvalidOneTimeTokenException.class);
        provisioning.activate(secondToken, "очень-длинный-пароль-директора", CLIENT_IP);
    }

    /** Активированную учётку не трогаем: роль ей выдаст вход, ссылка ей не нужна. */
    @Test
    void leavesActivatedAccountAlone(CapturedOutput output) {
        User director = User.invited(DIRECTOR, "Анна Директорова", TestTime.NOW);
        director.activate();
        userRepository.save(director);

        int logBefore = output.getOut().length();
        invitations.inviteFirstAdmins();

        assertThat(userRepository.findByEmail(DIRECTOR).orElseThrow().getFullName()).isEqualTo("Анна Директорова");
        assertThat(output.getOut().substring(logBefore)).doesNotContain("/activate?token=");
    }

    /** Администратор уже есть — механизм молчит: на сервере он срабатывает только при первом запуске. */
    @Test
    void doesNothingOnceAnAdminExists(CapturedOutput output) {
        User admin = User.invited(new EmailAddress("anna@sinhub.ru"), "Анна", TestTime.NOW);
        admin.activate();
        admin.grantRole(Role.ADMIN);
        userRepository.save(admin);

        int logBefore = output.getOut().length();
        invitations.inviteFirstAdmins();

        assertThat(userRepository.findByEmail(DIRECTOR)).isEmpty();
        assertThat(output.getOut().substring(logBefore)).doesNotContain("/activate?token=");
    }

    private static String lastLoggedToken(CapturedOutput output) {
        Matcher matcher = LINK_TOKEN.matcher(output.getOut());
        List<String> tokens = matcher.results().map(result -> result.group(1)).toList();
        assertThat(tokens).as("ссылка активации в логе").isNotEmpty();
        return tokens.getLast();
    }
}
