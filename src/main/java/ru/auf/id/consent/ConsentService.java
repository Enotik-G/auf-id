package ru.auf.id.consent;

import ru.auf.id.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Записывает согласия. Версия текста согласия на обработку ПДн — в настройке
 * {@code auth.consent.personal-data-version}: поменяли текст ({@code templates/consent/personal-data.html})
 * — поднимите версию, тогда по записи всегда видно, с каким именно текстом человек согласился.
 */
@Service
public class ConsentService {

    private final ConsentRepository repository;
    private final Clock clock;
    private final String personalDataVersion;

    public ConsentService(ConsentRepository repository, Clock clock,
                          @Value("${auth.consent.personal-data-version}") String personalDataVersion) {
        this.repository = repository;
        this.clock = clock;
        this.personalDataVersion = personalDataVersion;
    }

    /** Вызывается внутри транзакции активации: не сохранилось согласие — не сохранится и пароль. */
    public void recordPersonalDataConsent(User user, String ip) {
        repository.save(Consent.given(user, ConsentDocument.PERSONAL_DATA, personalDataVersion, Instant.now(clock), ip));
    }

    public String personalDataVersion() {
        return personalDataVersion;
    }
}
