package com.lbt.telegram_learning_bot.bot;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Контекст навигации пользователя по учебным материалам.
 *
 * <p>Хранит текущее положение пользователя в иерархии курс → раздел → тема → блок,
 * историю страниц для корректного возврата назад, а также источник списка курсов.
 * Является вложенным объектом {@link UserContext} и прозрачно сериализуется Jackson.</p>
 */
@Data
public class NavigationContext {

    // --- Текущая позиция в иерархии ---
    private Long currentCourseId;
    private Long currentSectionId;
    private Long currentTopicId;
    private Long currentBlockId;

    // --- Блоки и вопросы внутри темы ---
    private List<Long> currentTopicBlockIds = new ArrayList<>();
    private int currentBlockIndex;
    private List<Long> currentBlockQuestionIds = new ArrayList<>();
    private int currentBlockQuestionIndex;

    // --- Состояние страниц (для корректного возврата) ---
    private Integer currentPage = 0;
    private Integer previousSectionPage = 0;
    private Integer previousTopicPage = 0;
    private Integer previousCoursesPage = 0;

    // --- Источник и поиск ---
    private String coursesListSource;
    private String searchQuery;
    private String previousMenuState;

    // --- UI ---
    private Integer lastInteractiveMessageId;
    private List<Integer> lastMediaMessageIds = new ArrayList<>();
}
