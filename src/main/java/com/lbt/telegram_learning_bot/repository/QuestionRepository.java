package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByBlockIdOrderByOrderIndexAsc(Long blockId);

    @Query("SELECT COUNT(q) FROM Question q WHERE q.block.topic.section.id = :sectionId")
    long countBySectionId(@Param("sectionId") Long sectionId);

    @Query("SELECT COUNT(q) FROM Question q WHERE q.block.topic.id = :topicId")
    long countByTopicId(@Param("topicId") Long topicId);

    @Query("SELECT DISTINCT q FROM Question q LEFT JOIN FETCH q.answerOptions WHERE q.block.id IN :blockIds")
    List<Question> findAllByBlockIdWithOptions(@Param("blockIds") List<Long> blockIds);

    /**
     * Пакетная загрузка всех вопросов для списка тем.
     * Используется для batch-расчёта статусов тем без N+1 запросов.
     */
    @Query("SELECT q FROM Question q WHERE q.block.topic.id IN :topicIds ORDER BY q.block.topic.id, q.block.orderIndex, q.orderIndex")
    List<Question> findAllByTopicIds(@Param("topicIds") List<Long> topicIds);

    /**
     * Количество вопросов по каждой теме из списка.
     * Возвращает массивы [topicId, count].
     */
    @Query("SELECT q.block.topic.id, COUNT(q) FROM Question q WHERE q.block.topic.id IN :topicIds GROUP BY q.block.topic.id")
    List<Object[]> countByTopicIds(@Param("topicIds") List<Long> topicIds);
}