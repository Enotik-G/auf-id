package com.example.planner.provisioning;

import com.example.planner.onetimetoken.OneTimeTokenService;
import com.example.planner.onetimetoken.TokenPurpose;
import com.example.planner.user.EmailAddress;
import com.example.planner.user.Role;
import com.example.planner.user.User;
import com.example.planner.user.UserRepository;
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
}
