package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    // Long — это тип userId
}