package com.example.planner.registration;

import com.example.planner.onetimetoken.InvalidOneTimeTokenException;
import com.example.planner.onetimetoken.OneTimeTokenService;
import com.example.planner.onetimetoken.TokenPurpose;
import com.example.planner.user.EmailAddress;
import com.example.planner.user.PasswordCredential;
import com.example.planner.user.PasswordCredentialRepository;
import com.example.planner.user.PasswordHasher;
import com.example.planner.user.User;
import com.example.planner.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordCredentialRepository credentialRepository;
    private final PasswordHasher passwordHasher;
    private final OneTimeTokenService tokenService;
    private final ApplicationEventPublisher events;

    /**
     * Саморегистрация: пользователь в статусе PENDING_VERIFICATION, его пароль, токен EMAIL_VERIFY
     * и письмо со ссылкой (уходит после коммита транзакции).
     *
     * <p>Если почта уже занята — молча ничего не делает: снаружи ответ одинаковый,
     * чтобы по форме регистрации нельзя было узнать, чья почта у нас есть.
     *
     * <p>Если та же почта регистрируется одновременно из двух запросов, проверку пройдут оба,
     * а второй упадёт на {@code users_email_key} — вызывающий код должен трактовать
     * {@link org.springframework.dao.DataIntegrityViolationException} так же, как «почта занята».
     */
    @Transactional
    public void register(EmailAddress email, String fullName, String rawPassword) {
        // Хешируем до проверки почты: так ответ занимает одинаковое время
        // и для новой, и для занятой почты, и по времени ответа ничего не узнать.
        String passwordHash = passwordHasher.hash(rawPassword);

        if (userRepository.existsByEmail(email)) {
            return;
        }

        User user = userRepository.save(User.selfRegistered(email, fullName));
        credentialRepository.save(PasswordCredential.forUser(user, passwordHash));
        String rawToken = tokenService.issue(user, TokenPurpose.EMAIL_VERIFY);

        events.publishEvent(new VerificationEmailRequested(email, fullName, rawToken));
    }

    /**
     * Переход по ссылке из письма: гасит токен EMAIL_VERIFY и активирует аккаунт.
     *
     * @throws InvalidOneTimeTokenException если ссылка недействительна, использована, устарела
     *                                      или пользователь уже не ждёт подтверждения (например, заблокирован)
     */
    @Transactional
    public void confirmEmail(String rawToken) {
        User user = tokenService.consume(rawToken, TokenPurpose.EMAIL_VERIFY);
        if (!user.isAwaitingEmailVerification()) {
            throw new InvalidOneTimeTokenException();
        }
        user.verifyEmail();
    }
}
