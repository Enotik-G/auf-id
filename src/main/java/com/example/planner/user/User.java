package com.example.planner.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private EmailAddress email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Новый пользователь, зарегистрировавшийся сам: почта ещё не подтверждена. */
    public static User selfRegistered(EmailAddress email, String fullName) {
        User user = new User();
        user.email = email;
        user.fullName = fullName;
        user.emailVerified = false;
        user.status = UserStatus.PENDING_VERIFICATION;
        user.createdAt = Instant.now();
        return user;
    }

    public boolean isAwaitingEmailVerification() {
        return status == UserStatus.PENDING_VERIFICATION;
    }

    /** Владелец подтвердил почту — аккаунт становится активным. */
    public void verifyEmail() {
        if (!isAwaitingEmailVerification()) {
            throw new IllegalStateException("Подтвердить почту можно только в статусе PENDING_VERIFICATION, сейчас " + status);
        }
        emailVerified = true;
        status = UserStatus.ACTIVE;
    }

    /** Отметить успешный вход. */
    public void recordLogin(Instant at) {
        lastLoginAt = at;
    }
}
