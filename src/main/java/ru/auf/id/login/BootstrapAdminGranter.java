package ru.auf.id.login;

import ru.auf.id.user.BootstrapAdminEmails;
import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Выдаёт роль {@code ADMIN} тем, кто назван в настройке {@code auth.bootstrap.admin-emails}
 * ({@link BootstrapAdminEmails}).
 *
 * <p>Учётку на пустой базе заводит при старте {@code provisioning.BootstrapAdminInvitations}, но
 * роль выдаётся здесь, на входе: это первый момент, когда точно известно, что аккаунт активирован и
 * принадлежит владельцу адреса.
 *
 * <p>Срабатывает, только пока в системе нет ни одного действующего администратора: первый вошедший
 * из списка становится админом, остальных он назначит сам через админку. Поэтому снятая через
 * админку роль не возвращается при следующем входе. Запись в список никогда не отнимает роль и не
 * касается никого другого. Пустая настройка полностью отключает механизм.
 */
@Component
public class BootstrapAdminGranter {

    private final UserRepository userRepository;
    private final BootstrapAdminEmails bootstrapAdmins;

    public BootstrapAdminGranter(UserRepository userRepository, BootstrapAdminEmails bootstrapAdmins) {
        this.userRepository = userRepository;
        this.bootstrapAdmins = bootstrapAdmins;
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

    /**
     * Роль выдаётся, только пока в системе нет ни одного действующего администратора. Иначе
     * снять её через админку было бы невозможно: при следующем же входе она вернулась бы, пока
     * адрес остаётся в настройке.
     */
    private void grantIfListed(User user) {
        if (bootstrapAdmins.contains(user.getEmail()) && userRepository.countUsableWithRole(Role.ADMIN) == 0) {
            user.grantRole(Role.ADMIN);
        }
    }
}
