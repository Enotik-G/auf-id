package ru.auf.id.admin;

import ru.auf.id.authserver.UserAuthorizationRevoker;
import ru.auf.id.onetimetoken.OneTimeTokenService;
import ru.auf.id.onetimetoken.TokenPurpose;
import ru.auf.id.user.PasswordCredentialRepository;
import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import ru.auf.id.user.UserNotFoundException;
import ru.auf.id.user.UserRepository;
import ru.auf.id.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Действия администратора над учётными записями: блокировка и роли.
 *
 * <p>Выдача новых учёток и ссылок активации — в {@code ProvisioningService}: это разные зоны
 * ответственности, хотя обе доступны администратору.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordCredentialRepository credentialRepository;
    private final OneTimeTokenService tokenService;
    private final UserAuthorizationRevoker authorizationRevoker;

    /**
     * Закрывает доступ и обесценивает ожидающие ссылки.
     *
     * <p>Ссылки гасим потому, что неиспользованная ссылка активации — это отложенный вход:
     * заблокировали человека, а он через час активировался по старой ссылке.
     *
     * <p>Всё, что сервер авторизации выдал пользователю (коды, токены), удаляем в той же транзакции:
     * не удалась блокировка — не удалится и это, и наоборот. Access token, уже ушедший в сервис
     * экосистемы, доживёт свои 10 минут — сервисы проверяют подпись сами, к нам не ходят. Новых
     * токенов он не получит, а открытая сессия закроется на следующем же запросе
     * ({@code UserClaims.addTo}, {@code SessionUserRevalidationFilter}).
     *
     * @throws LastAdminException если это последний действующий администратор
     */
    @Transactional
    public void block(UUID userId) {
        User user = find(userId);
        if (isLastUsableAdmin(user)) {
            throw new LastAdminException("Блокировка администратора");
        }

        user.block();
        tokenService.revokeAll(user, TokenPurpose.INVITE);
        authorizationRevoker.revokeAll(userId);
    }

    /**
     * Снимает блокировку.
     *
     * <p>Куда вернуть, решаем здесь: у учётки, заблокированной до активации, пароля нет, и вернуть её
     * в {@code ACTIVE} означало бы показать исправный с виду аккаунт, в который невозможно войти.
     * Такая возвращается в {@code INVITED} — администратору останется выдать ссылку активации.
     */
    @Transactional
    public void unblock(UUID userId) {
        User user = find(userId);
        UserStatus restoreTo = credentialRepository.existsById(userId)
                ? UserStatus.ACTIVE
                : UserStatus.INVITED;
        user.unblock(restoreTo);
    }

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return find(userId);
    }

    @Transactional
    public void grantRole(UUID userId, Role role) {
        find(userId).grantRole(role);
    }

    /**
     * @throws LastAdminException если снятие роли оставит систему без администраторов
     */
    @Transactional
    public void revokeRole(UUID userId, Role role) {
        User user = find(userId);
        if (role == Role.ADMIN && isLastUsableAdmin(user)) {
            throw new LastAdminException("Снятие роли ADMIN");
        }
        user.revokeRole(role);
    }

    /**
     * Защита от самой дорогой ошибки в админке: снять роль себе или заблокировать себя, оставшись
     * единственным администратором. Восстановить доступ после этого можно только правкой базы руками —
     * {@code auth.bootstrap.admin-emails} спасёт лишь если его настроили заранее.
     */
    private boolean isLastUsableAdmin(User user) {
        boolean usableAdminNow = user.hasRole(Role.ADMIN)
                && user.getStatus() != UserStatus.BLOCKED
                && user.getStatus() != UserStatus.DELETED;
        return usableAdminNow && userRepository.countUsableWithRole(Role.ADMIN) <= 1;
    }

    private User find(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }
}
