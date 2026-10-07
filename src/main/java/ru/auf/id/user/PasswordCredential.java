package ru.auf.id.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Пароль пользователя — только в виде хеша из {@link PasswordHasher}. У пользователя не больше одного. */
@Entity
@Table(name = "password_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordCredential {

    @Id
    private UUID userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "must_change", nullable = false)
    private boolean mustChange;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public static PasswordCredential forUser(User user, String passwordHash) {
        PasswordCredential credential = new PasswordCredential();
        credential.user = user;
        credential.passwordHash = passwordHash;
        credential.mustChange = false;
        credential.changedAt = Instant.now();
        return credential;
    }
}
