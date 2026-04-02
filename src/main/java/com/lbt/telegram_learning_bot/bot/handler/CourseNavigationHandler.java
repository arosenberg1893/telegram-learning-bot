package com.lbt.telegram_learning_bot.bot.handler;

import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.entity.*;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.repository.AdminUserRepository;
import com.lbt.telegram_learning_bot.service.NavigationService;
import com.lbt.telegram_learning_bot.service.UserSessionService;
import com.lbt.telegram_learning_bot.service.UserSettingsService;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
public class CourseNavigationHandler extends BaseHandler {

    private final KeyboardBuilder keyboardBuilder;

    public CourseNavigationHandler(MessageSender messageSender,
                                   UserSessionService sessionService,
                                   NavigationService navigationService,
                                   AdminUserRepository adminUserRepository,
                                   KeyboardBuilder keyboardBuilder,
                                   UserSettingsService userSettingsService) {
        super(messageSender, sessionService, navigationService, adminUserRepository, userSettingsService);
        this.keyboardBuilder = keyboardBuilder;
    }

    // ================== Публичные методы (все принимают pageSize) ==================

    public void handleMyCourses(Long userId, Integer messageId, int page, int pageSize) {
        log.debug("handleMyCourses: userId={}, messageId={}, page={}, pageSize={}", userId, messageId, page, pageSize);
        showMyCourses(userId, messageId, page, pageSize);
    }

    public void handleSelectTopic(Long userId, Integer messageId, Long topicId) {
        clearMediaMessages(userId);
        UserContext context = sessionService.getCurrentContext(userId);

        context.setPreviousTopicPage(context.getCurrentPage());

        List<Block> blocks = navigationService.getTopicBlocksWithQuestions(topicId);
        if (blocks.isEmpty()) {
            String text = MSG_TOPIC_NO_BLOCKS;
            BotKeyboard keyboard = new BotKeyboard().addRow(BotButton.callback(BUTTON_BACK, CALLBACK_BACK_TO_TOPICS));
            if (messageId != null) {
                editMessage(userId, messageId, text, keyboard);
            } else {
                sendMessage(userId, text, keyboard);
            }
            return;
        }

        List<Long> blockIds = blocks.stream().map(Block::getId).toList();
        context.setCurrentTopicBlockIds(blockIds);
        context.setCurrentBlockIndex(0);
        context.setCurrentBlockQuestionIndex(-1);
        context.setCurrentTopicId(topicId);
        context.setTestMode(false);
        context.setPreviousMenuState(sessionService.getCurrentState(userId).name());
        context.setCorrectAnswers(0);
        context.setWrongAnswers(0);

        Long firstBlockId = blockIds.get(0);
        List<Question> questions = navigationService.getQuestionsForBlock(firstBlockId);
        context.setCurrentBlockQuestionIds(questions.stream().map(Question::getId).toList());

        sessionService.updateSession(userId, BotState.TOPIC_LEARNING, context);
        showBlockContent(userId, messageId, firstBlockId);
    }

    public void handleNextBlock(Long userId, Integer messageId) {
        UserContext context = sessionService.getCurrentContext(userId);
        int currentIdx = context.getCurrentBlockIndex();
        List<Long> blockIds = context.getCurrentTopicBlockIds();

        if (currentIdx < blockIds.size() - 1) {
            Long nextBlockId = blockIds.get(currentIdx + 1);
            List<Question> questions = navigationService.getQuestionsForBlock(nextBlockId);
            context.setCurrentBlockQuestionIds(questions.stream().map(Question::getId).toList());
            context.setCurrentBlockIndex(currentIdx + 1);
            context.setCurrentBlockQuestionIndex(-1);
            sessionService.updateSessionContext(userId, context);
            showBlockContent(userId, messageId, nextBlockId);
        } else {
            int correct = context.getCorrectAnswers();
            int wrong = context.getWrongAnswers();
            int total = correct + wrong;
            String stats = String.format(FORMAT_TOPIC_COMPLETED, correct, wrong, total);

            BotKeyboard keyboard = new BotKeyboard().addRow(BotButton.callback(BUTTON_BACK, CALLBACK_BACK_TO_TOPICS));

            if (messageId != null) {
                editMessage(userId, messageId, stats, keyboard);
            } else {
                sendMessage(userId, stats, keyboard);
            }

            context.setCorrectAnswers(0);
            context.setWrongAnswers(0);
            sessionService.updateSessionContext(userId, context);
        }
    }

    public void handlePrevBlock(Long userId, Integer messageId) {
        UserContext context = sessionService.getCurrentContext(userId);
        int currentIdx = context.getCurrentBlockIndex();

        if (currentIdx > 0) {
            Long prevBlockId = context.getCurrentTopicBlockIds().get(currentIdx - 1);
            List<Question> questions = navigationService.getQuestionsForBlock(prevBlockId);
            context.setCurrentBlockQuestionIds(questions.stream().map(Question::getId).toList());
            context.setCurrentBlockIndex(currentIdx - 1);
            context.setCurrentBlockQuestionIndex(-1);
            sessionService.updateSessionContext(userId, context);
            showBlockContent(userId, messageId, prevBlockId);
        } else {
            int pageSize = userSettingsService.getSettings(userId).getPageSize();
            handleBackToSections(userId, messageId, pageSize);
        }
    }

    public void promptSearch(Long userId, Integer messageId) {
        String text = MSG_SEARCH_PROMPT;
        if (messageId != null) {
            editMessage(userId, messageId, text, createCancelKeyboard());
        } else {
            sendMessage(userId, text, createCancelKeyboard());
        }
        sessionService.updateSessionState(userId, BotState.AWAITING_SEARCH_QUERY);
    }

    public void handleAllCourses(Long userId, Integer messageId, int page, int pageSize) {
        showAllCourses(userId, messageId, page, pageSize);
    }

    public void handleCoursesPage(Long userId, Integer messageId, String source, int page, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setCurrentPage(page);
        sessionService.updateSessionContext(userId, context);

        switch (source) {
            case SOURCE_MY_COURSES:
                showMyCourses(userId, messageId, page, pageSize);
                break;
            case SOURCE_ALL_COURSES:
                showAllCourses(userId, messageId, page, pageSize);
                break;
            case SOURCE_SEARCH:
                String query = context.getSearchQuery();
                var result = navigationService.getFoundCoursesPage(query, page, pageSize);
                String text = String.format(FORMAT_SEARCH_RESULTS,
                        query, page + 1, result.getTotalPages(), result.getTotalItems());
                BotKeyboard keyboard = keyboardBuilder.buildCoursesKeyboardBot(result, userId, SOURCE_SEARCH, CALLBACK_SELECT_COURSE, true);
                editMessage(userId, messageId, text, keyboard);
                break;
            default:
                sendMainMenu(userId, messageId);
        }
    }

    public void handleSectionsPage(Long userId, Integer messageId, Long courseId, int page, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setCurrentPage(page);
        sessionService.updateSessionContext(userId, context);
        showCourseSections(userId, messageId, courseId, page, pageSize);
    }

    public void handleTopicsPage(Long userId, Integer messageId, Long sectionId, int page, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setCurrentPage(page);
        sessionService.updateSessionContext(userId, context);
        showSectionTopics(userId, messageId, sectionId, page, pageSize);
    }

    public void handleBackToCourses(Long userId, Integer messageId, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        if (context.getCurrentCourseId() != null) {
            navigationService.updateCourseLastAccessedOnExit(userId, context.getCurrentCourseId());
        }
        clearMediaMessages(userId);

        String source = context.getCoursesListSource();
        Integer page = context.getPreviousCoursesPage();
        if (page == null) page = 0;

        if (SOURCE_MY_COURSES.equals(source)) {
            showMyCourses(userId, messageId, page, pageSize);
        } else if (SOURCE_ALL_COURSES.equals(source)) {
            showAllCourses(userId, messageId, page, pageSize);
        } else if (SOURCE_SEARCH.equals(source)) {
            String query = context.getSearchQuery();
            var result = navigationService.getFoundCoursesPage(query, page, pageSize);
            String text = "Результаты поиска (страница " + (page + 1) + "):";
            BotKeyboard keyboard = keyboardBuilder.buildCoursesKeyboardBot(result, userId, SOURCE_SEARCH, CALLBACK_SELECT_COURSE, true);
            editMessage(userId, messageId, text, keyboard);
            sessionService.updateSessionState(userId, BotState.SEARCH_COURSES);
        } else {
            showAllCourses(userId, messageId, page, pageSize);
        }
    }

    public void handleSelectCourse(Long userId, Integer messageId, Long courseId, int pageSize) {
        clearMediaMessages(userId);
        UserContext context = sessionService.getCurrentContext(userId);
        context.setCurrentCourseId(courseId);
        context.setCurrentPage(0);
        BotState prevState = sessionService.getCurrentState(userId);
        context.setPreviousMenuState(prevState.name());
        context.setCoursesListSource(prevState.name());
        sessionService.updateSession(userId, BotState.COURSE_SECTIONS, context);
        showCourseSections(userId, messageId, courseId, 0, pageSize);
    }

    public void handleSelectSection(Long userId, Integer messageId, Long sectionId, int pageSize) {
        clearMediaMessages(userId);
        UserContext context = sessionService.getCurrentContext(userId);
        context.setPreviousSectionPage(context.getCurrentPage());
        context.setCurrentSectionId(sectionId);
        context.setCurrentPage(0);
        context.setPreviousMenuState(sessionService.getCurrentState(userId).name());
        sessionService.updateSession(userId, BotState.SECTION_TOPICS, context);
        showSectionTopics(userId, messageId, sectionId, 0, pageSize);
    }

    public void handleBackToSections(Long userId, Integer messageId, int pageSize) {
        clearMediaMessages(userId);
        UserContext context = sessionService.getCurrentContext(userId);
        if (context.getCurrentCourseId() != null) {
            int page = context.getPreviousSectionPage() != null ? context.getPreviousSectionPage() : 0;
            showCourseSections(userId, messageId, context.getCurrentCourseId(), page, pageSize);
            sessionService.updateSessionState(userId, BotState.COURSE_SECTIONS);
        } else {
            sendMainMenu(userId, messageId);
        }
    }

    public void handleBackToTopics(Long userId, Integer messageId, int pageSize) {
        clearMediaMessages(userId);
        UserContext context = sessionService.getCurrentContext(userId);
        if (context.getCurrentSectionId() != null) {
            int page = context.getPreviousTopicPage() != null ? context.getPreviousTopicPage() : 0;
            showSectionTopics(userId, messageId, context.getCurrentSectionId(), page, pageSize);
            sessionService.updateSessionState(userId, BotState.SECTION_TOPICS);
        } else {
            sendMainMenu(userId, messageId);
        }
    }

    public void handleSearchQuery(Long userId, String query, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setSearchQuery(query);
        sessionService.updateSessionContext(userId, context);
        var result = navigationService.getFoundCoursesPage(query, 0, pageSize);
        if (result.getItems().isEmpty()) {
            sendMessage(userId, "😕 По запросу \"" + query + "\" ничего не найдено.", createSearchNotFoundKeyboard());
            return;
        }
        String text = String.format("🔍 Результаты поиска по запросу «%s» (страница 1 из %d) – найдено %d курсов.",
                query, result.getTotalPages(), result.getTotalItems());
        BotKeyboard keyboard = keyboardBuilder.buildCoursesKeyboardBot(result, userId, SOURCE_SEARCH, CALLBACK_SELECT_COURSE, true);
        sendMessage(userId, text, keyboard);
        sessionService.updateSessionState(userId, BotState.SEARCH_COURSES);
    }

    // ================== Внутренние методы навигации ==================

    private void showMyCourses(Long userId, Integer messageId, int page, int pageSize) {
        log.debug("showMyCourses: userId={}, page={}, pageSize={}", userId, page, pageSize);
        var result = navigationService.getMyCoursesPage(userId, page, pageSize);
        log.debug("result: items={}, totalPages={}, totalItems={}",
                result.getItems().size(), result.getTotalPages(), result.getTotalItems());

        if (result.getItems().isEmpty()) {
            String text = MSG_NO_MY_COURSES;
            if (messageId != null) {
                editMessage(userId, messageId, text, createBackToMainKeyboard());
            } else {
                sendMessage(userId, text, createBackToMainKeyboard());
            }
            return;
        }
        String text = String.format(FORMAT_MY_COURSES_HEADER,
                page + 1, result.getTotalPages(), result.getTotalItems());
        BotKeyboard keyboard = keyboardBuilder.buildCoursesKeyboardBot(result, userId, CALLBACK_MY_COURSES, CALLBACK_SELECT_COURSE, true);
        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    private void showAllCourses(Long userId, Integer messageId, int page, int pageSize) {
        log.debug("showAllCourses: userId={}, page={}, pageSize={}", userId, page, pageSize);
        var result = navigationService.getAllCoursesPage(page, pageSize);
        if (result.getItems().isEmpty()) {
            String text = MSG_NO_COURSES;
            if (messageId != null) {
                editMessage(userId, messageId, text, createBackToMainKeyboard());
            } else {
                sendMessage(userId, text, createBackToMainKeyboard());
            }
            return;
        }
        String text = String.format(FORMAT_ALL_COURSES_HEADER,
                page + 1, result.getTotalPages(), result.getTotalItems());
        BotKeyboard keyboard = keyboardBuilder.buildCoursesKeyboardBot(result, userId, CALLBACK_ALL_COURSES, CALLBACK_SELECT_COURSE, true);
        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    private void showCourseSections(Long userId, Integer messageId, Long courseId, int page, int pageSize) {
        var result = navigationService.getSectionsPage(courseId, page, pageSize);
        String courseTitle = navigationService.getCourseTitle(courseId);
        String courseDescription = navigationService.getCourseDescription(courseId);
        String lastAccessedStr = formatLastAccessed(navigationService.getCourseLastAccessed(userId, courseId));

        if (messageId != null && page == 0) {
            String text = String.format(FORMAT_COURSE_SECTIONS_HEADER,
                    courseTitle, courseDescription, lastAccessedStr, page + 1, result.getTotalPages(), result.getTotalItems());
            BotKeyboard keyboard = keyboardBuilder.buildSectionsKeyboardBot(result, userId, courseId, true, CALLBACK_SELECT_SECTION);
            editMessage(userId, messageId, text, keyboard);
        } else if (messageId != null) {
            String text = String.format(FORMAT_SECTIONS_HEADER,
                    courseTitle, page + 1, result.getTotalPages(), result.getTotalItems(), lastAccessedStr);
            BotKeyboard keyboard = keyboardBuilder.buildSectionsKeyboardBot(result, userId, courseId, true, CALLBACK_SELECT_SECTION);
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, String.format(FORMAT_COURSE_HEADER, courseTitle, courseDescription, lastAccessedStr));
            String text = String.format("📌 **Разделы** курса «%s» (страница %d из %d) – всего %d разделов.\nВыберите раздел.",
                    courseTitle, page + 1, result.getTotalPages(), result.getTotalItems());
            BotKeyboard keyboard = keyboardBuilder.buildSectionsKeyboardBot(result, userId, courseId, true, CALLBACK_SELECT_SECTION);
            sendMessage(userId, text, keyboard);
        }
    }

    private void showSectionTopics(Long userId, Integer messageId, Long sectionId, int page, int pageSize) {
        var result = navigationService.getTopicsPage(sectionId, page, pageSize);
        String sectionTitle = navigationService.getSectionTitle(sectionId);
        String sectionDescription = navigationService.getSectionDescription(sectionId);
        Instant lastAccessed = navigationService.getSectionLastAccessed(userId, sectionId);
        String lastAccessedStr = formatLastAccessed(lastAccessed);

        if (messageId != null && page == 0) {
            String text = String.format(FORMAT_TOPICS_HEADER,
                    sectionTitle, sectionDescription, lastAccessedStr, page + 1, result.getTotalPages(), result.getTotalItems());
            BotKeyboard keyboard = keyboardBuilder.buildTopicsKeyboardBot(result, userId, sectionId, true, CALLBACK_SELECT_TOPIC);
            editMessage(userId, messageId, text, keyboard);
            navigationService.updateSectionLastAccessed(userId, sectionId);
        } else if (messageId != null) {
            String text = String.format(FORMAT_TOPICS_HEADER2,
                    sectionTitle, page + 1, result.getTotalPages(), result.getTotalItems(), lastAccessedStr);
            BotKeyboard keyboard = keyboardBuilder.buildTopicsKeyboardBot(result, userId, sectionId, true, CALLBACK_SELECT_TOPIC);
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, String.format(FORMAT_SECTION_HEADER, sectionTitle, sectionDescription, lastAccessedStr));
            String text = String.format("📌 **Темы** раздела «%s» (страница %d из %d) – всего %d тем.\nВыберите тему.",
                    sectionTitle, page + 1, result.getTotalPages(), result.getTotalItems());
            BotKeyboard keyboard = keyboardBuilder.buildTopicsKeyboardBot(result, userId, sectionId, true, CALLBACK_SELECT_TOPIC);
            sendMessage(userId, text, keyboard);
            if (page == 0) {
                navigationService.updateSectionLastAccessed(userId, sectionId);
            }
        }
    }

    public void showBlockContent(Long userId, Integer messageId, Long blockId) {
        UserContext context = sessionService.getCurrentContext(userId);
        navigationService.getBlockWithImages(blockId).ifPresentOrElse(block -> {
            String text = block.getTextContent();
            BotKeyboard keyboard = keyboardBuilder.buildBlockNavigationKeyboardBot(context);
            if (messageId != null) {
                editMessage(userId, messageId, text, keyboard);
            } else {
                sendMessage(userId, text, keyboard);
            }
            sendMediaGroup(userId, block.getImages());
        }, () -> {
            if (messageId != null) {
                editMessage(userId, messageId, MSG_BLOCK_NOT_FOUND, createBackToMainKeyboard());
            } else {
                sendMessage(userId, MSG_BLOCK_NOT_FOUND, createBackToMainKeyboard());
            }
        });
    }
}