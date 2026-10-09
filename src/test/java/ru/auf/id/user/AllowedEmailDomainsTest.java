package ru.auf.id.user;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllowedEmailDomainsTest {

    private final AllowedEmailDomains college = new AllowedEmailDomains(List.of(" SinHub.ru "));

    @Test
    void collegeAddressIsAllowed() {
        assertThat(college.isAllowed(new EmailAddress("ivan@sinhub.ru"))).isTrue();
    }

    @Test
    void otherDomainIsNotAllowed() {
        assertThat(college.isAllowed(new EmailAddress("ivan@gmail.com"))).isFalse();
    }

    /** Совпадение точное: поддомен и домен, лишь оканчивающийся так же, не проходят. */
    @Test
    void subdomainAndLookalikeAreNotAllowed() {
        assertThat(college.isAllowed(new EmailAddress("ivan@student.sinhub.ru"))).isFalse();
        assertThat(college.isAllowed(new EmailAddress("ivan@evilsinhub.ru"))).isFalse();
    }

    @Test
    void requireAllowedNamesTheAllowedDomain() {
        assertThatThrownBy(() -> college.requireAllowed(new EmailAddress("ivan@gmail.com")))
                .isInstanceOf(InvalidEmailException.class)
                .hasMessageContaining("sinhub.ru");
    }

    /** Пустая настройка не должна молча означать «пускать всех». */
    @Test
    void emptyListRefusesToStart() {
        assertThatThrownBy(() -> new AllowedEmailDomains(List.of(" ")))
                .isInstanceOf(IllegalStateException.class);
    }
}
