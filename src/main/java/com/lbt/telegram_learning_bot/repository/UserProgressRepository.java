package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.Course;
import com.lbt.telegram_learning_bot.entity.Section;
import com.lbt.telegram_learning_bot.entity.UserProgress;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProgressRepository extends JpaRepository<UserProgress, Long> {

    // ================== Существующие методы ==================
    List<UserProgress> findByUserIdOrderByLastAccessedAtDesc(Long userId);
    Optional<UserProgress> findByUserIdAndCourseId(Long userId, Long courseId);

    @Query("SELECT DISTINCT p.course FROM UserProgress p WHERE p.userId = :userId AND p.isPassed = true ORDER BY MAX(p.lastAccessedAt) DESC")
    List<Course> findCoursesWithProgressByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT p.course.id FROM UserProgress p WHERE p.userId = :userId AND p.isPassed = true")
    List<Long> findDistinctCourseIdsWithProgressByUserId(@Param("userId") Long userId);

    List<UserProgress> findByUserId(Long userId);

    @Query("SELECT COUNT(DISTINCT p.course.id) FROM UserProgress p WHERE p.userId = :userId")
    long countDistinctCoursesByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(DISTINCT p.question.id) FROM UserProgress p WHERE p.userId = :userId AND p.course.id = :courseId AND p.question IS NOT NULL")
    long countDistinctAnsweredQuestionsByUserAndCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Query("SELECT p.course FROM UserProgress p WHERE p.userId = :userId GROUP BY p.course ORDER BY MAX(p.lastAccessedAt) DESC")
    List<Course> findCoursesWithProgressOrderByLastAccessed(@Param("userId") Long userId);

    @Query("SELECT p.course.id, COUNT(p), SUM(CASE WHEN p.answerResult = false THEN 1 ELSE 0 END) FROM UserProgress p WHERE p.userId = :userId AND p.question IS NOT NULL GROUP BY p.course.id")
    List<Object[]> getQuestionStatsByUser(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserProgress up WHERE up.course.id = :courseId")
    void deleteByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(DISTINCT p.question.id) FROM UserProgress p WHERE p.userId = :userId AND p.question IS NOT NULL AND p.question.block.topic.section.id = :sectionId")
    long countDistinctAnsweredQuestionsByUserAndSection(@Param("userId") Long userId, @Param("sectionId") Long sectionId);

    @Query("SELECT COUNT(DISTINCT p.question.id) FROM UserProgress p WHERE p.userId = :userId AND p.mode = 'learning' AND p.question IS NOT NULL AND p.question.block.topic.id = :topicId")
    long countDistinctAnsweredQuestionsByUserAndTopicLearning(@Param("userId") Long userId, @Param("topicId") Long topicId);

    @Query("SELECT COUNT(p) FROM UserProgress p WHERE p.userId = :userId AND p.mode = 'learning' AND p.question IS NOT NULL AND p.question.block.topic.id = :topicId AND p.answerResult = true")
    long countCorrectAnswersByUserAndTopic(@Param("userId") Long userId, @Param("topicId") Long topicId);

    @Query("SELECT COUNT(p) FROM UserProgress p WHERE p.userId = :userId AND p.mode = 'learning' AND p.question IS NOT NULL AND p.question.block.topic.section.id = :sectionId AND p.answerResult = true")
    long countCorrectAnswersByUserAndSection(@Param("userId") Long userId, @Param("sectionId") Long sectionId);

    @Query("SELECT COUNT(p) FROM UserProgress p WHERE p.userId = :userId AND p.mode = 'learning' AND p.question IS NOT NULL AND p.question.block.topic.section.id = :sectionId AND p.answerResult = false")
    long countWrongAnswersByUserAndSection(@Param("userId") Long userId, @Param("sectionId") Long sectionId);

    Optional<UserProgress> findTopByUserIdAndQuestionIdOrderByCompletedAtDesc(Long userId, Long questionId);
    Optional<UserProgress> findByUserIdAndSectionIdAndBlockIsNullAndQuestionIsNull(Long userId, Long sectionId);

    @Query("SELECT COUNT(p) FROM UserProgress p WHERE p.userId = :userId AND p.mode = 'learning' AND p.question IS NOT NULL AND p.course.id = :courseId AND p.answerResult = true")
    long countCorrectAnswersByUserAndCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    Optional<UserProgress> findByUserIdAndSection_Id(Long userId, Long sectionId);
    List<UserProgress> findByUserIdAndCourseIdAndBlockIsNullAndQuestionIsNullOrderByLastAccessedAtDesc(Long userId, Long courseId);
    List<UserProgress> findByUserIdAndSectionIdAndBlockIsNullAndQuestionIsNullOrderByLastAccessedAtDesc(Long userId, Long sectionId);

    Optional<UserProgress> findTopByUserIdAndQuestionIdAndModeOrderByCompletedAtDesc(Long userId, Long questionId, String mode);

    @Query("SELECT COUNT(p) FROM UserProgress p WHERE p.userId = :userId AND p.mode = 'learning' AND p.question IS NOT NULL AND p.course.id = :courseId AND p.answerResult = false")
    long countWrongAnswersByUserAndCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Query("SELECT COUNT(p) FROM UserProgress p WHERE p.userId = :userId AND p.mode = 'learning' AND p.question IS NOT NULL AND p.question.block.topic.id = :topicId AND p.answerResult = false")
    long countWrongAnswersByUserAndTopic(@Param("userId") Long userId, @Param("topicId") Long topicId);

    @Query("SELECT COUNT(DISTINCT p.question.id) FROM UserProgress p WHERE p.userId = :userId AND p.mode = 'learning' AND p.question IS NOT NULL AND p.question.block.topic.section.id = :sectionId")
    long countDistinctAnsweredQuestionsByUserAndSectionLearning(@Param("userId") Long userId, @Param("sectionId") Long sectionId);

    @Query("SELECT COUNT(DISTINCT p.question.id) FROM UserProgress p WHERE p.userId = :userId AND p.mode = 'learning' AND p.question IS NOT NULL AND p.course.id = :courseId")
    long countDistinctAnsweredQuestionsByUserAndCourseLearning(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Query("SELECT COUNT(DISTINCT p.question.id) FROM UserProgress p WHERE p.userId = :userId AND p.question IS NOT NULL AND p.question.block.topic.id = :topicId")
    long countDistinctAnsweredQuestionsByUserAndTopic(@Param("userId") Long userId, @Param("topicId") Long topicId);

    @Query("SELECT p.course.id, COUNT(DISTINCT p.question.id) FROM UserProgress p WHERE p.userId = :userId AND p.course.id IN :courseIds AND p.question IS NOT NULL GROUP BY p.course.id")
    List<Object[]> countDistinctAnsweredQuestionsByUserAndCourses(@Param("userId") Long userId, @Param("courseIds") List<Long> courseIds);

    @Query("SELECT p.course.id, COUNT(p) FROM UserProgress p WHERE p.userId = :userId AND p.mode = 'learning' AND p.course.id IN :courseIds AND p.answerResult = true GROUP BY p.course.id")
    List<Object[]> countCorrectLearningAnswersByUserAndCourses(@Param("userId") Long userId, @Param("courseIds") List<Long> courseIds);

    @Query("SELECT p.question.block.topic.section.id, COUNT(DISTINCT p.question.id) FROM UserProgress p WHERE p.userId = :userId AND p.question.block.topic.section.id IN :sectionIds AND p.question IS NOT NULL GROUP BY p.question.block.topic.section.id")
    List<Object[]> countDistinctAnsweredQuestionsByUserAndSections(@Param("userId") Long userId, @Param("sectionIds") List<Long> sectionIds);

    @Query("SELECT p.question.block.topic.section.id, COUNT(p) FROM UserProgress p WHERE p.userId = :userId AND p.mode = 'learning' AND p.question.block.topic.section.id IN :sectionIds AND p.answerResult = true GROUP BY p.question.block.topic.section.id")
    List<Object[]> countCorrectLearningAnswersByUserAndSections(@Param("userId") Long userId, @Param("sectionIds") List<Long> sectionIds);

    @Query("SELECT p.question.block.topic.id, COUNT(DISTINCT p.question.id) FROM UserProgress p WHERE p.userId = :userId AND p.mode = 'learning' AND p.question.block.topic.id IN :topicIds AND p.question IS NOT NULL GROUP BY p.question.block.topic.id")
    List<Object[]> countDistinctAnsweredLearningQuestionsByUserAndTopics(@Param("userId") Long userId, @Param("topicIds") List<Long> topicIds);

    @Query("SELECT p.question.block.topic.id, COUNT(p) FROM UserProgress p WHERE p.userId = :userId AND p.mode = 'learning' AND p.question.block.topic.id IN :topicIds AND p.answerResult = true GROUP BY p.question.block.topic.id")
    List<Object[]> countCorrectLearningAnswersByUserAndTopics(@Param("userId") Long userId, @Param("topicIds") List<Long> topicIds);

    @Query("SELECT p.course.id, p.course.title, COUNT(p), SUM(CASE WHEN p.answerResult = false THEN 1 ELSE 0 END) FROM UserProgress p WHERE p.userId = :userId AND p.question IS NOT NULL GROUP BY p.course.id, p.course.title")
    List<Object[]> getQuestionStatsByUserWithTitle(@Param("userId") Long userId);

    @Query("SELECT p.course FROM UserProgress p WHERE p.userId = :userId GROUP BY p.course ORDER BY MAX(p.lastAccessedAt) DESC")
    Page<Course> findCoursesWithProgressOrderByLastAccessed(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserProgress up WHERE up.question.id = :questionId")
    void deleteByQuestionId(@Param("questionId") Long questionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserProgress up WHERE up.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    boolean existsByUserId(Long userId);

    // ================== Методы, необходимые для NavigationService ==================

    /**
     * Возвращает все записи прогресса пользователя по конкретной теме в заданном режиме.
     * Используется для оптимизации статусов тем.
     */
    @Query("SELECT p FROM UserProgress p WHERE p.userId = :userId AND p.question.block.topic.id = :topicId AND p.mode = :mode")
    List<UserProgress> findByUserIdAndTopicIdAndMode(@Param("userId") Long userId, @Param("topicId") Long topicId, @Param("mode") String mode);

    /**
     * Находит прогресс пользователя по разделу (без привязки к блоку или вопросу).
     */
    Optional<UserProgress> findByUserIdAndSection(Long userId, Section section);

    /**
     * Альтернативный метод с использованием sectionId.
     */
    Optional<UserProgress> findByUserIdAndSectionId(Long userId, Long sectionId);

    // ================== НОВЫЙ МЕТОД ДЛЯ СЛИЯНИЯ АККАУНТОВ ==================
    /**
     * Копирует все записи прогресса из slaveInternalUserId в masterInternalUserId.
     * При копировании дублирование записей допускается, так как при отображении
     * берётся последняя запись по времени.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO user_progress (user_id, course_id, section_id, topic_id, block_id, question_id, is_passed, mode, answer_result, completed_at, last_accessed_at) " +
            "SELECT :masterUserId, course_id, section_id, topic_id, block_id, question_id, is_passed, mode, answer_result, completed_at, last_accessed_at " +
            "FROM user_progress WHERE user_id = :slaveUserId",
            nativeQuery = true)
    void copyUserProgress(@Param("slaveUserId") Long slaveUserId, @Param("masterUserId") Long masterUserId);
}