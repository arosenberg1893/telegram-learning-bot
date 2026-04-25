package com.lbt.telegram_learning_bot.bot;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Контекст активной тестовой сессии пользователя.
 *
 * <p>Хранит состояние текущего теста: список вопросов, индекс текущего вопроса,
 * счётчики правильных/неправильных ответов и тип теста (тема/раздел/курс).
 * Является вложенным объектом {@link UserContext} и прозрачно сериализуется Jackson.</p>
 */
@Data
public class TestContext {

    private boolean testMode;
    private String testType;
    private List<Long> testQuestionIds = new ArrayList<>();
    private int currentTestQuestionIndex;
    private int correctAnswers;
    private int wrongAnswers;

    /** Сбрасывает состояние теста перед началом нового. */
    public void reset() {
        testMode = false;
        testType = null;
        testQuestionIds = new ArrayList<>();
        currentTestQuestionIndex = 0;
        correctAnswers = 0;
        wrongAnswers = 0;
    }
}
