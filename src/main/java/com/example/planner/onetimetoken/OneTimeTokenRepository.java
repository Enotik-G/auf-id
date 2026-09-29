package com.example.planner.onetimetoken;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {

    /**
     * Блокирует найденную строку до конца транзакции (SELECT ... FOR UPDATE):
     * два одновременных перехода по одной ссылке не смогут оба её погасить.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OneTimeToken> findByTokenHashAndPurpose(String tokenHash, TokenPurpose purpose);
}
