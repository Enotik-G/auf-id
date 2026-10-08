package ru.auf.id.onetimetoken;

import ru.auf.id.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Выдаёт и гасит одноразовые токены для одноразовых ссылок (активация, сброс пароля).
 * Наружу уходит сам токен, в БД — только его SHA-256. Писем сервис не отправляет:
 * ссылку администратор передаёт человеку лично.
 */
@Service
@RequiredArgsConstructor
public class OneTimeTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OneTimeTokenRepository repository;
    private final Clock clock;

    /** Создаёт токен и возвращает его — это единственный момент, когда токен виден целиком. */
    @Transactional
    public String issue(User user, TokenPurpose purpose) {
        String rawToken = generateRawToken();
        repository.save(OneTimeToken.issue(user, purpose, sha256Hex(rawToken), Instant.now(clock)));
        return rawToken;
    }

    /**
     * Гасит токен и возвращает пользователя, которому он был выдан.
     *
     * @throws InvalidOneTimeTokenException если токен не найден, не того назначения, уже использован или истёк
     */
    @Transactional
    public User consume(String rawToken, TokenPurpose purpose) {
        OneTimeToken token = repository.findByTokenHashAndPurpose(sha256Hex(rawToken), purpose)
                .orElseThrow(InvalidOneTimeTokenException::new);
        token.markUsed(Instant.now(clock));
        return token.getUser();
    }

    /**
     * Обесценивает все живые токены этого назначения у пользователя.
     *
     * @return сколько ссылок перестало работать
     */
    @Transactional
    public int revokeAll(User user, TokenPurpose purpose) {
        List<OneTimeToken> live = repository.findByUserIdAndPurposeAndUsedAtIsNull(user.getId(), purpose);
        Instant now = Instant.now(clock);
        live.forEach(token -> token.revoke(now));
        return live.size();
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 недоступен в этой JVM", e);
        }
    }
}
