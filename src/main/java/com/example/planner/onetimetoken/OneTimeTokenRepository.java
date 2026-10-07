package com.example.planner.onetimetoken;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {

    /**
     * Блокирует найденную строку до конца транзакции (SELECT ... FOR UPDATE):
     * два одновременных перехода по одной ссылке не смогут оба её погасить.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OneTimeToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);

    /**
     * Ещё не использованные токены одного назначения у пользователя.
     *
     * <p>Читаем и правим по одной сущности, а не одним {@code UPDATE}: массовый запрос пишет прямо в
     * БД, оставляя в контексте персистентности прежние значения, и чтение в той же транзакции видело
     * бы отозванный токен живым. Токенов у пользователя единицы, так что выигрыш одного запроса не
     * стоит такой ловушки.
     */
    List<OneTimeToken> findByUserIdAndPurposeAndUsedAtIsNull(UUID userId, TokenPurpose purpose);
}
