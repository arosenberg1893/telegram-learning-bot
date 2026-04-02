package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.UserStudyTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserStudyTimeRepository extends JpaRepository<UserStudyTime, Long> {

    Optional<UserStudyTime> findByUserIdAndTopicId(Long userId, Long topicId);

    List<UserStudyTime> findByUserIdAndTopicIdIn(Long userId, List<Long> topicIds);

    @Query("SELECT SUM(ust.totalSeconds) FROM UserStudyTime ust WHERE ust.userId = :userId")
    Long sumTotalSecondsByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserStudyTime ust WHERE ust.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserStudyTime ust WHERE ust.topic.id IN (SELECT t.id FROM Topic t WHERE t.section.course.id = :courseId)")
    void deleteByCourseId(@Param("courseId") Long courseId);

    /**
     * Объединяет записи времени изучения: копирует из slave в master,
     * при конфликте (user_id, topic_id) суммирует total_seconds и берёт максимальный last_action_at.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO user_study_time (user_id, topic_id, total_seconds, last_action_at) " +
            "SELECT :masterUserId, topic_id, total_seconds, last_action_at " +
            "FROM user_study_time WHERE user_id = :slaveUserId " +
            "ON CONFLICT (user_id, topic_id) DO UPDATE SET " +
            "total_seconds = user_study_time.total_seconds + EXCLUDED.total_seconds, " +
            "last_action_at = GREATEST(user_study_time.last_action_at, EXCLUDED.last_action_at)",
            nativeQuery = true)
    void mergeUserStudyTime(@Param("slaveUserId") Long slaveUserId, @Param("masterUserId") Long masterUserId);
}