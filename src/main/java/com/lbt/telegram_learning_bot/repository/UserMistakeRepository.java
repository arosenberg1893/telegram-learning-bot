package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.Question;
import com.lbt.telegram_learning_bot.entity.UserMistake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserMistakeRepository extends JpaRepository<UserMistake, Long> {

    Optional<UserMistake> findByUserIdAndQuestionId(Long userId, Long questionId);

    @Query("SELECT um.question FROM UserMistake um WHERE um.userId = :userId")
    List<Question> findMistakeQuestionsByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    void deleteByUserIdAndQuestionId(Long userId, Long questionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserMistake um WHERE um.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    // ================== НОВЫЙ МЕТОД ДЛЯ СЛИЯНИЯ АККАУНТОВ ==================
    /**
     * Объединяет записи об ошибках: копирует из slave в master,
     * при конфликте (user_id, question_id) оставляет запись с более поздним last_mistake_at.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO user_mistake (user_id, question_id, last_mistake_at) " +
            "SELECT :masterUserId, question_id, last_mistake_at " +
            "FROM user_mistake WHERE user_id = :slaveUserId " +
            "ON CONFLICT (user_id, question_id) DO UPDATE SET " +
            "last_mistake_at = EXCLUDED.last_mistake_at " +
            "WHERE EXCLUDED.last_mistake_at > user_mistake.last_mistake_at",
            nativeQuery = true)
    void mergeUserMistakes(@Param("slaveUserId") Long slaveUserId, @Param("masterUserId") Long masterUserId);
}