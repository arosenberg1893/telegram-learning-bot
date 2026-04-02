package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.Topic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {
    List<Topic> findBySectionIdOrderByOrderIndexAsc(Long sectionId);
    Page<Topic> findBySectionId(Long sectionId, Pageable pageable);
}