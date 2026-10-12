package ru.auf.id.consent;

import ru.auf.id.user.User;
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

/**
 * Данное человеком согласие: на какой документ, какой версии, когда и с какого адреса.
 *
 * <p>Запись не меняется после создания — сеттеров нет. Новая версия документа требует нового
 * согласия, и это новая строка; прежние остаются как история.
 */
@Entity
@Table(name = "consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Consent {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 32, updatable = false)
    private ConsentDocument document;

    @Column(name = "doc_version", nullable = false, length = 64, updatable = false)
    private String documentVersion;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @Column(nullable = false, length = 45, updatable = false)
    private String ip;

    public static Consent given(User user, ConsentDocument document, String documentVersion,
                                Instant acceptedAt, String ip) {
        Consent consent = new Consent();
        consent.user = user;
        consent.document = document;
        consent.documentVersion = documentVersion;
        consent.acceptedAt = acceptedAt;
        consent.ip = ip;
        return consent;
    }
}
