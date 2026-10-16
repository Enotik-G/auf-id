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
        replace(credentialOf(userId), newRawPassword);
    }

    /**
     * Смена пароля самим человеком: сначала — текущий пароль.
     *
     * <p>Вошедшему этого мало: сессию могли оставить открытой в компьютерном классе, и без
     * текущего пароля любой подошедший сменил бы его и забрал учётку себе.
     *
     * @throws WrongCurrentPasswordException если текущий пароль неверен
     * @throws WeakPasswordException если новый пароль не проходит {@link PasswordPolicy}
     */
    @Transactional
    public void changePassword(UUID userId, String currentRawPassword, String newRawPassword) {
        PasswordCredential credential = credentialOf(userId);
        if (!passwordHasher.matches(currentRawPassword, credential.getPasswordHash())) {
            throw new WrongCurrentPasswordException();
        }
        replace(credential, newRawPassword);
    }

    private PasswordCredential credentialOf(UUID userId) {
        return credentialRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("У учётки " + userId + " нет пароля"));
    }

    private void replace(PasswordCredential credential, String newRawPassword) {
        PasswordPolicy.Verdict verdict = passwordPolicy.check(newRawPassword);
        if (verdict != PasswordPolicy.Verdict.OK) {
            throw new WeakPasswordException(verdict);
        }
        credential.replace(passwordHasher.hash(newRawPassword), Instant.now(clock));
        authorizationRevoker.revokeAll(credential.getUserId());
    }
}
