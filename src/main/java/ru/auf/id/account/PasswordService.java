package ru.auf.id.account;

import ru.auf.id.authserver.UserAuthorizationRevoker;
import ru.auf.id.provisioning.PasswordPolicy;
import ru.auf.id.user.PasswordCredential;
import ru.auf.id.user.PasswordCredentialRepository;
import ru.auf.id.user.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Замена пароля у действующей учётки — общее для смены пароля самим человеком и сброса по ссылке
 * от администратора.
 *
 * <p>Пароль меняют, когда его кто-то узнал. Поэтому замена — это не только новый хеш, но и обрыв
 * всего, что успели получить со старым паролем:
 * <ul>
 *   <li>выданные токены отзываются сразу ({@link UserAuthorizationRevoker}) — лаунчер и
 *       планировщик попросят войти заново;</li>
 *   <li>открытые сессии закроются на своём следующем запросе: они открыты раньше {@code changed_at}
 *       ({@code SessionUserRevalidationFilter}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PasswordService {

    private final PasswordCredentialRepository credentialRepository;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHasher passwordHasher;
    private final UserAuthorizationRevoker authorizationRevoker;
    private final Clock clock;

    /**
     * Правила пароля проверяются здесь, а не у вызывающего: тогда ни смена, ни сброс не смогут их
     * пропустить.
     *
     * @throws WeakPasswordException если пароль не проходит {@link PasswordPolicy}
     * @throws IllegalStateException если у учётки нет пароля — её ещё не активировали
     */
    @Transactional
    public void replacePassword(UUID userId, String newRawPassword) {
        PasswordPolicy.Verdict verdict = passwordPolicy.check(newRawPassword);
        if (verdict != PasswordPolicy.Verdict.OK) {
            throw new WeakPasswordException(verdict);
        }
        PasswordCredential credential = credentialRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("У учётки " + userId + " нет пароля"));

        credential.replace(passwordHasher.hash(newRawPassword), Instant.now(clock));
        authorizationRevoker.revokeAll(userId);
    }
}
