package ru.auf.id.login;

import ru.auf.id.user.AllowedEmailDomains;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Выдаёт роль {@code ADMIN} тем, кто назван в настройке {@code auth.bootstrap.admin-emails}.
 *
 * <p>Решает задачу курицы и яйца: роли выдаются через админку, но открыть её некому, пока нет ни
 * одного администратора. Поэтому первых администраторов перечисляют в настройках.
 *
 * <p>Почему на входе, а не при старте приложения: при старте учётки может ещё не существовать, и
 * тогда настройку пришлось бы применять повторным перезапуском. Вход — первый момент, когда точно
 * известно, что аккаунт есть и принадлежит владельцу адреса.
 *
 * <p>Запись в список никогда не отнимает роль и не касается никого другого: снять роль можно только
 * через админку. Пустая настройка полностью отключает механизм.
 */
@Component
public class BootstrapAdminGranter {

    private final UserRepository userRepository;
    private final Set<EmailAddress> bootstrapAdmins;

    /**
     * Адреса прогоняются через {@link EmailAddress}, поэтому сравнение не зависит от регистра и
     * пробелов. Побочный эффект полезный: кривой адрес в настройке уронит приложение при старте, а не
     * тихо превратится в «администратора, который никогда не совпадёт».
     */
    public BootstrapAdminGranter(UserRepository userRepository,
                                 AllowedEmailDomains allowedDomains,
                                 @Value("${auth.bootstrap.admin-emails:}") List<String> adminEmails) {
        this.userRepository = userRepository;
        this.bootstrapAdmins = adminEmails.stream()
                .filter(email -> !email.isBlank())
                .map(EmailAddress::new)
                .collect(Collectors.toUnmodifiableSet());
        // Адрес чужого домена войти не сможет, а значит, и роль не получит никогда — лучше узнать
        // об этом при старте, чем гадать, почему админка не открывается.
        bootstrapAdmins.forEach(allowedDomains::requireAllowed);
    }

    @EventListener
    @Transactional
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        if (bootstrapAdmins.isEmpty()) {
            return;
        }
        // То же событие публикуется, когда вход выполняет сервис-клиент на /oauth2/token:
        // там имя — client_id, а не id пользователя.
        if (!(event.getAuthentication() instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        UUID userId = UUID.fromString(event.getAuthentication().getName());
        userRepository.findById(userId).ifPresent(this::grantIfListed);
    }

    private void grantIfListed(User user) {
        if (bootstrapAdmins.contains(user.getEmail())) {
            user.grantRole(Role.ADMIN);
        }
    }
}
