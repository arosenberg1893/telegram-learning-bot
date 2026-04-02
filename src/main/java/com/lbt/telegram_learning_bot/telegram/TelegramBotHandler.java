package com.lbt.telegram_learning_bot.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.bot.handler.*;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.platform.Platform;
import com.lbt.telegram_learning_bot.repository.*;
import com.lbt.telegram_learning_bot.repository.UserStudyTimeRepository;
import com.lbt.telegram_learning_bot.service.*;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.GetChat;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
@Component
public class TelegramBotHandler extends BaseHandler {

    private final PdfExportService pdfExportService;
    private final AccountLinkService accountLinkService;
    private final LinkHandler linkHandler;
    private final CourseNavigationHandler courseNavHandler;
    private final TestHandler testHandler;
    private final AdminHandler adminHandler;
    private final RateLimiterService rateLimiterService;
    private final SettingsHandler settingsHandler;
    private final TelegramBot telegramBot;
    private final KeyboardBuilder keyboardBuilder;
    private final ObjectMapper objectMapper;
    private final UserProgressCleanupService progressCleanupService;
    private final UserLockService userLockService;

    @Value("${message.max-length:2000}")
    private int maxMessageLength;

    @PostConstruct
    public void init() {
        log.info("TelegramBotHandler (dispatcher) initialized");
    }

    public TelegramBotHandler(TelegramBot telegramBot,
                              UserSessionService sessionService,
                              NavigationService navigationService,
                              AdminUserRepository adminUserRepository,
                              UserSettingsService userSettingsService,
                              PdfExportService pdfExportService,
                              RateLimiterService rateLimiterService,
                              AccountLinkService accountLinkService,
                              LinkHandler linkHandler,
                              KeyboardBuilder keyboardBuilder,
                              QuestionRepository questionRepository,
                              AnswerOptionRepository answerOptionRepository,
                              UserProgressRepository userProgressRepository,
                              UserMistakeRepository userMistakeRepository,
                              UserTestResultRepository userTestResultRepository,
                              CourseImportService courseImportService,
                              CourseRepository courseRepository,
                              SectionRepository sectionRepository,
                              TopicRepository topicRepository,
                              BlockRepository blockRepository,
                              BlockImageRepository blockImageRepository,
                              QuestionImageRepository questionImageRepository,
                              ObjectMapper objectMapper,
                              UserProgressCleanupService progressCleanupService,
                              UserStudyTimeRepository userStudyTimeRepository,
                              UserLockService userLockService) {
        super(new TelegramMessageSender(telegramBot), sessionService, navigationService, adminUserRepository, userSettingsService);
        this.telegramBot = telegramBot;
        this.pdfExportService = pdfExportService;
        this.rateLimiterService = rateLimiterService;
        this.accountLinkService = accountLinkService;
        this.linkHandler = linkHandler;
        this.keyboardBuilder = keyboardBuilder;
        this.objectMapper = objectMapper;
        this.progressCleanupService = progressCleanupService;
        this.userLockService = userLockService;

        // Создаём зависимые хендлеры
        this.courseNavHandler = new CourseNavigationHandler(messageSender, sessionService, navigationService, adminUserRepository, keyboardBuilder, userSettingsService);
        this.testHandler = new TestHandler(messageSender, sessionService, navigationService, questionRepository, adminUserRepository, answerOptionRepository, userProgressRepository, userMistakeRepository, userTestResultRepository, courseNavHandler, userSettingsService);
        this.adminHandler = new AdminHandler(messageSender, new TelegramFileDownloader(telegramBot), sessionService, navigationService, courseImportService, courseRepository, keyboardBuilder, sectionRepository, topicRepository, blockRepository, questionRepository, answerOptionRepository, blockImageRepository, questionImageRepository, adminUserRepository, userProgressRepository, userStudyTimeRepository, objectMapper, userSettingsService);
        this.settingsHandler = new SettingsHandler(messageSender, sessionService, navigationService, adminUserRepository, userSettingsService, progressCleanupService);
    }

    private boolean isAdminState(BotState state) {
        return state == BotState.EDIT_COURSE_SECTION_CHOOSE ||
                state == BotState.EDIT_SECTION_CHOOSE_TOPIC ||
                state == BotState.EDIT_COURSE_NAME_DESC ||
                state == BotState.EDIT_SECTION_NAME_DESC ||
                state == BotState.EDIT_TOPIC_JSON ||
                state == BotState.AWAITING_IMAGE ||
                state == BotState.AWAITING_COURSE_JSON;
    }

    public void handle(Update update) {
        if (update.message() != null) {
            handleMessage(update.message());
        } else if (update.callbackQuery() != null) {
            handleCallback(update.callbackQuery());
        }
    }

    private void handleMessage(Message message) {
        Long externalUserId = message.from().id();
        Long userId = accountLinkService.resolveInternalUserId(Platform.TELEGRAM, externalUserId);
        synchronized (userLockService.getLock(userId)) {
            if (!rateLimiterService.isAllowed(userId)) {
                sendMessage(userId, TOO_MANY_REQUEST);
                return;
            }

            String text = message.text();
            String firstName = message.from().firstName();

            if (text != null && text.length() > maxMessageLength) {
                sendMessage(userId, "⚠️ Сообщение слишком длинное. Пожалуйста, сократите до " + maxMessageLength + " символов.");
                return;
            }

            if (message.document() != null) {
                adminHandler.handleDocument(userId, message);
                return;
            }
            if (message.photo() != null && message.photo().length > 0) {
                adminHandler.handleImageUpload(userId, message);
                return;
            }
            if (text == null) return;

            BotState currentState = sessionService.getCurrentState(userId);

            if (text.equals("/start")) {
                UserContext context = sessionService.getCurrentContext(userId);
                context.setUserName(firstName);
                sessionService.updateSessionContext(userId, context);
                sendMainMenu(userId, null);
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
                return;
            }

            if (text.startsWith("/link")) {
                String[] parts = text.split("\\s+", 2);
                if (parts.length == 2 && !parts[1].isBlank()) {
                    linkHandler.applyCode(userId, parts[1], Platform.TELEGRAM, externalUserId,
                            new TelegramMessageSenderAdapter(telegramBot, sessionService, userId));
                } else {
                    linkHandler.generateCode(userId, Platform.TELEGRAM,
                            new TelegramMessageSenderAdapter(telegramBot, sessionService, userId));
                }
                return;
            }

            switch (currentState) {
                case MAIN_MENU:
                    break;
                case AWAITING_SEARCH_QUERY:
                    int pageSize = userSettingsService.getSettings(userId).getPageSize();
                    courseNavHandler.handleSearchQuery(userId, text, pageSize);
                    break;
                case AWAITING_LINK_CODE:
                    linkHandler.applyCode(userId, text, Platform.TELEGRAM, externalUserId,
                            new TelegramMessageSenderAdapter(telegramBot, sessionService, userId));
                    sessionService.updateSessionState(userId, BotState.MAIN_MENU);
                    break;
                default:
                    sendMainMenu(userId, message.messageId());
                    sessionService.updateSessionState(userId, BotState.MAIN_MENU);
            }
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        Long externalUserId = callbackQuery.from().id();
        Long userId = accountLinkService.resolveInternalUserId(Platform.TELEGRAM, externalUserId);
        synchronized (userLockService.getLock(userId)) {
            if (!rateLimiterService.isAllowed(userId)) {
                try {
                    telegramBot.execute(new AnswerCallbackQuery(callbackQuery.id())
                            .text(TOO_MANY_REQUEST)
                            .showAlert(true));
                } catch (Exception e) {
                    log.error("Failed to answer callback query", e);
                }
                return;
            }
            String data = callbackQuery.data();
            Integer messageId = callbackQuery.message().messageId();
            log.debug("Callback from user {}: {}", userId, data);

            String[] parts = data.split(":", 3);
            String action = parts[0];
            int pageSize = userSettingsService.getSettings(userId).getPageSize();

            switch (action) {
                // навигация
                case CALLBACK_MY_COURSES:
                    courseNavHandler.handleMyCourses(userId, messageId, 0, pageSize);
                    break;
                case CALLBACK_ALL_COURSES:
                    courseNavHandler.handleAllCourses(userId, messageId, 0, pageSize);
                    break;
                case CALLBACK_SEARCH_COURSES:
                    courseNavHandler.promptSearch(userId, messageId);
                    break;
                case CALLBACK_COURSES_PAGE:
                    courseNavHandler.handleCoursesPage(userId, messageId, parts[1], Integer.parseInt(parts[2]), pageSize);
                    break;
                case CALLBACK_SELECT_COURSE:
                    courseNavHandler.handleSelectCourse(userId, messageId, Long.parseLong(parts[1]), pageSize);
                    break;
                case CALLBACK_SELECT_SECTION:
                    courseNavHandler.handleSelectSection(userId, messageId, Long.parseLong(parts[1]), pageSize);
                    break;
                case CALLBACK_SELECT_TOPIC:
                    courseNavHandler.handleSelectTopic(userId, messageId, Long.parseLong(parts[1]));
                    break;
                case CALLBACK_SECTIONS_PAGE:
                    courseNavHandler.handleSectionsPage(userId, messageId, Long.parseLong(parts[1]), Integer.parseInt(parts[2]), pageSize);
                    break;
                case CALLBACK_TOPICS_PAGE:
                    courseNavHandler.handleTopicsPage(userId, messageId, Long.parseLong(parts[1]), Integer.parseInt(parts[2]), pageSize);
                    break;
                case CALLBACK_BACK_TO_COURSES:
                    BotState state = sessionService.getCurrentState(userId);
                    if (isAdminState(state)) {
                        adminHandler.handleBackToCoursesFromEdit(userId, messageId, pageSize);
                    } else {
                        courseNavHandler.handleBackToCourses(userId, messageId, pageSize);
                    }
                    break;
                case CALLBACK_BACK_TO_SECTIONS:
                    courseNavHandler.handleBackToSections(userId, messageId, pageSize);
                    break;
                case CALLBACK_BACK_TO_TOPICS:
                    courseNavHandler.handleBackToTopics(userId, messageId, pageSize);
                    break;
                case CALLBACK_NEXT_BLOCK:
                    courseNavHandler.handleNextBlock(userId, messageId);
                    break;
                case CALLBACK_PREV_BLOCK:
                    courseNavHandler.handlePrevBlock(userId, messageId);
                    break;
                case CALLBACK_NEXT_QUESTION:
                    testHandler.handleNextQuestion(userId, messageId);
                    break;
                case CALLBACK_PREV_QUESTION:
                    testHandler.handlePrevQuestion(userId, messageId);
                    break;
                case CALLBACK_ANSWER:
                    testHandler.handleAnswer(userId, messageId, Long.parseLong(parts[1]), Long.parseLong(parts[2]));
                    break;
                case CALLBACK_BACK_TO_BLOCK_TEXT:
                    testHandler.handleBackToBlockText(userId, messageId);
                    break;

                // тесты
                case CALLBACK_TEST_TOPIC:
                    testHandler.handleTestTopic(userId, messageId, Long.parseLong(parts[1]));
                    break;
                case CALLBACK_TEST_SECTION:
                    testHandler.handleTestSection(userId, messageId, Long.parseLong(parts[1]));
                    break;
                case CALLBACK_TEST_COURSE:
                    testHandler.handleTestCourse(userId, messageId, Long.parseLong(parts[1]));
                    break;

                // администрирование
                case CALLBACK_CREATE_COURSE:
                    if (!isAdmin(userId)) return;
                    adminHandler.promptCreateCourse(userId, messageId);
                    break;
                case CALLBACK_EDIT_COURSE:
                    if (!isAdmin(userId)) return;
                    adminHandler.promptEditCourse(userId, messageId, pageSize);
                    break;
                case CALLBACK_DELETE_COURSE:
                    if (!isAdmin(userId)) return;
                    adminHandler.promptDeleteCourse(userId, messageId, pageSize);
                    break;
                case CALLBACK_SELECT_COURSE_FOR_EDIT:
                    if (!isAdmin(userId)) return;
                    adminHandler.handleSelectCourseForEdit(userId, messageId, Long.parseLong(parts[1]));
                    break;
                case CALLBACK_SELECT_COURSE_FOR_DELETE:
                    if (!isAdmin(userId)) return;
                    adminHandler.handleSelectCourseForDelete(userId, messageId, Long.parseLong(parts[1]));
                    break;
                case CALLBACK_EDIT_COURSE_ACTION:
                    if (!isAdmin(userId)) return;
                    adminHandler.handleEditCourseAction(userId, messageId, parts[1]);
                    break;
                case CALLBACK_SELECT_SECTION_FOR_EDIT:
                    if (!isAdmin(userId)) return;
                    adminHandler.handleSelectSectionForEdit(userId, messageId, Long.parseLong(parts[1]));
                    break;
                case CALLBACK_EDIT_SECTION_ACTION:
                    if (!isAdmin(userId)) return;
                    adminHandler.handleEditSectionAction(userId, messageId, parts[1]);
                    break;
                case CALLBACK_SELECT_TOPIC_FOR_EDIT:
                    if (!isAdmin(userId)) return;
                    adminHandler.handleSelectTopicForEdit(userId, messageId, Long.parseLong(parts[1]));
                    break;
                case CALLBACK_CONFIRM_DELETE_COURSE:
                    if (!isAdmin(userId)) return;
                    adminHandler.handleConfirmDeleteCourse(userId, messageId, Long.parseLong(parts[1]));
                    break;
                case CALLBACK_RETRY:
                    adminHandler.handleRetry(userId, messageId);
                    break;
                case CALLBACK_ADMIN_COURSES_PAGE:
                    if (!isAdmin(userId)) return;
                    adminHandler.handleAdminCoursesPage(userId, messageId, parts[1], Integer.parseInt(parts[2]), pageSize);
                    break;
                case CALLBACK_ADMIN_SECTIONS_PAGE:
                    if (!isAdmin(userId)) return;
                    adminHandler.handleAdminSectionsPage(userId, messageId, Long.parseLong(parts[1]), Integer.parseInt(parts[2]), pageSize);
                    break;
                case CALLBACK_ADMIN_TOPICS_PAGE:
                    if (!isAdmin(userId)) return;
                    adminHandler.handleAdminTopicsPage(userId, messageId, Long.parseLong(parts[1]), Integer.parseInt(parts[2]), pageSize);
                    break;
                case CALLBACK_ADMIN_BACK_TO_SECTIONS:
                    if (!isAdmin(userId)) return;
                    adminHandler.handleBackToSectionsFromEdit(userId, messageId, pageSize);
                    break;
                case CALLBACK_ADMIN_BACK_TO_TOPICS:
                    if (!isAdmin(userId)) return;
                    if (parts.length >= 3) {
                        Long sectionId = Long.parseLong(parts[1]);
                        int page = Integer.parseInt(parts[2]);
                        adminHandler.handleBackToTopicsFromEdit(userId, messageId, sectionId, page, pageSize);
                    } else {
                        adminHandler.handleBackToTopicsFromEdit(userId, messageId, pageSize);
                    }
                    break;

                // статистика и ошибки
                case CALLBACK_STATISTICS:
                    if (parts.length > 1 && CALLBACK_BACK.equals(parts[1])) {
                        deleteMessage(userId, messageId);
                        showStatistics(userId, null);
                    } else {
                        showStatistics(userId, messageId);
                    }
                    break;
                case CALLBACK_EXPORT_PDF:
                    handleExportPdf(userId, messageId);
                    break;
                case CALLBACK_MY_MISTAKES:
                    testHandler.handleMyMistakes(userId, messageId);
                    break;

                // общие
                case CALLBACK_MAIN_MENU:
                    sendMainMenu(userId, messageId);
                    sessionService.updateSessionState(userId, BotState.MAIN_MENU);
                    break;
                case CALLBACK_BACK:
                    handleBack(userId, messageId);
                    break;

                case CALLBACK_SETTINGS:
                    settingsHandler.showSettingsMenu(userId, messageId);
                    break;
                case CALLBACK_SETTINGS_SHUFFLE:
                    settingsHandler.toggleShuffle(userId, messageId);
                    break;
                case CALLBACK_SETTINGS_PAGESIZE:
                    settingsHandler.showPageSizeOptions(userId, messageId);
                    break;
                case CALLBACK_SETTINGS_QUESTIONS:
                    settingsHandler.showQuestionsPerBlockOptions(userId, messageId);
                    break;
                case CALLBACK_SETTINGS_EXPLANATIONS:
                    settingsHandler.toggleExplanations(userId, messageId);
                    break;
                case CALLBACK_SETTINGS_NOTIFICATIONS:
                    settingsHandler.toggleNotifications(userId, messageId);
                    break;
                case CALLBACK_SETTINGS_RESET:
                    settingsHandler.confirmResetProgress(userId, messageId);
                    break;
                case CALLBACK_SETTINGS_PAGESIZE_SET:
                    if (parts.length >= 2) {
                        int size = Integer.parseInt(parts[1]);
                        settingsHandler.setPageSize(userId, messageId, size);
                    }
                    break;
                case CALLBACK_SETTINGS_QUESTIONS_SET:
                    if (parts.length >= 2) {
                        int count = Integer.parseInt(parts[1]);
                        settingsHandler.setQuestionsPerBlock(userId, messageId, count);
                    }
                    break;
                case CALLBACK_SETTINGS_RESET_CONFIRM:
                    settingsHandler.resetProgress(userId, messageId);
                    break;
                case CALLBACK_LINK_GENERATE:
                    linkHandler.generateCode(userId, Platform.TELEGRAM,
                            new TelegramMessageSenderAdapter(telegramBot, sessionService, userId));
                    break;
                case CALLBACK_LINK_RESOLVE_KEEP_THIS:
                    if (parts.length >= 2) {
                        linkHandler.resolveConflictKeepThis(userId, parts[1], Platform.TELEGRAM, externalUserId,
                                new TelegramMessageSenderAdapter(telegramBot, sessionService, userId));
                    }
                    break;
                case CALLBACK_LINK_RESOLVE_KEEP_OTHER:
                    if (parts.length >= 2) {
                        linkHandler.resolveConflictKeepOther(userId, parts[1], Platform.TELEGRAM, externalUserId,
                                new TelegramMessageSenderAdapter(telegramBot, sessionService, userId));
                    }
                    break;
                default:
                    log.warn("Unknown callback action: {}", action);
            }
        }
    }

    private void handleBack(Long userId, Integer messageId) {
        BotState currentState = sessionService.getCurrentState(userId);
        switch (currentState) {
            case MY_COURSES:
            case ALL_COURSES:
            case SEARCH_COURSES:
                sendMainMenu(userId, messageId);
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
                break;
            case COURSE_SECTIONS:
            case SECTION_TOPICS:
            case TOPIC_LEARNING:
                int pageSize = userSettingsService.getSettings(userId).getPageSize();
                courseNavHandler.handleBackToCourses(userId, messageId, pageSize);
                break;
            default:
                sendMainMenu(userId, messageId);
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
        }
    }

    private void showStatistics(Long userId, Integer messageId) {
        long totalCourses = navigationService.getTotalStartedCourses(userId);
        long completedCourses = navigationService.getCompletedCoursesCount(userId);
        String hardestCourse = navigationService.getHardestCourse(userId);

        StringBuilder stats = new StringBuilder();
        stats.append(STATS_TITLE).append("\n\n");
        stats.append(String.format(STATS_TOTAL_COURSES, totalCourses));
        stats.append(String.format(STATS_COMPLETED, completedCourses));
        stats.append(String.format(STATS_HARDEST, hardestCourse));
        long totalSeconds = navigationService.getTotalStudySecondsForUser(userId);
        String totalTimeStr = formatStudyTime(totalSeconds);
        stats.append(String.format(STATS_TOTAL_TIME, totalTimeStr));
        stats.append(STATS_PROGRESS);

        List<String> courseProgress = navigationService.getCoursesProgress(userId);

        if (courseProgress.isEmpty()) {
            stats.append(STATS_NO_DATA);
        } else {
            for (String line : courseProgress) {
                stats.append(line).append("\n");
            }
        }

        if (messageId != null) {
            editMessage(userId, messageId, stats.toString(), createStatisticsKeyboard());
        } else {
            sendMessage(userId, stats.toString(), createStatisticsKeyboard());
        }
    }

    private void handleExportPdf(Long userId, Integer messageId) {
        Integer progressMsgId = sendProgressMessage(userId);
        try {
            UserContext context = sessionService.getCurrentContext(userId);
            String userName = context.getUserName();
            if (userName == null) {
                try {
                    var chat = telegramBot.execute(new GetChat(userId)).chat();
                    userName = chat.firstName();
                    if (userName == null) userName = DEFAULT_USER_NAME;
                    context.setUserName(userName);
                    sessionService.updateSessionContext(userId, context);
                } catch (Exception ex) {
                    userName = DEFAULT_USER_NAME;
                }
            }
            log.info("Export PDF: userName = {}", userName);

            byte[] pdfBytes = pdfExportService.generateStatisticsPdf(userId, userName);

            String fileName = String.format("Статистика обучения %s на %s.pdf",
                    userName,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH-mm")));

            BotKeyboard keyboard = new BotKeyboard().addRow(BotButton.callback(BUTTON_BACK, CALLBACK_STATISTICS_BACK));

            messageSender.sendDocument(userId, pdfBytes, fileName, STATS_PDF_CAPTION, keyboard);

            if (messageId != null) {
                deleteMessage(userId, messageId);
            }
            if (progressMsgId != null) deleteMessage(userId, progressMsgId);
        } catch (Exception e) {
            log.error("Error generating PDF", e);
            if (progressMsgId != null) deleteMessage(userId, progressMsgId);
            sendMessage(userId, MSG_PDF_ERROR, createBackToMainKeyboard());
        }
    }
}