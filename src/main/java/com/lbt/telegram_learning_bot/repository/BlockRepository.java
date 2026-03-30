package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.Block;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import static com.lbt.telegram_learning_bot.util.Constants.*;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {
    List<Block> findByTopicIdOrderByOrderIndexAsc(Long topicId);
    @Query("SELECT b FROM Block b LEFT JOIN FETCH b.images WHERE b.id = :id")
    Optional<Block> findByIdWithImages(@Param("id") Long id);
    Page<Block> findByTopicId(Long topicId, Pageable pageable);
}