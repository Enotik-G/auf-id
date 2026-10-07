package com.example.planner.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
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

    /**
     * Роли из таблицы {@code user_roles}.
     *
     * <p>{@code EAGER} здесь осознанно: роли читают при выдаче токена и при входе — то есть почти
     * всегда, когда пользователя вообще загружают, — и часто уже за пределами транзакции
     * репозитория. Ленивая загрузка в этом месте дала бы {@code LazyInitializationException}.
     * Цена мизерная: у пользователя не больше трёх строк.
     *
     * <p>Колонку {@code granted_at} не отображаем — её заполняет умолчание в БД. Нам она нужна для
     * разбора «кто когда получил роль», а не для логики.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new LinkedHashSet<>();

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

    /**
     * Копия, а не сам набор: снаружи роли меняют только через {@link #grantRole} и
     * {@link #revokeRole}, иначе любой желающий сможет дописать роль мимо проверок.
     */
    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /** Повторная выдача той же роли ничего не меняет — набор есть набор. */
    public void grantRole(Role role) {
        roles.add(role);
    }

    public void revokeRole(Role role) {
        roles.remove(role);
    }
}
