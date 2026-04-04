package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.Section;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SectionRepository extends JpaRepository<Section, Long> {
    List<Section> findByCourseIdOrderByOrderIndexAsc(Long courseId);
    Page<Section> findByCourseId(Long courseId, Pageable pageable);

    /**
     * Загружает раздел со всеми темами для генерации PDF (только первый уровень).
     * Блоки загружаются отдельными запросами, чтобы избежать MultipleBagFetchException.
     */
    @Query("SELECT DISTINCT s FROM Section s LEFT JOIN FETCH s.topics t WHERE s.id = :sectionId")
    Optional<Section> findByIdWithTopics(@Param("sectionId") Long sectionId);
}