package com.example.planner.login;

import com.example.planner.user.EmailAddress;
import com.example.planner.user.InvalidEmailException;
import com.example.planner.user.PasswordCredential;
import com.example.planner.user.PasswordCredentialRepository;
import com.example.planner.user.User;
import com.example.planner.user.UserRepository;
import com.example.planner.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Объясняет Spring Security, где искать пользователя при входе: по почте из формы — в нашей БД.
 * Проверку пароля Spring делает сам, через бин PasswordEncoder (он поверх нашего PasswordHasher).
 */
@Service
@RequiredArgsConstructor
public class AccountUserDetailsService implements UserDetailsService {

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
                // Роли нужны Spring Security в виде полномочий с приставкой ROLE_: именно её ждёт
                // hasRole("ADMIN"). Без этого правила доступа к админке не сработали бы вовсе —
                // вошедший администратор выглядел бы как пользователь без единого полномочия.
                .authorities(user.getRoles().stream()
                        .map(role -> "ROLE_" + role.name())
                        .toArray(String[]::new))
                .disabled(user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.LOCKED)
                // Статус LOCKED в БД — на будущее, ставит администратор. От подбора пароля защищает
                // не блокировка, а капча (LoginAttemptService) — чужой аккаунт так не заблокировать.
                .accountLocked(user.getStatus() == UserStatus.LOCKED)
                .build();
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
