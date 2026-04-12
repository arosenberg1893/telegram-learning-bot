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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
@Component
@ConditionalOnProperty(name = "vk.bot.enabled", havingValue = "true")
public class VkBotHandler {

    private final VkMessageSender sender;
    private final UserSessionService sessionService;
    private final NavigationService navigationService;
    private final RateLimiterService rateLimiterService;
    private final CourseNavigationHandler courseNavHandler;
    private final TestHandler testHandler;
    private final AdminHandler adminHandler;
    private final SettingsHandler settingsHandler;
    private final LinkHandler linkHandler;
    private final PdfExportService pdfExportService;
    private final VkHttpClient vkHttpClient;
    private final UserLockService userLockService;
    private final UserSettingsService userSettingsService;
    private final MaterialPdfGenerator materialPdfGenerator;
    private final CloudStorageFacade cloudStorageFacade;
    private final MaintenanceModeService maintenanceModeService;
    private final BackupService backupService;

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
                        BackupService backupService) {
        this.sender = sender;
        this.sessionService = sessionService;
        this.navigationService = navigationService;
        this.rateLimiterService = rateLimiterService;
        this.linkHandler = linkHandler;
        this.pdfExportService = pdfExportService;
        this.vkHttpClient = vkHttpClient;
        this.userLockService = userLockService;
        this.userSettingsService = userSettingsService;
        this.materialPdfGenerator = materialPdfGenerator;
        this.cloudStorageFacade = cloudStorageFacade;
        this.maintenanceModeService = maintenanceModeService;
        this.backupService = backupService;


        this.adminHandler = new AdminHandler(sender, new VkFileDownloader(vkHttpClient),
                sessionService, navigationService, courseImportService, zipCourseImportService,
                courseRepository, keyboardBuilder, sectionRepository, topicRepository,
                blockRepository, questionRepository, answerOptionRepository,
                blockImageRepository, questionImageRepository, adminUserRepository,
                userProgressRepository, userStudyTimeRepository, objectMapper, userSettingsService,
                backupService, maintenanceModeService);
        this.courseNavHandler = new CourseNavigationHandler(sender, sessionService, navigationService,
                adminUserRepository, keyboardBuilder, userSettingsService, materialPdfGenerator, cloudStorageFacade,
                maintenanceModeService);
        this.testHandler = new TestHandler(sender, sessionService, navigationService,
                questionRepository, adminUserRepository, answerOptionRepository,
                userProgressRepository, userMistakeRepository, userTestResultRepository,
                courseNavHandler, userSettingsService, maintenanceModeService);
        this.settingsHandler = new SettingsHandler(sender, sessionService, navigationService,
                adminUserRepository, userSettingsService, progressCleanupService, maintenanceModeService);
    }

    private boolean isMaintenanceBlocked(long internalUserId) {
        return maintenanceModeService.isMaintenance() && !isAdmin(internalUserId);
    }

    private void sendMaintenanceMessage(long vkUserId) {
        String text = "🔧 Бот временно недоступен. Ведутся технические работы. Пожалуйста, зайдите позже.";
        sender.sendText(vkUserId, text);
    }

    private void sendMainMenu(long internalUserId, long vkUserId, Integer messageId) {
        if (isMaintenanceBlocked(internalUserId)) {
            sendMaintenanceMessage(vkUserId);
            return;
        }

        BotKeyboard keyboard = new BotKeyboard()
                .addRow(BotButton.callback(BUTTON_MY_COURSES, CALLBACK_MY_COURSES),
                        BotButton.callback(BUTTON_ALL_COURSES, CALLBACK_ALL_COURSES))
                .addRow(BotButton.callback(BUTTON_SEARCH, CALLBACK_SEARCH_COURSES),
                        BotButton.callback(BUTTON_STATISTICS, CALLBACK_STATISTICS))
                .addRow(BotButton.callback(BUTTON_MISTAKES, CALLBACK_MY_MISTAKES))
                .addRow(BotButton.callback("⚙️ Настройки", CALLBACK_SETTINGS));

        if (isAdmin(internalUserId)) {
            keyboard.addRow(
                    BotButton.callback("🎓 Adm Курсы", CALLBACK_ADMIN_COURSES_MENU),
                    BotButton.callback("💾 Adm БД", CALLBACK_ADMIN_DB)
            );
        }

        if (messageId != null) {
            sender.editMenu(vkUserId, messageId, MSG_MAIN_MENU, keyboard);
        } else {
            sender.sendMenu(vkUserId, MSG_MAIN_MENU, keyboard);
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
                sender.sendText(vkUserId, "⚠️ Сообщение слишком длинное. Пожалуйста, сократите до " + maxMessageLength + " символов.");
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
                sendMainMenu(internalUserId, vkUserId, null);
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
                    sender.sendText(vkUserId, "📁 Пожалуйста, отправьте файл резервной копии (дамп БД в формате .dump), а не текст.");
                    break;
                default:
                    sendMainMenu(internalUserId, vkUserId, null);
            }
        }
    }

    public void handleDocument(long internalUserId, long vkUserId, Map<String, Object> fileRef, Integer messageId) {
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

    public void handleCallback(long internalUserId, long vkUserId, String payload, Integer messageId, String eventId) {
        log.info("VK callback received: internalUserId={}, payload={}, messageId={}", internalUserId, payload, messageId);
        synchronized (userLockService.getLock(internalUserId)) {
            updatePlatformUserId(internalUserId, vkUserId);
            if (!rateLimiterService.isAllowed(internalUserId)) {
                sender.sendText(vkUserId, TOO_MANY_REQUEST);
                return;
            }
            log.debug("[VK] Callback internalUser={} payload={}", internalUserId, payload);
            String[] parts = payload.split(":", 3);
            String action = parts[0];

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
                    courseNavHandler.handleCoursesPage(internalUserId, messageId, parts[1], Integer.parseInt(parts[2]), vkPageSize);
                    break;
                case CALLBACK_SELECT_COURSE:
                    courseNavHandler.handleSelectCourse(internalUserId, messageId, Long.parseLong(parts[1]), vkPageSize);
                    break;
                case CALLBACK_SELECT_SECTION:
                    courseNavHandler.handleSelectSection(internalUserId, messageId, Long.parseLong(parts[1]), vkPageSize);
                    break;
                case CALLBACK_SELECT_TOPIC:
                    courseNavHandler.handleSelectTopic(internalUserId, messageId, Long.parseLong(parts[1]));
                    break;
                case CALLBACK_SECTIONS_PAGE:
                    courseNavHandler.handleSectionsPage(internalUserId, messageId, Long.parseLong(parts[1]), Integer.parseInt(parts[2]), vkPageSize);
                    break;
                case CALLBACK_TOPICS_PAGE:
                    courseNavHandler.handleTopicsPage(internalUserId, messageId, Long.parseLong(parts[1]), Integer.parseInt(parts[2]), vkPageSize);
                    break;
                case CALLBACK_BACK_TO_COURSES:
                    BotState state = sessionService.getCurrentState(internalUserId);
                    if (isAdminState(state)) {
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
                    testHandler.handleAnswer(internalUserId, messageId, Long.parseLong(parts[1]), Long.parseLong(parts[2]));
                    break;
                case CALLBACK_BACK_TO_BLOCK_TEXT:
                    testHandler.handleBackToBlockText(internalUserId, messageId);
                    break;

                // тесты
                case CALLBACK_TEST_TOPIC:
                    testHandler.handleTestTopic(internalUserId, messageId, Long.parseLong(parts[1]));
                    break;
                case CALLBACK_TEST_SECTION:
                    testHandler.handleTestSection(internalUserId, messageId, Long.parseLong(parts[1]));
                    break;
                case CALLBACK_TEST_COURSE:
                    testHandler.handleTestCourse(internalUserId, messageId, Long.parseLong(parts[1]));
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
                    adminHandler.restoreFromBackup(internalUserId, messageId, parts[1]);
                    break;
                case CALLBACK_UPLOAD_BACKUP_FILE:
                    if (!isAdmin(internalUserId)) return;
                    adminHandler.promptBackupFileUpload(internalUserId, messageId);
                    break;
                case CALLBACK_TOGGLE_MAINTENANCE:
                    if (!isAdmin(internalUserId)) return;
                    adminHandler.toggleMaintenanceMode(internalUserId, messageId);
                    break;

                // статистика и ошибки
                case CALLBACK_STATISTICS:
                    if (parts.length > 1 && CALLBACK_BACK.equals(parts[1])) {
                        sender.deleteMessage(vkUserId, messageId);
                        showStatistics(internalUserId, vkUserId, null);
                    } else {
                        showStatistics(internalUserId, vkUserId, messageId);
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
                    if (parts.length >= 2) {
                        int size = Integer.parseInt(parts[1]);
                        settingsHandler.setPageSize(internalUserId, messageId, size);
                    }
                    break;
                case CALLBACK_SETTINGS_QUESTIONS_SET:
                    if (parts.length >= 2) {
                        int count = Integer.parseInt(parts[1]);
                        settingsHandler.setQuestionsPerBlock(internalUserId, messageId, count);
                    }
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
                    if (parts.length >= 2) {
                        linkHandler.resolveConflictKeepTelegram(internalUserId, parts[1], Platform.VK, vkUserId, sender);
                    }
                    break;
                case CALLBACK_LINK_KEEP_VK:
                    if (parts.length >= 2) {
                        linkHandler.resolveConflictKeepVk(internalUserId, parts[1], Platform.VK, vkUserId, sender);
                    }
                    break;
                case CALLBACK_LINK_MERGE:
                    if (parts.length >= 2) {
                        linkHandler.resolveConflictMerge(internalUserId, parts[1], Platform.VK, vkUserId, sender);
                    }
                    break;
                case CALLBACK_LINK_MERGE_SETTINGS_TG:
                    if (parts.length >= 2) {
                        linkHandler.finalizeMergeWithTelegramSettings(internalUserId, parts[1], Platform.VK, vkUserId, sender);
                    }
                    break;
                case CALLBACK_LINK_MERGE_SETTINGS_VK:
                    if (parts.length >= 2) {
                        linkHandler.finalizeMergeWithVkSettings(internalUserId, parts[1], Platform.VK, vkUserId, sender);
                    }
                    break;

                // старые callback-и (для обратной совместимости)
                case CALLBACK_LINK_RESOLVE_KEEP_THIS:
                    if (parts.length >= 2) {
                        linkHandler.resolveConflictKeepTelegram(internalUserId, parts[1], Platform.VK, vkUserId, sender);
                    }
                    break;
                case CALLBACK_LINK_RESOLVE_KEEP_OTHER:
                    if (parts.length >= 2) {
                        linkHandler.resolveConflictKeepVk(internalUserId, parts[1], Platform.VK, vkUserId, sender);
                    }
                    break;

                case CALLBACK_MAIN_MENU:
                    sendMainMenu(internalUserId, vkUserId, messageId);
                    sessionService.updateSessionState(internalUserId, BotState.MAIN_MENU);
                    break;
                case CALLBACK_BACK:
                    handleBack(internalUserId, vkUserId, messageId);
                    break;
                default:
                    log.warn("[VK] Unknown callback action: {}", action);
            }
        }
    }

    // ================== Приватные методы ==================

    private void showStatistics(long internalUserId, long vkUserId, Integer messageId) {
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
        List<String> courseProgress = navigationService.getCoursesProgress(internalUserId);
        if (courseProgress.isEmpty()) {
            stats.append(STATS_NO_DATA);
        } else {
            courseProgress.forEach(line -> stats.append(line).append("\n"));
        }
        BotKeyboard keyboard = new BotKeyboard()
                .addRow(BotButton.callback(BUTTON_EXPORT_PDF, CALLBACK_EXPORT_PDF),
                        BotButton.callback(BUTTON_MAIN_MENU, CALLBACK_MAIN_MENU));
        if (messageId != null) {
            sender.editMenu(vkUserId, messageId, stats.toString(), keyboard);
        } else {
            sender.sendMenu(vkUserId, stats.toString(), keyboard);
        }
    }

    private void handleExportPdf(long internalUserId, long vkUserId, Integer messageId) {
        Integer progressId = sender.sendProgress(vkUserId);
        try {
            UserContext context = sessionService.getCurrentContext(internalUserId);
            String userName = context.getUserName() != null ? context.getUserName() : DEFAULT_USER_NAME;
            String downloadUrl = pdfExportService.saveStatisticsPdf(internalUserId, userName);
            String message = "📊 Ваша статистика готова. Скачайте PDF по ссылке:\n" + downloadUrl +
                    "\n\nСсылка действительна 15 минут.";
            BotKeyboard keyboard = BotKeyboard.of(BotButton.callback(BUTTON_MAIN_MENU, CALLBACK_MAIN_MENU));
            sender.sendMenu(vkUserId, message, keyboard);
            if (messageId != null) {
                sender.deleteMessage(vkUserId, messageId);
            }
        } catch (Exception e) {
            log.error("[VK] Error generating PDF for user {}", internalUserId, e);
            sender.sendMenu(vkUserId, MSG_PDF_ERROR, BotKeyboard.backToMain());
        } finally {
            if (progressId != null) sender.deleteMessage(vkUserId, progressId);
        }
    }

    private void handleBack(long internalUserId, long vkUserId, Integer messageId) {
        BotState state = sessionService.getCurrentState(internalUserId);
        switch (state) {
            case MY_COURSES, ALL_COURSES, SEARCH_COURSES:
                sendMainMenu(internalUserId, vkUserId, messageId);
                sessionService.updateSessionState(internalUserId, BotState.MAIN_MENU);
                break;
            case COURSE_SECTIONS, SECTION_TOPICS, TOPIC_LEARNING:
                courseNavHandler.handleBackToCourses(internalUserId, messageId, vkPageSize);
                break;
            default:
                sendMainMenu(internalUserId, vkUserId, messageId);
                sessionService.updateSessionState(internalUserId, BotState.MAIN_MENU);
        }
    }

    private boolean isAdminState(BotState state) {
        return switch (state) {
            case EDIT_COURSE_SECTION_CHOOSE, EDIT_SECTION_CHOOSE_TOPIC,
                 EDIT_COURSE_NAME_DESC, EDIT_SECTION_NAME_DESC,
                 EDIT_TOPIC_JSON, AWAITING_IMAGE, AWAITING_COURSE_JSON,
                 AWAITING_SECTION_JSON, AWAITING_BACKUP_FILE -> true;
            default -> false;
        };
    }

    private boolean isAdmin(long internalUserId) {
        return adminHandler.isAdmin(internalUserId);
    }

    private String formatStudyTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours > 0
                ? String.format(FORMAT_STUDY_TIME_HOURS, hours, minutes)
                : String.format(FORMAT_STUDY_TIME_MINUTES, minutes);
    }

    private void updatePlatformUserId(long internalUserId, long vkUserId) {
        UserContext ctx = sessionService.getCurrentContext(internalUserId);
        ctx.setCurrentPlatformUserId(vkUserId);
        sessionService.updateSessionContext(internalUserId, ctx);
    }
}