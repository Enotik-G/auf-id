package ru.auf.id.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Почты первых администраторов — настройка {@code auth.bootstrap.admin-emails}.
 *
 * <p>Решает задачу курицы и яйца: учётки и роли выдаются через админку, но открыть её некому, пока
 * нет ни одного администратора. Пользуются списком двое: создание учёток при старте
 * ({@code provisioning.BootstrapAdminInvitations}) и выдача роли при входе
 * ({@code login.BootstrapAdminGranter}). Пустая настройка отключает оба.
 */
@Component
public class BootstrapAdminEmails {

    private final Set<EmailAddress> emails;

    /**
     * Адреса прогоняются через {@link EmailAddress}, поэтому сравнение не зависит от регистра и
     * пробелов. Побочный эффект полезный: кривой адрес в настройке уронит приложение при старте, а не
     * тихо превратится в «администратора, который никогда не совпадёт».
     */
    public BootstrapAdminEmails(AllowedEmailDomains allowedDomains,
                                @Value("${auth.bootstrap.admin-emails:}") List<String> emails) {
        this.emails = emails.stream()
                .filter(email -> !email.isBlank())
                .map(EmailAddress::new)
                .collect(Collectors.toUnmodifiableSet());
        // Адрес чужого домена войти не сможет, а значит, и роль не получит никогда — лучше узнать
        // об этом при старте, чем гадать, почему админка не открывается.
        this.emails.forEach(allowedDomains::requireAllowed);
    }

    public Set<EmailAddress> all() {
        return emails;
    }

    public boolean contains(EmailAddress email) {
        return emails.contains(email);
    }

    public boolean isEmpty() {
        return emails.isEmpty();
    }
}
