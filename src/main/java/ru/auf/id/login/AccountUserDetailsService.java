package ru.auf.id.login;

import ru.auf.id.user.EmailAddress;
import ru.auf.id.user.InvalidEmailException;
import ru.auf.id.user.PasswordCredential;
import ru.auf.id.user.PasswordCredentialRepository;
import ru.auf.id.user.User;
import ru.auf.id.user.UserRepository;
import ru.auf.id.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Объясняет Spring Security, где искать пользователя при входе: по почте из формы — в нашей БД.
 * Проверку пароля Spring делает сам, через бин PasswordEncoder (он поверх нашего PasswordHasher).
 */
@Service
@RequiredArgsConstructor
public class AccountUserDetailsService implements UserDetailsService {

    static final String ROLE_PREFIX = "ROLE_";

    private final UserRepository userRepository;
    private final PasswordCredentialRepository credentialRepository;

    /**
     * @param username то, что человек ввёл в поле логина, — его почта
     * @return данные для входа; имя пользователя в них — <b>id</b>, а не почта:
     *         id не меняется никогда, и именно он станет {@code sub} в JWT
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        User user = findByEmail(username);
        PasswordCredential credential = credentialRepository.findById(user.getId())
                .orElseThrow(() -> new UsernameNotFoundException("У пользователя нет пароля"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getId().toString())
                .password(credential.getPasswordHash())
                .authorities(roleAuthorities(user))
                .disabled(user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.LOCKED)
                // Статус LOCKED в БД — на будущее, ставит администратор. От подбора пароля защищает
                // не блокировка, а капча (LoginAttemptService) — чужой аккаунт так не заблокировать.
                .accountLocked(user.getStatus() == UserStatus.LOCKED)
                .build();
    }

    /**
     * Роли пользователя в виде полномочий Spring Security — с приставкой {@code ROLE_}: именно её
     * ждёт {@code hasRole("ADMIN")}. Без этого правила доступа к админке не сработали бы вовсе —
     * вошедший администратор выглядел бы как пользователь без единого полномочия.
     *
     * <p>Нужны и при входе, и при перепроверке уже открытой сессии ({@link SessionUserRevalidationFilter}),
     * поэтому собраны в одном месте.
     */
    static List<GrantedAuthority> roleAuthorities(User user) {
        return user.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(ROLE_PREFIX + role.name()))
                .toList();
    }

    private User findByEmail(String rawEmail) {
        try {
            return userRepository.findByEmail(new EmailAddress(rawEmail))
                    .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
        } catch (InvalidEmailException e) {
            throw new UsernameNotFoundException("Пользователь не найден", e);
        }
    }
}
