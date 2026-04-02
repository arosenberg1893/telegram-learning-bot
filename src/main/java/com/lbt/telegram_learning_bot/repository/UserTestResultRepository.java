package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.UserTestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTestResultRepository extends JpaRepository<UserTestResult, Long> {

    Optional<UserTestResult> findByUserIdAndTestTypeAndTestId(Long userId, String testType, Long testId);

    @Query("SELECT t.testId, t.correctCount, t.wrongCount FROM UserTestResult t WHERE t.userId = :userId AND t.testType = :testType AND t.testId IN :testIds")
    List<Object[]> findTestResultsByUserAndTestIds(@Param("userId") Long userId, @Param("testType") String testType, @Param("testIds") List<Long> testIds);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserTestResult utr WHERE utr.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * Объединяет записи результатов тестов: копирует из slave в master,
     * при конфликте (user_id, test_type, test_id) суммирует correct_count и wrong_count,
     * и берёт максимальный updated_at.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO user_test_result (user_id, test_type, test_id, correct_count, wrong_count, updated_at) " +
            "SELECT :masterUserId, test_type, test_id, correct_count, wrong_count, updated_at " +
            "FROM user_test_result WHERE user_id = :slaveUserId " +
            "ON CONFLICT (user_id, test_type, test_id) DO UPDATE SET " +
            "correct_count = user_test_result.correct_count + EXCLUDED.correct_count, " +
            "wrong_count = user_test_result.wrong_count + EXCLUDED.wrong_count, " +
            "updated_at = GREATEST(user_test_result.updated_at, EXCLUDED.updated_at)",
            nativeQuery = true)
    void mergeUserTestResults(@Param("slaveUserId") Long slaveUserId, @Param("masterUserId") Long masterUserId);
}