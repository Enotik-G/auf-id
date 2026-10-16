package ru.auf.id.provisioning;

import ru.auf.id.user.BootstrapAdminEmails;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import ru.auf.id.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Заводит учётки первых администраторов на пустой базе и пишет в лог ссылки активации.
 *
 * <p>Учётки создаёт только администратор, а на свежей базе (первый деплой, новая машина) его нет.
 * Поэтому при старте, пока в системе нет ни одного действующего администратора, для каждого адреса
 * из {@link BootstrapAdminEmails}:
 * <ul>
 *   <li>учётки нет — она создаётся в статусе {@code INVITED};</li>
 *   <li>учётка есть и ещё не активирована — прежняя ссылка гасится, выдаётся новая: если ссылку из
 *       лога потеряли, перезапуск её вернёт;</li>
 *   <li>учётка уже активирована — не трогается.</li>
 * </ul>
 *
 * <p>Роль {@code ADMIN} здесь не выдаётся: её выдаст {@code login.BootstrapAdminGranter} при первом
 * входе. Выдай её сразу — неактивированная учётка считалась бы действующим администратором, и
 * granter больше не сработал бы ни для кого.
 *
 * <p>Ссылка в логе — секрет: по ней задают пароль будущему администратору. Принято сознательно
 * (решение 2026-10-12): она одноразовая, живёт 7 дней и нужна только при первом запуске.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminInvitations {

    /** Настоящее ФИО админ впишет сам через админку — колонка {@code full_name} пустой быть не может. */
    static final String PLACEHOLDER_FULL_NAME = "Первый администратор";

    private final BootstrapAdminEmails bootstrapAdmins;
    private final UserRepository userRepository;
    private final ProvisioningService provisioning;
    private final OneTimeLinks oneTimeLinks;

    /**
     * {@link ApplicationReadyEvent} — приложение полностью запущено: миграции применены, база
     * доступна. Транзакции нет намеренно: каждая учётка заводится своей транзакцией внутри
     * {@link ProvisioningService}, поэтому каждая строка в логе соответствует тому, что уже лежит
     * в базе.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void inviteFirstAdmins() {
        if (bootstrapAdmins.isEmpty() || userRepository.countUsableWithRole(Role.ADMIN) > 0) {
            return;
        }
        for (EmailAddress email : bootstrapAdmins.all()) {
            inviteOrReissue(email).ifPresent(invitation -> log.warn(
                    "Администратора в системе нет. Чтобы задать пароль для {}, откройте ссылку "
                            + "(одноразовая, держите в секрете): {}",
                    email, oneTimeLinks.activationLinkFor(invitation.activationToken())));
        }
    }

    private Optional<Invitation> inviteOrReissue(EmailAddress email) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isEmpty()) {
            return Optional.of(provisioning.invite(email, PLACEHOLDER_FULL_NAME, Set.of()));
        }
        if (existing.get().getStatus() == UserStatus.INVITED) {
            return Optional.of(provisioning.reissueInvitation(existing.get().getId()));
        }
        return Optional.empty();
    }
}
