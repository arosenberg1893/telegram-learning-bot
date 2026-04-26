package com.lbt.telegram_learning_bot.vk;

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
import com.lbt.telegram_learning_bot.util.CallbackData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.lbt.telegram_learning_bot.util.Constants.*;

/**
 * Обработчик событий ВКонтакте.
 *
 * <p>Наследует {@link BaseHandler} для получения общих вспомогательных методов:
 * {@code isAdmin}, {@code sendMainMenu}, {@code sendMaintenanceMessage},
 * {@code formatStudyTime} и клавиатурных фабрик.
 * Платформо-специфичная логика (VK-отправка, VK-документы) остаётся здесь.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "vk.bot.enabled", havingValue = "true")
public class VkBotHandler extends BaseHandler {

    private final VkMessageSender sender;
    private final BotDispatcher dispatcher;
    private final RateLimiterService rateLimiterService;
    private final CourseNavigationHandler courseNavHandler;
    private final TestHandler testHandler;
    private final AdminHandler adminHandler;
    private final SettingsHandler settingsHandler;
    private final LinkHandler linkHandler;
    private final PdfExportService pdfExportService;
    private final VkHttpClient vkHttpClient;
    private final UserLockService userLockService;
    private final MaterialPdfGenerator materialPdfGenerator;
    private final CloudStorageFacade cloudStorageFacade;

    @Value("${vk.bot.page-size:3}")
    private int vkPageSize;

    @Value("${message.max-length:2000}")
    private int maxMessageLength;

    public VkBotHandler(VkMessageSender sender,
                        UserSessionService sessionService,
                        NavigationService navigationService,
                        RateLimiterService rateLimiterService,
                        LinkHandler linkHandler,
                        PdfExportService pdfExportService,
                        KeyboardBuilder keyboardBuilder,
                        AdminUserRepository adminUserRepository,
                        UserSettingsService userSettingsService,
                        QuestionRepository questionRepository,
                        AnswerOptionRepository answerOptionRepository,
                        UserProgressRepository userProgressRepository,
                        UserMistakeRepository userMistakeRepository,
                        UserTestResultRepository userTestResultRepository,
                        CourseRepository courseRepository,
                        SectionRepository sectionRepository,
                        TopicRepository topicRepository,
                        BlockRepository blockRepository,
                        BlockImageRepository blockImageRepository,
                        QuestionImageRepository questionImageRepository,
                        ObjectMapper objectMapper,
                        CourseImportService courseImportService,
                        ZipCourseImportService zipCourseImportService,
                        UserProgressCleanupService progressCleanupService,
                        UserStudyTimeRepository userStudyTimeRepository,
                        VkHttpClient vkHttpClient,
                        UserLockService userLockService,
                        MaterialPdfGenerator materialPdfGenerator,
                        CloudStorageFacade cloudStorageFacade,
                        MaintenanceModeService maintenanceModeService,
                        BackupService backupService,
                        ImageStorageService imageStorageService) {
        // BaseHandler получает VkMessageSender как MessageSender (адаптер)
        super(sender, sessionService, navigationService, adminUserRepository,
                userSettingsService, maintenanceModeService);

        this.sender = sender;
        this.rateLimiterService = rateLimiterService;
        this.linkHandler = linkHandler;
        this.pdfExportService = pdfExportService;
        this.vkHttpClient = vkHttpClient;
        this.userLockService = userLockService;
        this.materialPdfGenerator = materialPdfGenerator;
        this.cloudStorageFacade = cloudStorageFacade;

        this.adminHandler = new AdminHandler(sender, new VkFileDownloader(vkHttpClient),
                sessionService, navigationService, courseImportService, zipCourseImportService,
                courseRepository, keyboardBuilder, sectionRepository, topicRepository,
                blockRepository, questionRepository, answerOptionRepository,
                blockImageRepository, questionImageRepository, adminUserRepository,
                userProgressRepository, userStudyTimeRepository, objectMapper, userSettingsService,
                backupService, maintenanceModeService, imageStorageService);
        this.courseNavHandler = new CourseNavigationHandler(sender, sessionService, navigationService,
                adminUserRepository, keyboardBuilder, userSettingsService, materialPdfGenerator,
                cloudStorageFacade, maintenanceModeService);
        this.testHandler = new TestHandler(sender, sessionService, navigationService,
                questionRepository, adminUserRepository, answerOptionRepository,
                userProgressRepository, userMistakeRepository, userTestResultRepository,
                courseNavHandler, userSettingsService, maintenanceModeService);
        this.settingsHandler = new SettingsHandler(sender, sessionService, navigationService,
                adminUserRepository, userSettingsService, progressCleanupService, maintenanceModeService);

        // dispatcher создаётся последним — после инициализации всех хендлеров
        this.dispatcher = new BotDispatcher(courseNavHandler, testHandler, adminHandler,
                settingsHandler, linkHandler, sessionService, adminUserRepository,
                userSettingsService, pdfExportService);
    }

    // ================== Переопределение главного меню (добавляем кнопки администратора) ==================

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

    // ================== Публичные методы ==================

    public void handleMessage(long internalUserId, long vkUserId, String text, Integer messageId) {
        synchronized (userLockService.getLock(internalUserId)) {
            updatePlatformUserId(internalUserId, vkUserId);
            if (!rateLimiterService.isAllowed(internalUserId)) {
                sender.sendText(vkUserId, TOO_MANY_REQUEST);
                return;
            }
            if (text == null) return;
            if (text.length() > maxMessageLength) {
                sender.sendText(vkUserId, "⚠️ Сообщение слишком длинное. Пожалуйста, сократите до "
                        + maxMessageLength + " символов.");
                return;
            }
            text = text.trim();

            if (text.startsWith("/start") || text.equals("начать")) {
                UserContext context = sessionService.getCurrentContext(internalUserId);
                if (context.getUserName() == null) context.setUserName("Пользователь");
                sessionService.updateSessionContext(internalUserId, context);
                sendMainMenu(internalUserId, null);
                sessionService.updateSessionState(internalUserId, BotState.MAIN_MENU);
                return;
            }

            if (text.startsWith("/link")) {
                String[] parts = text.split("\\s+", 2);
                BotDispatcher.PlatformContext ctx = buildPlatformContext(internalUserId, vkUserId);
                if (parts.length == 2 && !parts[1].isBlank()) {
                    ctx.handleLinkCode(internalUserId, parts[1]);
                } else {
                    ctx.handleLinkGenerate(internalUserId);
                }
                return;
            }

            dispatcher.dispatchMessage(internalUserId, text, buildPlatformContext(internalUserId, vkUserId));
        }
    }

    public void handleDocument(long internalUserId, long vkUserId, Map<String, Object> fileRef,
                               Integer messageId) {
        synchronized (userLockService.getLock(internalUserId)) {
            updatePlatformUserId(internalUserId, vkUserId);
            if (!rateLimiterService.isAllowed(internalUserId)) {
                sender.sendText(vkUserId, TOO_MANY_REQUEST);
                return;
            }
            adminHandler.handleDocument(internalUserId, fileRef);
            if (messageId != null) {
                sender.deleteMessage(vkUserId, messageId);
            }
        }
    }

    public void handleCallback(long internalUserId, long vkUserId, String payload,
                               Integer messageId, String eventId) {
        log.info("VK callback received: internalUserId={}, payload={}", internalUserId, payload);
        synchronized (userLockService.getLock(internalUserId)) {
            updatePlatformUserId(internalUserId, vkUserId);
            if (!rateLimiterService.isAllowed(internalUserId)) {
                sender.sendText(vkUserId, TOO_MANY_REQUEST);
                return;
            }
            dispatcher.dispatchCallback(internalUserId, messageId, payload, Platform.VK,
                    buildPlatformContext(internalUserId, vkUserId));
        }
    }


    private BotDispatcher.PlatformContext buildPlatformContext(long internalUserId, long vkUserId) {
        return new BotDispatcher.PlatformContext() {
            @Override public void sendMainMenu(Long uid, Integer msgId) {
                VkBotHandler.this.sendMainMenu(uid, msgId);
                sessionService.updateSessionState(uid, BotState.MAIN_MENU);
            }
            @Override public void handleBack(Long uid, Integer msgId) {
                VkBotHandler.this.handleBack(uid, msgId);
            }
            @Override public void handleStatistics(Long uid, Integer msgId, boolean back) {
                if (back) { deleteMessage(uid, msgId); showStatistics(uid, null); }
                else { showStatistics(uid, msgId); }
            }
            @Override public void handleExportPdf(Long uid, Integer msgId) {
                VkBotHandler.this.handleExportPdf(uid, vkUserId, msgId);
            }
            @Override public void handleLinkGenerate(Long uid) {
                linkHandler.generateCode(uid, Platform.VK, sender);
            }
            @Override public void handleLinkCode(Long uid, String code) {
                linkHandler.applyCode(uid, code, Platform.VK, vkUserId, sender);
                sessionService.updateSessionState(uid, BotState.MAIN_MENU);
            }
            @Override public void handleLinkKeepTelegram(Long uid, String code) {
                linkHandler.resolveConflictKeepTelegram(uid, code, Platform.VK, vkUserId, sender);
            }
            @Override public void handleLinkKeepVk(Long uid, String code) {
                linkHandler.resolveConflictKeepVk(uid, code, Platform.VK, vkUserId, sender);
            }
            @Override public void handleLinkMerge(Long uid, String code) {
                linkHandler.resolveConflictMerge(uid, code, Platform.VK, vkUserId, sender);
            }
            @Override public void handleLinkMergeSettingsTg(Long uid, String code) {
                linkHandler.finalizeMergeWithTelegramSettings(uid, code, Platform.VK, vkUserId, sender);
            }
            @Override public void handleLinkMergeSettingsVk(Long uid, String code) {
                linkHandler.finalizeMergeWithVkSettings(uid, code, Platform.VK, vkUserId, sender);
            }
            @Override public void notifyExpectingFile(Long uid) {
                sender.sendText(vkUserId, "📁 Пожалуйста, отправьте файл резервной копии (дамп БД в формате .dump), а не текст.");
            }
        };
    }

    // ================== Приватные методы ==================

    private void showStatistics(long internalUserId, Integer messageId) {
        long totalCourses = navigationService.getTotalStartedCourses(internalUserId);
        long completedCourses = navigationService.getCompletedCoursesCount(internalUserId);
        String hardestCourse = navigationService.getHardestCourse(internalUserId);
        long totalSeconds = navigationService.getTotalStudySecondsForUser(internalUserId);

        StringBuilder stats = new StringBuilder();
        stats.append(STATS_TITLE).append("\n\n");
        stats.append(String.format(STATS_TOTAL_COURSES, totalCourses));
        stats.append(String.format(STATS_COMPLETED, completedCourses));
        stats.append(String.format(STATS_HARDEST, hardestCourse));
        stats.append(String.format(STATS_TOTAL_TIME, formatStudyTime(totalSeconds)));
        stats.append(STATS_PROGRESS);

        java.util.List<String> courseProgress = navigationService.getCoursesProgress(internalUserId);
        if (courseProgress.isEmpty()) {
            stats.append(STATS_NO_DATA);
        } else {
            courseProgress.forEach(line -> stats.append(line).append("\n"));
        }

        BotKeyboard keyboard = createStatisticsKeyboard();
        if (messageId != null) {
            editMessage(internalUserId, messageId, stats.toString(), keyboard);
        } else {
            sendMessage(internalUserId, stats.toString(), keyboard);
        }
    }

    private void handleExportPdf(long internalUserId, long vkUserId, Integer messageId) {
        Integer progressId = sender.sendProgress(vkUserId);
        try {
            UserContext context = sessionService.getCurrentContext(internalUserId);
            String userName = context.getUserName() != null ? context.getUserName() : DEFAULT_USER_NAME;
            String downloadUrl = pdfExportService.saveStatisticsPdf(internalUserId, userName);
            String message = "📊 Ваша статистика готова. Скачайте PDF по ссылке:\n" + downloadUrl
                    + "\n\nСсылка действительна 15 минут.";
            sendMessage(internalUserId, message, BotKeyboard.of(BotButton.callback(BUTTON_MAIN_MENU, CALLBACK_MAIN_MENU)));
            if (messageId != null) sender.deleteMessage(vkUserId, messageId);
        } catch (Exception e) {
            log.error("[VK] Error generating PDF for user {}", internalUserId, e);
            sendMessage(internalUserId, MSG_PDF_ERROR, BotKeyboard.backToMain());
        } finally {
            if (progressId != null) sender.deleteMessage(vkUserId, progressId);
        }
    }

    private void handleBack(long internalUserId, Integer messageId) {
        BotState state = sessionService.getCurrentState(internalUserId);
        switch (state) {
            case MY_COURSES, ALL_COURSES, SEARCH_COURSES -> {
                sendMainMenu(internalUserId, messageId);
                sessionService.updateSessionState(internalUserId, BotState.MAIN_MENU);
            }
            case COURSE_SECTIONS, SECTION_TOPICS, TOPIC_LEARNING ->
                courseNavHandler.handleBackToCourses(internalUserId, messageId, vkPageSize);
            default -> {
                sendMainMenu(internalUserId, messageId);
                sessionService.updateSessionState(internalUserId, BotState.MAIN_MENU);
            }
        }
    }

    private void updatePlatformUserId(long internalUserId, long vkUserId) {
        UserContext ctx = sessionService.getCurrentContext(internalUserId);
        ctx.setCurrentPlatformUserId(vkUserId);
        sessionService.updateSessionContext(internalUserId, ctx);
    }
}
