package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.LinkedAccount;
import com.lbt.telegram_learning_bot.platform.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LinkedAccountRepository extends JpaRepository<LinkedAccount, Long> {

    Optional<LinkedAccount> findByPlatformAndExternalUserId(Platform platform, Long externalUserId);

    List<LinkedAccount> findByInternalUserId(Long internalUserId);

    boolean existsByPlatformAndExternalUserId(Platform platform, Long externalUserId);
}
