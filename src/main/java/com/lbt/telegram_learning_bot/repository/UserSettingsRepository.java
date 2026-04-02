package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM UserSettings us WHERE us.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * Копирует настройки из slave в master (заменяет, если уже есть).
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO user_settings (user_id, shuffle_options, page_size, test_questions_per_block, show_explanations, notifications_enabled) " +
            "SELECT :masterUserId, shuffle_options, page_size, test_questions_per_block, show_explanations, notifications_enabled " +
            "FROM user_settings WHERE user_id = :slaveUserId " +
            "ON CONFLICT (user_id) DO UPDATE SET " +
            "shuffle_options = EXCLUDED.shuffle_options, " +
            "page_size = EXCLUDED.page_size, " +
            "test_questions_per_block = EXCLUDED.test_questions_per_block, " +
            "show_explanations = EXCLUDED.show_explanations, " +
            "notifications_enabled = EXCLUDED.notifications_enabled",
            nativeQuery = true)
    void copyUserSettings(@Param("slaveUserId") Long slaveUserId, @Param("masterUserId") Long masterUserId);
}