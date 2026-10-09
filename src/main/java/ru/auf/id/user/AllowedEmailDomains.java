package ru.auf.id.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Почты каких доменов допускаются в AUF ID — настройка {@code auth.allowed-email-domains}.
 *
 * <p>Решение 2026-10-10: входят только адреса колледжа ({@code @sinhub.ru}). Личные почты студентов
 * (для рассылок) появятся в отдельном сервисе экосистемы, к входу они отношения не имеют.
 *
 * <p>Проверяется в трёх местах: при создании учётки и смене почты (админ получит 400), при входе
 * (чужой домен ведёт себя как несуществующая почта) и при старте — для адресов первых
 * администраторов.
 *
 * <p>Совпадение домена точное: {@code student.sinhub.ru} не подходит под {@code sinhub.ru}, его
 * нужно перечислить отдельно. Так в список не попадёт ничего, чего не вписали явно.
 */
@Component
public class AllowedEmailDomains {

    private final Set<String> domains;

    /**
     * Пустой список — ошибка при старте, а не «пускать всех»: опечатка в настройке не должна
     * молча открыть вход любым адресам.
     */
    public AllowedEmailDomains(@Value("${auth.allowed-email-domains}") List<String> domains) {
        this.domains = domains.stream()
                .map(domain -> domain.strip().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (this.domains.isEmpty()) {
            throw new IllegalStateException("auth.allowed-email-domains пуст: вход был бы закрыт для всех");
        }
    }

    public boolean isAllowed(EmailAddress email) {
        return domains.contains(email.domain());
    }

    /**
     * @throws InvalidEmailException если домен не из списка; сообщение можно показать админу как есть
     */
    public void requireAllowed(EmailAddress email) {
        if (!isAllowed(email)) {
            throw new InvalidEmailException("Допускаются только адреса в домене " + String.join(", ", domains));
        }
    }
}
