package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.LinkCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface LinkCodeRepository extends JpaRepository<LinkCode, Long> {

    Optional<LinkCode> findByCodeAndUsedFalse(String code);

    /** Удаляет просроченные коды. */
    @Modifying
    @Transactional
    @Query("DELETE FROM LinkCode lc WHERE lc.expiresAt < :now")
    void deleteExpiredBefore(Instant now);
}
