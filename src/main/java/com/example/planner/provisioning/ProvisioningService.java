package com.example.planner.provisioning;

import com.example.planner.onetimetoken.InvalidOneTimeTokenException;
import com.example.planner.onetimetoken.OneTimeTokenService;
import com.example.planner.onetimetoken.TokenPurpose;
import com.example.planner.user.EmailAddress;
import com.example.planner.user.PasswordCredential;
import com.example.planner.user.PasswordCredentialRepository;
import com.example.planner.user.PasswordHasher;
import com.example.planner.user.Role;
import com.example.planner.user.User;
import com.example.planner.user.UserRepository;
import com.example.planner.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

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
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyTakenException(email);
        }

        User user = User.invited(email, fullName);
        roles.forEach(user::grantRole);
        userRepository.save(user);

        // Токен виден целиком только в этот момент: в базе остаётся лишь его SHA-256.
        return new Invitation(user.getId(), tokenService.issue(user, TokenPurpose.INVITE));
    }

    /**
     * Переход по ссылке активации: гасит токен, сохраняет пароль и делает учётку активной.
     *
     * <p>Проверка статуса обязательна вместе с проверкой токена: учётку могли заблокировать или уже
     * активировать после того, как ссылку выдали. Причину наружу не различаем — для перешедшего по
     * ссылке это одинаковое «ссылка недействительна».
     *
     * @throws InvalidOneTimeTokenException если ссылка недействительна, использована, устарела
     *                                      или учётка уже не ждёт активации
     */
    @Transactional
    public void activate(String rawToken, String rawPassword) {
        User user = tokenService.consume(rawToken, TokenPurpose.INVITE);
        if (user.getStatus() != UserStatus.INVITED) {
            throw new InvalidOneTimeTokenException();
        }
        credentialRepository.save(PasswordCredential.forUser(user, passwordHasher.hash(rawPassword)));
        user.activate();
    }
}
