package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.QuestionImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import static com.lbt.telegram_learning_bot.util.Constants.*;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

@Repository
public interface QuestionImageRepository extends JpaRepository<QuestionImage, Long> {
    List<QuestionImage> findByQuestionIdOrderByOrderIndexAsc(Long questionId);

    @Query("SELECT qi FROM QuestionImage qi WHERE qi.question.block.topic.id = :topicId AND (qi.filePath IS NULL OR qi.filePath = '')")
    List<QuestionImage> findPendingImagesByTopicId(@Param("topicId") Long topicId);
}