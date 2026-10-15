package ru.auf.id.user;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BootstrapAdminEmailsTest {

    private static final AllowedEmailDomains COLLEGE_DOMAIN = new AllowedEmailDomains(List.of("sinhub.ru"));

    @Test
    void normalizesAddressesAndSkipsBlankEntries() {
        BootstrapAdminEmails admins = new BootstrapAdminEmails(COLLEGE_DOMAIN, List.of(" Director@Sinhub.RU ", ""));

        assertThat(admins.all()).containsExactly(new EmailAddress("director@sinhub.ru"));
        assertThat(admins.contains(new EmailAddress("director@sinhub.ru"))).isTrue();
    }

    @Test
    void emptySettingMeansNobody() {
        assertThat(new BootstrapAdminEmails(COLLEGE_DOMAIN, List.of()).isEmpty()).isTrue();
    }

    /** Кривой адрес в настройке должен уронить приложение при старте, а не молчать. */
    @Test
    void refusesToStartWithAMalformedAddress() {
        assertThatThrownBy(() -> new BootstrapAdminEmails(COLLEGE_DOMAIN, List.of("не-похоже-на-почту")))
                .isInstanceOf(InvalidEmailException.class);
    }

    /** Адрес чужого домена никогда не войдёт, а значит, и роль не получит — тоже ошибка при старте. */
    @Test
    void refusesToStartWithAnAddressOutsideTheCollegeDomain() {
        assertThatThrownBy(() -> new BootstrapAdminEmails(COLLEGE_DOMAIN, List.of("director@gmail.com")))
                .isInstanceOf(InvalidEmailException.class);
    }
}
