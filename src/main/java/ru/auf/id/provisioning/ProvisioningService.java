package ru.auf.id.provisioning;

import ru.auf.id.consent.ConsentService;
import ru.auf.id.onetimetoken.InvalidOneTimeTokenException;
import ru.auf.id.onetimetoken.OneTimeTokenService;
import ru.auf.id.onetimetoken.TokenPurpose;
import ru.auf.id.user.AllowedEmailDomains;
import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.PasswordCredential;
import ru.auf.id.user.PasswordCredentialRepository;
import ru.auf.id.user.PasswordHasher;
import ru.auf.id.user.Role;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import ru.auf.id.user.UserNotFoundException;
import ru.auf.id.user.UserStatus;
import ru.auf.id.user.WrongUserStatusException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Выдача учётных записей администратором — единственный путь, которым в системе появляется аккаунт.
 *
 * <p>Саморегистрации нет: писем сервис не отправляет, а значит подтвердить владение адресом нечем.
 * Вместо письма администратор получает одноразовую ссылку и передаёт её человеку лично.
 */
@Service
@RequiredArgsConstructor
public class ProvisioningService {

    private final UserRepository userRepository;
    private final PasswordCredentialRepository credentialRepository;
    private final PasswordHasher passwordHasher;
    private final OneTimeTokenService tokenService;
    private final AllowedEmailDomains allowedDomains;
    private final Clock clock;
    private final ConsentService consentService;

    /**
     * Создаёт учётку в статусе {@code INVITED} и выдаёт токен активации.
     *
     * <p>Пароля у неё пока нет: его задаст владелец, перейдя по ссылке. Поэтому войти в такую учётку
     * невозможно — {@code AccountUserDetailsService} пускает только {@code ACTIVE}.
     *
     * @param roles роли, выдаваемые сразу; пустой набор допустим
     * @throws EmailAlreadyTakenException если учётка с такой почтой уже есть
     */
    @Transactional
    public Invitation invite(EmailAddress email, String fullName, Set<Role> roles) {
        allowedDomains.requireAllowed(email);
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyTakenException(email);
        }

        User user = User.invited(email, fullName, Instant.now(clock));
        roles.forEach(user::grantRole);
        userRepository.save(user);

        // Токен виден целиком только в этот момент: в базе остаётся лишь его SHA-256.
        return new Invitation(user.getId(), tokenService.issue(user, TokenPurpose.INVITE));
    }

    /**
     * Выдаёт новую ссылку активации вместо прежних: человек потерял ссылку или она истекла.
     *
     * <p>Прежние ссылки при этом обесцениваются. Иначе выдача новой не закрывала бы утёкшую старую —
     * а просить администратора «выдайте ещё одну» как раз и будут в том числе потому, что первая
     * ушла не туда.
     *
     * @throws UserNotFoundException если учётки нет
     * @throws WrongUserStatusException если учётка уже не ждёт активации
     */
    @Transactional
    public Invitation reissueInvitation(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getStatus() != UserStatus.INVITED) {
            throw new WrongUserStatusException(
                    "Ссылку активации можно выдать только учётке в статусе INVITED, сейчас " + user.getStatus());
        }

        tokenService.revokeAll(user, TokenPurpose.INVITE);
        return new Invitation(userId, tokenService.issue(user, TokenPurpose.INVITE));
    }

    /**
     * Переход по ссылке активации: гасит токен, сохраняет пароль и согласие на обработку ПДн и
     * делает учётку активной — всё в одной транзакции.
     *
     * <p>Проверка статуса обязательна вместе с проверкой токена: учётку могли заблокировать или уже
     * активировать после того, как ссылку выдали. Причину наружу не различаем — для перешедшего по
     * ссылке это одинаковое «ссылка недействительна».
     *
     * @param clientIp адрес, с которого дано согласие, — пишется в запись о согласии
     * @throws InvalidOneTimeTokenException если ссылка недействительна, использована, устарела
     *                                      или учётка уже не ждёт активации
     */
    @Transactional
    public void activate(String rawToken, String rawPassword, String clientIp) {
        User user = tokenService.consume(rawToken, TokenPurpose.INVITE);
        if (user.getStatus() != UserStatus.INVITED) {
            throw new InvalidOneTimeTokenException();
        }
        credentialRepository.save(PasswordCredential.forUser(user, passwordHasher.hash(rawPassword), Instant.now(clock)));
        consentService.recordPersonalDataConsent(user, clientIp);
        user.activate();
    }
}
