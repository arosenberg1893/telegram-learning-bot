package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.Section;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

@Repository
public interface SectionRepository extends JpaRepository<Section, Long> {
    List<Section> findByCourseIdOrderByOrderIndexAsc(Long courseId);
    Page<Section> findByCourseId(Long courseId, Pageable pageable);
}