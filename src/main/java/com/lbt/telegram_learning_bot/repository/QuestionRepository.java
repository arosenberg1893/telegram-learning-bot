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
    // @Query("SELECT q FROM Question q LEFT JOIN FETCH q.images LEFT JOIN FETCH q.answerOptions WHERE q.id = :id")
// Optional<Question> findByIdWithImagesAndOptions(@Param("id") Long id);
    @Query("SELECT COUNT(q) FROM Question q WHERE q.block.topic.section.id = :sectionId")
    long countBySectionId(@Param("sectionId") Long sectionId);

    @Query("SELECT COUNT(q) FROM Question q WHERE q.block.topic.id = :topicId")
    long countByTopicId(@Param("topicId") Long topicId);

}