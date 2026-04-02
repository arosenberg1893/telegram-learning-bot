package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM UserSession us WHERE us.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}