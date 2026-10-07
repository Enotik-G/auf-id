package com.example.planner.onetimetoken;

import com.example.planner.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/** Одноразовый токен из ссылки в письме. В БД хранится только SHA-256 от токена, не сам токен. */
@Entity
@Table(name = "one_time_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OneTimeToken {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public static OneTimeToken issue(User user, TokenPurpose purpose, String tokenHash, Instant now) {
        OneTimeToken token = new OneTimeToken();
        token.user = user;
        token.purpose = purpose;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = now.plus(purpose.lifetime());
        return token;
    }

    /**
     * Обесценить токен, не используя его: администратор выдал новую ссылку вместо этой.
     *
     * <p>В отличие от {@link #markUsed}, не бросает исключение — отозвать уже негодный токен не
     * ошибка, а ничего не значащее действие. Отзыв пишется в то же поле {@code used_at}: для проверки
     * действительности «использован» и «отозван» — одно и то же. Различать их понадобится в журнале
     * событий (задача 11), тогда появится отдельное поле.
     */
    void revoke(Instant now) {
        if (usedAt == null) {
            usedAt = now;
        }
    }

    /** Погасить токен. Второй раз или после истечения срока — нельзя. */
    void markUsed(Instant now) {
        boolean alreadyUsed = usedAt != null;
        boolean expired = !now.isBefore(expiresAt);
        if (alreadyUsed || expired) {
            throw new InvalidOneTimeTokenException();
        }
        usedAt = now;
    }
}
