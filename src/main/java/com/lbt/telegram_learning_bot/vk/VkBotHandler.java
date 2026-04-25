package com.lbt.telegram_learning_bot.vk;

import com.fasterxml.jackson.databind.ObjectMapper;
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
            BotState currentState = sessionService.getCurrentState(internalUserId);

            if (text.startsWith("/start") || text.equals("начать")) {
                UserContext context = sessionService.getCurrentContext(internalUserId);
                if (context.getUserName() == null) {
                    context.setUserName("Пользователь");
                    sessionService.updateSessionContext(internalUserId, context);
                }
                sendMainMenu(internalUserId, null);
                sessionService.updateSessionState(internalUserId, BotState.MAIN_MENU);
                return;
            }

            if (text.startsWith("/link")) {
                String[] parts = text.split("\\s+", 2);
                if (parts.length == 2 && !parts[1].isBlank()) {
                    linkHandler.applyCode(internalUserId, parts[1], Platform.VK, vkUserId, sender);
                } else {
                    linkHandler.generateCode(internalUserId, Platform.VK, sender);
                }
                return;
            }

            switch (currentState) {
                case AWAITING_SEARCH_QUERY:
                    courseNavHandler.handleSearchQuery(internalUserId, text, vkPageSize);
                    break;
                case AWAITING_LINK_CODE:
                    linkHandler.applyCode(internalUserId, text, Platform.VK, vkUserId, sender);
                    sessionService.updateSessionState(internalUserId, BotState.MAIN_MENU);
                    break;
                case AWAITING_BACKUP_FILE:
                    sender.sendText(vkUserId, "📁 Пожалуйста, отправьте файл резервной копии"
                            + " (дамп БД в формате .dump), а не текст.");
                    break;
                default:
                    sendMainMenu(internalUserId, null);
            }
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
        log.info("VK callback received: internalUserId={}, payload={}, messageId={}",
                internalUserId, payload, messageId);
        synchronized (userLockService.getLock(internalUserId)) {
            updatePlatformUserId(internalUserId, vkUserId);
            if (!rateLimiterService.isAllowed(internalUserId)) {
                sender.sendText(vkUserId, TOO_MANY_REQUEST);
                return;
            }
            CallbackData cb = CallbackData.parse(payload);
            String action = cb.action();

            switch (action) {
                // навигация
                case CALLBACK_MY_COURSES:
                    courseNavHandler.handleMyCourses(internalUserId, messageId, 0, vkPageSize);
                    break;
                case CALLBACK_ALL_COURSES:
                    courseNavHandler.handleAllCourses(internalUserId, messageId, 0, vkPageSize);
                    break;
                case CALLBACK_SEARCH_COURSES:
                    courseNavHandler.promptSearch(internalUserId, messageId);
                    break;
                case CALLBACK_COURSES_PAGE:
                    cb.getString(1).ifPresent(src ->
                        cb.getInt(2).ifPresent(page ->
                            courseNavHandler.handleCoursesPage(internalUserId, messageId, src, page, vkPageSize)));
                    break;
                case CALLBACK_SELECT_COURSE:
                    cb.getLong(1).ifPresent(id ->
                        courseNavHandler.handleSelectCourse(internalUserId, messageId, id, vkPageSize));
                    break;
                case CALLBACK_SELECT_SECTION:
                    cb.getLong(1).ifPresent(id ->
                        courseNavHandler.handleSelectSection(internalUserId, messageId, id, vkPageSize));
                    break;
                case CALLBACK_SELECT_TOPIC:
                    cb.getLong(1).ifPresent(id ->
                        courseNavHandler.handleSelectTopic(internalUserId, messageId, id));
                    break;
                case CALLBACK_SECTIONS_PAGE:
                    cb.getLong(1).ifPresent(courseId ->
                        cb.getInt(2).ifPresent(page ->
                            courseNavHandler.handleSectionsPage(internalUserId, messageId, courseId, page, vkPageSize)));
                    break;
                case CALLBACK_TOPICS_PAGE:
                    cb.getLong(1).ifPresent(sectionId ->
                        cb.getInt(2).ifPresent(page ->
                            courseNavHandler.handleTopicsPage(internalUserId, messageId, sectionId, page, vkPageSize)));
                    break;
                case CALLBACK_BACK_TO_COURSES:
                    BotState state = sessionService.getCurrentState(internalUserId);
                    if (state.isAdminEditState()) {
                        adminHandler.handleBackToCoursesFromEdit(internalUserId, messageId, vkPageSize);
                    } else {
                        courseNavHandler.handleBackToCourses(internalUserId, messageId, vkPageSize);
                    }
                    break;
                case CALLBACK_BACK_TO_SECTIONS:
                    courseNavHandler.handleBackToSections(internalUserId, messageId, vkPageSize);
                    break;
                case CALLBACK_BACK_TO_TOPICS:
                    courseNavHandler.handleBackToTopics(internalUserId, messageId, vkPageSize);
                    break;
                case CALLBACK_NEXT_BLOCK:
                    courseNavHandler.handleNextBlock(internalUserId, messageId);
                    break;
                case CALLBACK_PREV_BLOCK:
                    courseNavHandler.handlePrevBlock(internalUserId, messageId);
                    break;
                case CALLBACK_NEXT_QUESTION:
                    testHandler.handleNextQuestion(internalUserId, messageId);
                    break;
                case CALLBACK_PREV_QUESTION:
                    testHandler.handlePrevQuestion(internalUserId, messageId);
                    break;
                case CALLBACK_ANSWER:
                    cb.getLong(1).ifPresent(questionId ->
                        cb.getLong(2).ifPresent(answerId ->
                            testHandler.handleAnswer(internalUserId, messageId, questionId, answerId)));
                    break;
                case CALLBACK_BACK_TO_BLOCK_TEXT:
                    testHandler.handleBackToBlockText(internalUserId, messageId);
                    break;

                // тесты
                case CALLBACK_TEST_TOPIC:
                    cb.getLong(1).ifPresent(id -> testHandler.handleTestTopic(internalUserId, messageId, id));
                    break;
                case CALLBACK_TEST_SECTION:
                    cb.getLong(1).ifPresent(id -> testHandler.handleTestSection(internalUserId, messageId, id));
                    break;
                case CALLBACK_TEST_COURSE:
                    cb.getLong(1).ifPresent(id -> testHandler.handleTestCourse(internalUserId, messageId, id));
                    break;

                // экспорт учебных материалов
                case CALLBACK_EXPORT_TOPIC:
                    courseNavHandler.handleExportTopic(internalUserId, messageId, payload);
                    break;
                case CALLBACK_EXPORT_SECTION:
                    courseNavHandler.handleExportSection(internalUserId, messageId, payload);
                    break;
                case CALLBACK_EXPORT_COURSE:
                    courseNavHandler.handleExportCourse(internalUserId, messageId, payload);
                    break;

                // администрирование курсов
                case CALLBACK_CREATE_COURSE:
                case CALLBACK_EDIT_COURSE:
                case CALLBACK_DELETE_COURSE:
                case CALLBACK_ADMIN_COURSES_MENU:
                case CALLBACK_SELECT_COURSE_FOR_EDIT:
                case CALLBACK_SELECT_COURSE_FOR_DELETE:
                case CALLBACK_EDIT_COURSE_ACTION:
                case CALLBACK_SELECT_SECTION_FOR_EDIT:
                case CALLBACK_EDIT_SECTION_ACTION:
                case CALLBACK_SELECT_TOPIC_FOR_EDIT:
                case CALLBACK_CONFIRM_DELETE_COURSE:
                case CALLBACK_RETRY:
                case CALLBACK_ADMIN_COURSES_PAGE:
                case CALLBACK_ADMIN_SECTIONS_PAGE:
                case CALLBACK_ADMIN_TOPICS_PAGE:
                case CALLBACK_ADMIN_BACK_TO_SECTIONS:
                case CALLBACK_ADMIN_BACK_TO_TOPICS:
                    if (!isAdmin(internalUserId)) return;
                    adminHandler.handleAdminCallback(internalUserId, messageId, payload, vkPageSize);
                    break;

                // управление базой данных
                case CALLBACK_ADMIN_DB:
                    if (!isAdmin(internalUserId)) return;
                    adminHandler.showDatabaseMenu(internalUserId, messageId);
                    break;
                case CALLBACK_BACKUP_NOW:
                    if (!isAdmin(internalUserId)) return;
                    adminHandler.performBackupNow(internalUserId, messageId);
                    break;
                case CALLBACK_RESTORE:
                    if (!isAdmin(internalUserId)) return;
                    adminHandler.showRestoreMenu(internalUserId, messageId);
                    break;
                case CALLBACK_RESTORE_SELECT:
                    if (!isAdmin(internalUserId)) return;
                    cb.getString(1).ifPresent(fileName ->
                        adminHandler.restoreFromBackup(internalUserId, messageId, fileName));
                    break;
                case CALLBACK_UPLOAD_BACKUP_FILE:
                    if (!isAdmin(internalUserId)) return;
                    adminHandler.promptBackupFileUpload(internalUserId, messageId);
                    break;
                case CALLBACK_TOGGLE_MAINTENANCE:
                    if (!isAdmin(internalUserId)) return;
                    adminHandler.toggleMaintenanceMode(internalUserId, messageId);
                    break;

                // статистика
                case CALLBACK_STATISTICS:
                    if (cb.getString(1).filter(CALLBACK_BACK::equals).isPresent()) {
                        deleteMessage(internalUserId, messageId);
                        showStatistics(internalUserId, null);
                    } else {
                        showStatistics(internalUserId, messageId);
                    }
                    break;
                case CALLBACK_EXPORT_PDF:
                    handleExportPdf(internalUserId, vkUserId, messageId);
                    break;
                case CALLBACK_MY_MISTAKES:
                    testHandler.handleMyMistakes(internalUserId, messageId);
                    break;

                // настройки
                case CALLBACK_SETTINGS:
                    settingsHandler.showSettingsMenu(internalUserId, messageId);
                    break;
                case CALLBACK_SETTINGS_SHUFFLE:
                    settingsHandler.toggleShuffle(internalUserId, messageId);
                    break;
                case CALLBACK_SETTINGS_PAGESIZE:
                    settingsHandler.showPageSizeOptions(internalUserId, messageId);
                    break;
                case CALLBACK_SETTINGS_PAGESIZE_OTHER:
                    settingsHandler.promptPageSizeInput(internalUserId, messageId);
                    break;
                case CALLBACK_SETTINGS_QUESTIONS:
                    settingsHandler.showQuestionsPerBlockOptions(internalUserId, messageId);
                    break;
                case CALLBACK_SETTINGS_EXPLANATIONS:
                    settingsHandler.toggleExplanations(internalUserId, messageId);
                    break;
                case CALLBACK_SETTINGS_RESET:
                    settingsHandler.confirmResetProgress(internalUserId, messageId);
                    break;
                case CALLBACK_SETTINGS_PAGESIZE_SET:
                    cb.getInt(1).ifPresent(size -> settingsHandler.setPageSize(internalUserId, messageId, size));
                    break;
                case CALLBACK_SETTINGS_QUESTIONS_SET:
                    cb.getInt(1).ifPresent(count ->
                        settingsHandler.setQuestionsPerBlock(internalUserId, messageId, count));
                    break;
                case CALLBACK_SETTINGS_RESET_CONFIRM:
                    settingsHandler.resetProgress(internalUserId, messageId);
                    break;
                case CALLBACK_SETTINGS_PDF_QUESTIONS:
                    settingsHandler.togglePdfQuestions(internalUserId, messageId);
                    break;

                // привязка аккаунтов
                case CALLBACK_LINK_GENERATE:
                    linkHandler.generateCode(internalUserId, Platform.VK, sender);
                    break;
                case CALLBACK_LINK_KEEP_TELEGRAM:
                    cb.getString(1).ifPresent(code ->
                        linkHandler.resolveConflictKeepTelegram(internalUserId, code, Platform.VK, vkUserId, sender));
                    break;
                case CALLBACK_LINK_KEEP_VK:
                    cb.getString(1).ifPresent(code ->
                        linkHandler.resolveConflictKeepVk(internalUserId, code, Platform.VK, vkUserId, sender));
                    break;
                case CALLBACK_LINK_MERGE:
                    cb.getString(1).ifPresent(code ->
                        linkHandler.resolveConflictMerge(internalUserId, code, Platform.VK, vkUserId, sender));
                    break;
                case CALLBACK_LINK_MERGE_SETTINGS_TG:
                    cb.getString(1).ifPresent(code ->
                        linkHandler.finalizeMergeWithTelegramSettings(internalUserId, code, Platform.VK, vkUserId, sender));
                    break;
                case CALLBACK_LINK_MERGE_SETTINGS_VK:
                    cb.getString(1).ifPresent(code ->
                        linkHandler.finalizeMergeWithVkSettings(internalUserId, code, Platform.VK, vkUserId, sender));
                    break;

                // старые callback-и (для обратной совместимости)
                case CALLBACK_LINK_RESOLVE_KEEP_THIS:
                    cb.getString(1).ifPresent(code ->
                        linkHandler.resolveConflictKeepTelegram(internalUserId, code, Platform.VK, vkUserId, sender));
                    break;
                case CALLBACK_LINK_RESOLVE_KEEP_OTHER:
                    cb.getString(1).ifPresent(code ->
                        linkHandler.resolveConflictKeepVk(internalUserId, code, Platform.VK, vkUserId, sender));
                    break;

                case CALLBACK_MAIN_MENU:
                    sendMainMenu(internalUserId, messageId);
                    sessionService.updateSessionState(internalUserId, BotState.MAIN_MENU);
                    break;
                case CALLBACK_BACK:
                    handleBack(internalUserId, messageId);
                    break;

                default:
                    log.warn("[VK] Unknown callback action: {}", action);
            }
        }
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
