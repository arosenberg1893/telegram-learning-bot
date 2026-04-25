package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.Topic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {
    List<Topic> findBySectionIdOrderByOrderIndexAsc(Long sectionId);
    Page<Topic> findBySectionId(Long sectionId, Pageable pageable);

    /**
     * Пакетная загрузка тем для списка разделов.
     * Используется в getCourseStatusEmoji и getRandomQuestionsForCourse вместо N вызовов по одному разделу.
     */
    @Query("SELECT t FROM Topic t WHERE t.section.id IN :sectionIds ORDER BY t.section.id, t.orderIndex")
    List<Topic> findBySectionIds(@Param("sectionIds") List<Long> sectionIds);

    /**
     * Загружает тему со всеми блоками (один уровень — безопасно от MultipleBagFetchException).
     */
    @Query("SELECT DISTINCT t FROM Topic t LEFT JOIN FETCH t.blocks b WHERE t.id = :topicId")
    Optional<Topic> findByIdWithBlocks(@Param("topicId") Long topicId);

    /**
     * Пакетная загрузка тем с блоками по списку ID (для раздела/курса).
     */
    @Query("SELECT DISTINCT t FROM Topic t LEFT JOIN FETCH t.blocks b WHERE t.id IN :topicIds")
    List<Topic> findByIdsWithBlocks(@Param("topicIds") List<Long> topicIds);
}