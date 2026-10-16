package ru.auf.id.account;

import ru.auf.id.authserver.UserAuthorizationRevoker;
import ru.auf.id.onetimetoken.InvalidOneTimeTokenException;
import ru.auf.id.onetimetoken.OneTimeTokenService;
import ru.auf.id.onetimetoken.TokenPurpose;
import ru.auf.id.provisioning.PasswordPolicy;
import ru.auf.id.user.PasswordCredential;
import ru.auf.id.user.PasswordCredentialRepository;
import ru.auf.id.user.PasswordHasher;
import ru.auf.id.user.User;
import ru.auf.id.user.UserStatus;
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
    private final PasswordChangeAttempts changeAttempts;
    private final OneTimeTokenService tokenService;

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
     * <p>Неверные текущие пароли считаются ({@link PasswordChangeAttempts}). Redis не участвует в
     * транзакции БД, поэтому неудача записывается, хотя исключение и откатит транзакцию, — так и нужно.
     *
     * @throws TooManyPasswordAttemptsException если неверных попыток уже слишком много
     * @throws WrongCurrentPasswordException если текущий пароль неверен
     * @throws WeakPasswordException если новый пароль не проходит {@link PasswordPolicy}
     */
    @Transactional
    public void changePassword(UUID userId, String currentRawPassword, String newRawPassword) {
        changeAttempts.lockedFor(userId).ifPresent(wait -> {
            throw new TooManyPasswordAttemptsException(wait);
        });
        PasswordCredential credential = credentialOf(userId);
        if (!passwordHasher.matches(currentRawPassword, credential.getPasswordHash())) {
            changeAttempts.recordFailure(userId);
            throw new WrongCurrentPasswordException();
        }
        replace(credential, newRawPassword);
        changeAttempts.reset(userId);
    }

    /**
     * Сброс забытого пароля по ссылке от администратора — всё в одной транзакции.
     *
     * <p>Порядок важен: токен гасится первым, но если новый пароль не прошёл правила, исключение
     * откатит транзакцию вместе с погашением — ссылка останется рабочей, и человек попробует снова.
     *
     * <p>Учётку могли заблокировать уже после выдачи ссылки — тогда ссылка недействительна. Причину
     * наружу не различаем: перешедшему по ссылке это одинаковое «ссылка недействительна».
     *
     * @throws InvalidOneTimeTokenException если ссылка недействительна, использована, устарела
     *                                      или учётка уже не {@code ACTIVE}
     * @throws WeakPasswordException если новый пароль не проходит {@link PasswordPolicy}
     */
    @Transactional
    public void resetPassword(String rawToken, String newRawPassword) {
        User user = tokenService.consume(rawToken, TokenPurpose.PASSWORD_RESET);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidOneTimeTokenException();
        }
        replace(credentialOf(user.getId()), newRawPassword);
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
