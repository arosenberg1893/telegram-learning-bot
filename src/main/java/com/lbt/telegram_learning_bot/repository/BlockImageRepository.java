package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.BlockImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import static com.lbt.telegram_learning_bot.util.Constants.*;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

@Repository
public interface BlockImageRepository extends JpaRepository<BlockImage, Long> {
    List<BlockImage> findByBlockIdOrderByOrderIndexAsc(Long blockId);

    @Query("SELECT bi FROM BlockImage bi WHERE bi.block.topic.id = :topicId AND (bi.filePath IS NULL OR bi.filePath = '')")
    List<BlockImage> findPendingImagesByTopicId(@Param("topicId") Long topicId);
}