package com.lbt.telegram_learning_bot.bot;

import com.lbt.telegram_learning_bot.bot.handler.AdminHandler;
import com.lbt.telegram_learning_bot.bot.handler.BaseHandler;
import com.lbt.telegram_learning_bot.bot.handler.CourseNavigationHandler;
import com.lbt.telegram_learning_bot.bot.handler.TestHandler;
import com.lbt.telegram_learning_bot.repository.AdminUserRepository;
import com.lbt.telegram_learning_bot.service.NavigationService;
import com.lbt.telegram_learning_bot.service.PdfExportService;
import com.lbt.telegram_learning_bot.service.RateLimiterService;
import com.lbt.telegram_learning_bot.service.UserSessionService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.GetChat;
import com.pengrad.telegrambot.request.SendDocument;
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

    private final AdminUserRepository adminUserRepository;
    private final PdfExportService pdfExportService;
    private final NavigationService navigationService; // если не унаследовано
    private final TelegramBot telegramBot;
    private final UserSessionService sessionService;
    private final CourseNavigationHandler courseNavHandler;
    private final TestHandler testHandler;
    private final AdminHandler adminHandler;
    @Value("${message.max-length:2000}")
    private int maxMessageLength;
    private final RateLimiterService rateLimiterService;

    @PostConstruct
    public void init() {
        log.info("TelegramBotHandler (dispatcher) initialized");
    }

    public TelegramBotHandler(TelegramBot telegramBot,
                              UserSessionService sessionService,
                              NavigationService navigationService,
                              AdminUserRepository adminUserRepository,
                              PdfExportService pdfExportService,
                              CourseNavigationHandler courseNavHandler,
                              RateLimiterService rateLimiterService,
                              TestHandler testHandler,
                              AdminHandler adminHandler) {
        super(telegramBot, sessionService, navigationService, adminUserRepository);
        this.telegramBot = telegramBot;
        this.sessionService = sessionService;
        this.navigationService = navigationService;
        this.adminUserRepository = adminUserRepository;
        this.pdfExportService = pdfExportService;
        this.courseNavHandler = courseNavHandler;
        this.testHandler = testHandler;
        this.adminHandler = adminHandler;
        this.rateLimiterService = rateLimiterService;
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
        Long userId = message.from().id();

        // Rate limiting проверка
        if (!rateLimiterService.isAllowed(userId)) {
            sendMessage(userId, TOO_MANY_REQUEST);
            return;
        }

        String text = message.text();
        String firstName = message.from().firstName();

        // Проверка длины текста
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

        switch (currentState) {
            case MAIN_MENU:
                break;
            case AWAITING_SEARCH_QUERY:
                courseNavHandler.handleSearchQuery(userId, text);
                break;
            default:
                sendMainMenu(userId, message.messageId());
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        Long userId = callbackQuery.from().id();

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

        switch (action) {
            // навигация
            case CALLBACK_MY_COURSES:
                courseNavHandler.handleMyCourses(userId, messageId, 0);
                break;
            case CALLBACK_ALL_COURSES:
                courseNavHandler.handleAllCourses(userId, messageId, 0);
                break;
            case CALLBACK_SEARCH_COURSES:
                courseNavHandler.promptSearch(userId, messageId);
                break;
            case CALLBACK_COURSES_PAGE:
                courseNavHandler.handleCoursesPage(userId, messageId, parts[1], Integer.parseInt(parts[2]));
                break;
            case CALLBACK_SELECT_COURSE:
                courseNavHandler.handleSelectCourse(userId, messageId, Long.parseLong(parts[1]));
                break;
            case CALLBACK_SELECT_SECTION:
                courseNavHandler.handleSelectSection(userId, messageId, Long.parseLong(parts[1]));
                break;
            case CALLBACK_SELECT_TOPIC:
                courseNavHandler.handleSelectTopic(userId, messageId, Long.parseLong(parts[1]));
                break;
            case CALLBACK_SECTIONS_PAGE:
                courseNavHandler.handleSectionsPage(userId, messageId, Long.parseLong(parts[1]), Integer.parseInt(parts[2]));
                break;
            case CALLBACK_TOPICS_PAGE:
                courseNavHandler.handleTopicsPage(userId, messageId, Long.parseLong(parts[1]), Integer.parseInt(parts[2]));
                break;
            case CALLBACK_BACK_TO_COURSES:
                BotState state = sessionService.getCurrentState(userId);
                if (isAdminState(state)) {
                    adminHandler.handleBackToCoursesFromEdit(userId, messageId);
                } else {
                    courseNavHandler.handleBackToCourses(userId, messageId);
                }
                break;
            case CALLBACK_BACK_TO_SECTIONS:
                courseNavHandler.handleBackToSections(userId, messageId);
                break;
            case CALLBACK_BACK_TO_TOPICS:
                courseNavHandler.handleBackToTopics(userId, messageId);
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
                adminHandler.promptEditCourse(userId, messageId);
                break;
            case CALLBACK_DELETE_COURSE:
                if (!isAdmin(userId)) return;
                adminHandler.promptDeleteCourse(userId, messageId);
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
                BotState currentState = sessionService.getCurrentState(userId);
                if (currentState == BotState.AWAITING_COURSE_JSON) {
                    adminHandler.promptCreateCourse(userId, messageId);
                } else if (currentState == BotState.AWAITING_IMAGE) {
                    adminHandler.promptCurrentImage(userId, messageId);
                }
                break;
            case CALLBACK_ADMIN_COURSES_PAGE:
                if (!isAdmin(userId)) return;
                adminHandler.handleAdminCoursesPage(userId, messageId, parts[1], Integer.parseInt(parts[2]));
                break;
            case CALLBACK_ADMIN_SECTIONS_PAGE:
                if (!isAdmin(userId)) return;
                adminHandler.handleAdminSectionsPage(userId, messageId, Long.parseLong(parts[1]), Integer.parseInt(parts[2]));
                break;
            case CALLBACK_ADMIN_TOPICS_PAGE:
                if (!isAdmin(userId)) return;
                adminHandler.handleAdminTopicsPage(userId, messageId, Long.parseLong(parts[1]), Integer.parseInt(parts[2]));
                break;
            case CALLBACK_ADMIN_BACK_TO_SECTIONS:
                if (!isAdmin(userId)) return;
                adminHandler.handleBackToSectionsFromEdit(userId, messageId);
                break;
            case CALLBACK_ADMIN_BACK_TO_TOPICS:
                if (!isAdmin(userId)) return;
                adminHandler.handleBackToTopicsFromEdit(userId, messageId);
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
            default:
                log.warn("Unknown callback action: {}", action);
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
                courseNavHandler.handleBackToCourses(userId, messageId);
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

            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                    new InlineKeyboardButton(BUTTON_BACK).callbackData(CALLBACK_STATISTICS_BACK)
            );

            SendDocument request = new SendDocument(userId, pdfBytes)
                    .fileName(fileName)
                    .caption(STATS_PDF_CAPTION)
                    .replyMarkup(keyboard);
            telegramBot.execute(request);

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