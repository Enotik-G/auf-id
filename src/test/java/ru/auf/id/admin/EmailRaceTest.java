package ru.auf.id.admin;

import ru.auf.id.ClockConfiguration;
import ru.auf.id.TestTime;
import ru.auf.id.TestcontainersConfiguration;
import ru.auf.id.authserver.UserAuthorizationRevoker;
import ru.auf.id.consent.ConsentService;
import ru.auf.id.onetimetoken.OneTimeTokenService;
import ru.auf.id.provisioning.EmailAlreadyTakenException;
import ru.auf.id.provisioning.ProvisioningService;
import ru.auf.id.user.AllowedEmailDomains;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.PasswordHasher;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

/**
 * Гонка за почту: проверка «адрес свободен» и запись — два разных шага, и между ними адрес может
 * занять параллельный запрос. Тогда отказывает сама база — и ответ должен быть обычным «почта
 * занята» (409), а не 500.
 *
 * <p>Два потока в тесте не дают надёжной гонки, поэтому она имитируется: репозиторий — настоящий,
 * но проверка занятости подменена и отвечает «свободно», хотя адрес уже занят. Ровно это и видит
 * проигравший гонку запрос.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, ClockConfiguration.class,
        AdminUserService.class, ProvisioningService.class, OneTimeTokenService.class, PasswordHasher.class,
        UserAuthorizationRevoker.class, AllowedEmailDomains.class, ConsentService.class})
class EmailRaceTest {

    private static final EmailAddress TAKEN = new EmailAddress("taken@sinhub.ru");

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private ProvisioningService provisioning;

    @Autowired
    private AdminUserService adminUsers;

    @Test
    void creatingAccountWithEmailTakenMeanwhileIsEmailTaken() {
        userRepository.saveAndFlush(User.invited(TAKEN, "Успевший первым", TestTime.NOW));
        doReturn(false).when(userRepository).existsByEmail(TAKEN);

        assertThatThrownBy(() -> provisioning.invite(TAKEN, "Опоздавший", Set.of()))
                .isInstanceOf(EmailAlreadyTakenException.class);
    }

    @Test
    void changingEmailToOneTakenMeanwhileIsEmailTaken() {
        userRepository.saveAndFlush(User.invited(TAKEN, "Успевший первым", TestTime.NOW));
        User other = userRepository.saveAndFlush(User.invited(new EmailAddress("other@sinhub.ru"), "Другой", TestTime.NOW));
        doReturn(Optional.empty()).when(userRepository).findByEmail(TAKEN);

        assertThatThrownBy(() -> adminUsers.update(other.getId(), null, TAKEN.value()))
                .isInstanceOf(EmailAlreadyTakenException.class);
    }
}
