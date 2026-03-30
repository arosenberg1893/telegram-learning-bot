package com.lbt.telegram_learning_bot.repository;

import com.lbt.telegram_learning_bot.entity.Question;
import com.lbt.telegram_learning_bot.entity.UserMistake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import static com.lbt.telegram_learning_bot.util.Constants.*;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

@Repository
public interface UserMistakeRepository extends JpaRepository<UserMistake, Long> {

    Optional<UserMistake> findByUserIdAndQuestionId(Long userId, Long questionId);

    @Query("SELECT um.question FROM UserMistake um WHERE um.userId = :userId")
    List<Question> findMistakeQuestionsByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    void deleteByUserIdAndQuestionId(Long userId, Long questionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserMistake um WHERE um.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}