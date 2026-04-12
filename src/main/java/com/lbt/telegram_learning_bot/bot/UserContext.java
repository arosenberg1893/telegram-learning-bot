package com.lbt.telegram_learning_bot.bot;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserContext {
    private Long currentCourseId;
    private Long currentSectionId;
    private Long currentTopicId;
    private Long currentBlockId;
    private Integer currentPage = 0;
    private String previousMenuState;
    private String searchQuery;
    private boolean testMode;
    private List<Long> testQuestionIds = new ArrayList<>();
    private int currentTestQuestionIndex;
    private Long editingCourseId;
    private Long editingSectionId;
    private Long editingTopicId;
    private List<PendingImage> pendingImages = new ArrayList<>();
    private String testType;
    private List<String> pendingImageDescriptions = new ArrayList<>();
    private int currentImageIndex;
    private Long targetEntityId;
    private String targetEntityType;
    private List<Long> currentTopicBlockIds = new ArrayList<>();
    private int currentBlockIndex;
    private int currentBlockQuestionIndex;
    private int correctAnswers;
    private int wrongAnswers;
    private List<Long> currentBlockQuestionIds = new ArrayList<>();
    private String coursesListSource; // источник для возврата из разделов
    private Integer lastInteractiveMessageId; // ID последнего сообщения с клавиатурой
    private String userName; // имя пользователя
    private List<Integer> lastMediaMessageIds = new ArrayList<>();
    private Integer previousSectionPage = 0;   // страница списка разделов, откуда пришли
    private Integer previousTopicPage = 0;     // страница списка тем, откуда пришли
    private Integer adminSectionsPage = 0;   // текущая страница списка разделов при редактировании
    private Integer adminTopicsPage = 0;     // текущая страница списка тем при редактировании
    private Integer previousCoursesPage = 0; // страница списка курсов, откуда пришли в тест
    private Long currentPlatformUserId; // ID пользователя на текущей платформе (для отправки сообщений)
    private String selectedBackupFileName;  // имя выбранной резервной копии для восстановления

    public Long getCurrentPlatformUserId() {
        return currentPlatformUserId;
    }

    public void setCurrentPlatformUserId(Long currentPlatformUserId) {
        this.currentPlatformUserId = currentPlatformUserId;
    }

}