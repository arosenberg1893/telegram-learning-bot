package com.lbt.telegram_learning_bot.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbt.telegram_learning_bot.bot.BotDispatcher;
import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.bot.handler.*;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.platform.Platform;
import com.lbt.telegram_learning_bot.repository.*;
import com.lbt.telegram_learning_bot.service.*;
import com.lbt.telegram_learning_bot.service.cloud.CloudStorageFacade;
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
    private final BotDispatcher dispatcher;
    private final KeyboardBuilder keyboardBuilder;
    private final ObjectMapper objectMapper;
    private final UserProgressCleanupService progressCleanupService;
    private final UserLockService userLockService;
    private final UserSettingsService userSettingsService;
    private final MaterialPdfGenerator materialPdfGenerator;
    private final CloudStorageFacade cloudStorageFacade;
    private final MaintenanceModeService maintenanceModeService;
    private final BackupService backupService;
    private final ZipCourseImportService zipCourseImportService;

    @Value("${message.max-length:2000}")
    private int maxMessageLength;

    @PostConstruct
    public void init() {
        log.info("TelegramBotHandler (dispatcher) initialized");
    }

    public TelegramBotHandler(TelegramBot telegramBot,
                              TelegramMessageSender telegramMessageSender,
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
                              ZipCourseImportService zipCourseImportService,
                              CourseRepository courseRepository,
                              SectionRepository sectionRepository,
                              TopicRepository topicRepository,
                              BlockRepository blockRepository,
                              BlockImageRepository blockImageRepository,
                              QuestionImageRepository questionImageRepository,
                              ObjectMapper objectMapper,
                              UserProgressCleanupService progressCleanupService,
                              UserStudyTimeRepository userStudyTimeRepository,
                              UserLockService userLockService,
                              MaterialPdfGenerator materialPdfGenerator,
                              CloudStorageFacade cloudStorageFacade,
                              MaintenanceModeService maintenanceModeService,
                              BackupService backupService,
                              ImageStorageService imageStorageService) {
        // TelegramMessageSender приходит как Spring-бин, не создаётся вручную
        super(telegramMessageSender, sessionService, navigationService,
                adminUserRepository, userSettingsService, maintenanceModeService);
        this.telegramBot = telegramBot;
        this.pdfExportService = pdfExportService;
        this.rateLimiterService = rateLimiterService;
        this.accountLinkService = accountLinkService;
        this.linkHandler = linkHandler;
        this.keyboardBuilder = keyboardBuilder;
        this.objectMapper = objectMapper;
        this.progressCleanupService = progressCleanupService;
        this.userLockService = userLockService;
        this.userSettingsService = userSettingsService;
        this.materialPdfGenerator = materialPdfGenerator;
        this.cloudStorageFacade = cloudStorageFacade;
        this.maintenanceModeService = maintenanceModeService;
        this.backupService = backupService;
        this.zipCourseImportService = zipCourseImportService;

        // Создаём зависимые хендлеры
        this.adminHandler = new AdminHandler(messageSender, new TelegramFileDownloader(telegramBot),
                sessionService, navigationService, courseImportService, zipCourseImportService,
                courseRepository, keyboardBuilder, sectionRepository, topicRepository,
                blockRepository, questionRepository, answerOptionRepository,
                blockImageRepository, questionImageRepository, adminUserRepository,
                userProgressRepository, userStudyTimeRepository, objectMapper, userSettingsService,
                backupService, maintenanceModeService, imageStorageService);
        this.courseNavHandler = new CourseNavigationHandler(
                messageSender, sessionService, navigationService, adminUserRepository,
                keyboardBuilder, userSettingsService, materialPdfGenerator, cloudStorageFacade,
                maintenanceModeService);
        this.testHandler = new TestHandler(messageSender, sessionService, navigationService,
                questionRepository, adminUserRepository, answerOptionRepository,
                userProgressRepository, userMistakeRepository, userTestResultRepository,
                courseNavHandler, userSettingsService, maintenanceModeService);
        this.settingsHandler = new SettingsHandler(messageSender, sessionService, navigationService,
                adminUserRepository, userSettingsService, progressCleanupService, maintenanceModeService);

        // dispatcher создаётся последним — после инициализации всех хендлеров
        this.dispatcher = new BotDispatcher(courseNavHandler, testHandler, adminHandler,
                settingsHandler, linkHandler, sessionService, adminUserRepository,
                userSettingsService, pdfExportService);
    }

    @Override
    protected void sendMainMenu(Long userId, Integer messageId) {
        if (isMaintenanceBlocked(userId)) {
            sendMaintenanceMessage(getEffectiveUserId(userId));
            return;
        }
        BotKeyboard keyboard = buildBaseMainMenuKeyboard(userId);
        if (isAdmin(userId)) {
            keyboard.addRow(
                    BotButton.callback("🎓 Adm Курсы", CALLBACK_ADMIN_COURSES_MENU),
                    BotButton.callback("💾 Adm БД", CALLBACK_ADMIN_DB)
            );
        }
        if (messageId != null) {
            editMessage(userId, messageId, MSG_MAIN_MENU, keyboard);
        } else {
            sendMessage(userId, MSG_MAIN_MENU, keyboard);
        }
    }

    public void handle(Update update) {
        if (update.message() != null) {
            handleMessage(update.message());
        } else if (update.callbackQuery() != null) {
            handleCallback(update.callbackQuery());
        }
    }

    private void updatePlatformUserId(Long internalUserId, Long telegramUserId) {
        UserContext ctx = sessionService.getCurrentContext(internalUserId);
        if (!telegramUserId.equals(ctx.getCurrentPlatformUserId())) {
            ctx.setCurrentPlatformUserId(telegramUserId);
            sessionService.updateSessionContext(internalUserId, ctx);
        }
    }

    private void handleMessage(Message message) {
        Long externalUserId = message.from().id();
        Long userId = accountLinkService.resolveInternalUserId(Platform.TELEGRAM, externalUserId);
        updatePlatformUserId(userId, externalUserId);
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
                BotDispatcher.PlatformContext ctx = buildPlatformContext(userId, externalUserId);
                if (parts.length == 2 && !parts[1].isBlank()) {
                    ctx.handleLinkCode(userId, parts[1]);
                } else {
                    ctx.handleLinkGenerate(userId);
                }
                return;
            }

            log.debug("handleMessage: userId={}, state={}, text={}", userId,
                    sessionService.getCurrentState(userId),
                    text.length() > 50 ? text.substring(0, 50) + "…" : text);

            dispatcher.dispatchMessage(userId, text, buildPlatformContext(userId, externalUserId));
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        Long externalUserId = callbackQuery.from().id();
        Long userId = accountLinkService.resolveInternalUserId(Platform.TELEGRAM, externalUserId);
        updatePlatformUserId(userId, externalUserId);
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

            dispatcher.dispatchCallback(userId, messageId, data, Platform.TELEGRAM,
                    buildPlatformContext(userId, externalUserId));
        }
    }

    private BotDispatcher.PlatformContext buildPlatformContext(Long userId, Long externalUserId) {
        return new BotDispatcher.PlatformContext() {
            @Override public void sendMainMenu(Long uid, Integer msgId) {
                TelegramBotHandler.this.sendMainMenu(uid, msgId);
                sessionService.updateSessionState(uid, BotState.MAIN_MENU);
            }
            @Override public void handleBack(Long uid, Integer msgId) {
                TelegramBotHandler.this.handleBack(uid, msgId);
            }
            @Override public void handleStatistics(Long uid, Integer msgId, boolean back) {
                if (back) { deleteMessage(uid, msgId); showStatistics(uid, null); }
                else { showStatistics(uid, msgId); }
            }
            @Override public void handleExportPdf(Long uid, Integer msgId) {
                TelegramBotHandler.this.handleExportPdf(uid, msgId);
            }
            @Override public void handleLinkGenerate(Long uid) {
                linkHandler.generateCode(uid, Platform.TELEGRAM,
                        new TelegramMessageSenderAdapter(telegramBot, sessionService, uid));
            }
            @Override public void handleLinkCode(Long uid, String code) {
                linkHandler.applyCode(uid, code, Platform.TELEGRAM, externalUserId,
                        new TelegramMessageSenderAdapter(telegramBot, sessionService, uid));
                sessionService.updateSessionState(uid, BotState.MAIN_MENU);
            }
            @Override public void handleLinkKeepTelegram(Long uid, String code) {
                linkHandler.resolveConflictKeepTelegram(uid, code, Platform.TELEGRAM, externalUserId,
                        new TelegramMessageSenderAdapter(telegramBot, sessionService, uid));
            }
            @Override public void handleLinkKeepVk(Long uid, String code) {
                linkHandler.resolveConflictKeepVk(uid, code, Platform.TELEGRAM, externalUserId,
                        new TelegramMessageSenderAdapter(telegramBot, sessionService, uid));
            }
            @Override public void handleLinkMerge(Long uid, String code) {
                linkHandler.resolveConflictMerge(uid, code, Platform.TELEGRAM, externalUserId,
                        new TelegramMessageSenderAdapter(telegramBot, sessionService, uid));
            }
            @Override public void handleLinkMergeSettingsTg(Long uid, String code) {
                linkHandler.finalizeMergeWithTelegramSettings(uid, code, Platform.TELEGRAM, externalUserId,
                        new TelegramMessageSenderAdapter(telegramBot, sessionService, uid));
            }
            @Override public void handleLinkMergeSettingsVk(Long uid, String code) {
                linkHandler.finalizeMergeWithVkSettings(uid, code, Platform.TELEGRAM, externalUserId,
                        new TelegramMessageSenderAdapter(telegramBot, sessionService, uid));
            }
            @Override public void notifyExpectingFile(Long uid) {
                TelegramBotHandler.this.sendMessage(uid,
                        "📁 Пожалуйста, отправьте файл резервной копии (дамп БД в формате .dump), а не текст.");
            }
        };
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