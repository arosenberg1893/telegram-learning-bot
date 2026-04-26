package com.lbt.telegram_learning_bot.bot;

import com.lbt.telegram_learning_bot.bot.handler.*;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.platform.Platform;
import com.lbt.telegram_learning_bot.repository.AdminUserRepository;
import com.lbt.telegram_learning_bot.service.*;
import com.lbt.telegram_learning_bot.util.CallbackData;
import lombok.extern.slf4j.Slf4j;

import static com.lbt.telegram_learning_bot.util.Constants.*;

/**
 * Платформо-независимый диспетчер пользовательских команд и callback-ов.
 *
 * <p>Содержит единственную копию маршрутизирующей логики, которая ранее была
 * продублирована между {@code TelegramBotHandler} и {@code VkBotHandler}.
 * Оба хендлера становятся тонкими адаптерами, преобразующими платформенные события
 * в вызовы методов этого класса.</p>
 *
 * <p>Добавление нового callback-а теперь требует правки только одного места.</p>
 */
@Slf4j
public class BotDispatcher {

    private final CourseNavigationHandler courseNavHandler;
    private final TestHandler testHandler;
    private final AdminHandler adminHandler;
    private final SettingsHandler settingsHandler;
    private final LinkHandler linkHandler;
    private final UserSessionService sessionService;
    private final AdminUserRepository adminUserRepository;
    private final UserSettingsService userSettingsService;
    private final PdfExportService pdfExportService;

    public BotDispatcher(CourseNavigationHandler courseNavHandler,
                         TestHandler testHandler,
                         AdminHandler adminHandler,
                         SettingsHandler settingsHandler,
                         LinkHandler linkHandler,
                         UserSessionService sessionService,
                         AdminUserRepository adminUserRepository,
                         UserSettingsService userSettingsService,
                         PdfExportService pdfExportService) {
        this.courseNavHandler = courseNavHandler;
        this.testHandler = testHandler;
        this.adminHandler = adminHandler;
        this.settingsHandler = settingsHandler;
        this.linkHandler = linkHandler;
        this.sessionService = sessionService;
        this.adminUserRepository = adminUserRepository;
        this.userSettingsService = userSettingsService;
        this.pdfExportService = pdfExportService;
    }

    /**
     * Маршрутизирует callback с любой платформы.
     *
     * @param userId       внутренний ID пользователя
     * @param messageId    ID сообщения для редактирования (может быть null)
     * @param data         строка callback-данных
     * @param platform     платформа-источник
     * @param platformCtx  платформо-специфичный контекст (sender, externalUserId и т.д.)
     */
    public void dispatchCallback(Long userId, Integer messageId, String data,
                                 Platform platform, PlatformContext platformCtx) {
        CallbackData cb = CallbackData.parse(data);
        String action = cb.action();
        int pageSize = userSettingsService.getSettings(userId).getPageSize();

        switch (action) {
            // --- навигация ---
            case CALLBACK_MY_COURSES ->
                courseNavHandler.handleMyCourses(userId, messageId, 0, pageSize);
            case CALLBACK_ALL_COURSES ->
                courseNavHandler.handleAllCourses(userId, messageId, 0, pageSize);
            case CALLBACK_SEARCH_COURSES ->
                courseNavHandler.promptSearch(userId, messageId);
            case CALLBACK_COURSES_PAGE ->
                cb.getString(1).ifPresent(src ->
                    cb.getInt(2).ifPresent(page ->
                        courseNavHandler.handleCoursesPage(userId, messageId, src, page, pageSize)));
            case CALLBACK_SELECT_COURSE ->
                cb.getLong(1).ifPresent(id ->
                    courseNavHandler.handleSelectCourse(userId, messageId, id, pageSize));
            case CALLBACK_SELECT_SECTION ->
                cb.getLong(1).ifPresent(id ->
                    courseNavHandler.handleSelectSection(userId, messageId, id, pageSize));
            case CALLBACK_SELECT_TOPIC ->
                cb.getLong(1).ifPresent(id ->
                    courseNavHandler.handleSelectTopic(userId, messageId, id));
            case CALLBACK_SECTIONS_PAGE ->
                cb.getLong(1).ifPresent(courseId ->
                    cb.getInt(2).ifPresent(page ->
                        courseNavHandler.handleSectionsPage(userId, messageId, courseId, page, pageSize)));
            case CALLBACK_TOPICS_PAGE ->
                cb.getLong(1).ifPresent(sectionId ->
                    cb.getInt(2).ifPresent(page ->
                        courseNavHandler.handleTopicsPage(userId, messageId, sectionId, page, pageSize)));
            case CALLBACK_BACK_TO_COURSES -> {
                BotState state = sessionService.getCurrentState(userId);
                if (state.isAdminEditState()) {
                    adminHandler.handleBackToCoursesFromEdit(userId, messageId, pageSize);
                } else {
                    courseNavHandler.handleBackToCourses(userId, messageId, pageSize);
                }
            }
            case CALLBACK_BACK_TO_SECTIONS ->
                courseNavHandler.handleBackToSections(userId, messageId, pageSize);
            case CALLBACK_BACK_TO_TOPICS ->
                courseNavHandler.handleBackToTopics(userId, messageId, pageSize);
            case CALLBACK_NEXT_BLOCK ->
                courseNavHandler.handleNextBlock(userId, messageId);
            case CALLBACK_PREV_BLOCK ->
                courseNavHandler.handlePrevBlock(userId, messageId);

            // --- тесты ---
            case CALLBACK_NEXT_QUESTION ->
                testHandler.handleNextQuestion(userId, messageId);
            case CALLBACK_PREV_QUESTION ->
                testHandler.handlePrevQuestion(userId, messageId);
            case CALLBACK_ANSWER ->
                cb.getLong(1).ifPresent(qId ->
                    cb.getLong(2).ifPresent(aId ->
                        testHandler.handleAnswer(userId, messageId, qId, aId)));
            case CALLBACK_BACK_TO_BLOCK_TEXT ->
                testHandler.handleBackToBlockText(userId, messageId);
            case CALLBACK_TEST_TOPIC ->
                cb.getLong(1).ifPresent(id -> testHandler.handleTestTopic(userId, messageId, id));
            case CALLBACK_TEST_SECTION ->
                cb.getLong(1).ifPresent(id -> testHandler.handleTestSection(userId, messageId, id));
            case CALLBACK_TEST_COURSE ->
                cb.getLong(1).ifPresent(id -> testHandler.handleTestCourse(userId, messageId, id));
            case CALLBACK_MY_MISTAKES ->
                testHandler.handleMyMistakes(userId, messageId);

            // --- экспорт ---
            case CALLBACK_EXPORT_TOPIC ->
                courseNavHandler.handleExportTopic(userId, messageId, data);
            case CALLBACK_EXPORT_SECTION ->
                courseNavHandler.handleExportSection(userId, messageId, data);
            case CALLBACK_EXPORT_COURSE ->
                courseNavHandler.handleExportCourse(userId, messageId, data);
            case CALLBACK_EXPORT_PDF ->
                platformCtx.handleExportPdf(userId, messageId);

            // --- администрирование ---
            case CALLBACK_CREATE_COURSE, CALLBACK_EDIT_COURSE, CALLBACK_DELETE_COURSE,
                 CALLBACK_ADMIN_COURSES_MENU, CALLBACK_SELECT_COURSE_FOR_EDIT,
                 CALLBACK_SELECT_COURSE_FOR_DELETE, CALLBACK_EDIT_COURSE_ACTION,
                 CALLBACK_SELECT_SECTION_FOR_EDIT, CALLBACK_EDIT_SECTION_ACTION,
                 CALLBACK_SELECT_TOPIC_FOR_EDIT, CALLBACK_CONFIRM_DELETE_COURSE,
                 CALLBACK_RETRY, CALLBACK_ADMIN_COURSES_PAGE, CALLBACK_ADMIN_SECTIONS_PAGE,
                 CALLBACK_ADMIN_TOPICS_PAGE, CALLBACK_ADMIN_BACK_TO_SECTIONS,
                 CALLBACK_ADMIN_BACK_TO_TOPICS -> {
                if (!isAdmin(userId)) return;
                adminHandler.handleAdminCallback(userId, messageId, data, pageSize);
            }
            case CALLBACK_ADMIN_DB -> { if (!isAdmin(userId)) return;
                adminHandler.showDatabaseMenu(userId, messageId); }
            case CALLBACK_BACKUP_NOW -> { if (!isAdmin(userId)) return;
                adminHandler.performBackupNow(userId, messageId); }
            case CALLBACK_RESTORE -> { if (!isAdmin(userId)) return;
                adminHandler.showRestoreMenu(userId, messageId); }
            case CALLBACK_RESTORE_SELECT -> { if (!isAdmin(userId)) return;
                cb.getString(1).ifPresent(f -> adminHandler.restoreFromBackup(userId, messageId, f)); }
            case CALLBACK_UPLOAD_BACKUP_FILE -> { if (!isAdmin(userId)) return;
                adminHandler.promptBackupFileUpload(userId, messageId); }
            case CALLBACK_TOGGLE_MAINTENANCE -> { if (!isAdmin(userId)) return;
                adminHandler.toggleMaintenanceMode(userId, messageId); }

            // --- статистика ---
            case CALLBACK_STATISTICS ->
                platformCtx.handleStatistics(userId, messageId,
                        cb.getString(1).filter(CALLBACK_BACK::equals).isPresent());

            // --- настройки ---
            case CALLBACK_SETTINGS ->
                settingsHandler.showSettingsMenu(userId, messageId);
            case CALLBACK_SETTINGS_SHUFFLE ->
                settingsHandler.toggleShuffle(userId, messageId);
            case CALLBACK_SETTINGS_PAGESIZE ->
                settingsHandler.showPageSizeOptions(userId, messageId);
            case CALLBACK_SETTINGS_PAGESIZE_OTHER ->
                settingsHandler.promptPageSizeInput(userId, messageId);
            case CALLBACK_SETTINGS_QUESTIONS ->
                settingsHandler.showQuestionsPerBlockOptions(userId, messageId);
            case CALLBACK_SETTINGS_EXPLANATIONS ->
                settingsHandler.toggleExplanations(userId, messageId);
            case CALLBACK_SETTINGS_RESET ->
                settingsHandler.confirmResetProgress(userId, messageId);
            case CALLBACK_SETTINGS_PAGESIZE_SET ->
                cb.getInt(1).ifPresent(size -> settingsHandler.setPageSize(userId, messageId, size));
            case CALLBACK_SETTINGS_QUESTIONS_SET ->
                cb.getInt(1).ifPresent(n -> settingsHandler.setQuestionsPerBlock(userId, messageId, n));
            case CALLBACK_SETTINGS_RESET_CONFIRM ->
                settingsHandler.resetProgress(userId, messageId);
            case CALLBACK_SETTINGS_PDF_QUESTIONS ->
                settingsHandler.togglePdfQuestions(userId, messageId);

            // --- привязка аккаунтов ---
            case CALLBACK_LINK_GENERATE ->
                platformCtx.handleLinkGenerate(userId);
            case CALLBACK_LINK_KEEP_TELEGRAM ->
                cb.getString(1).ifPresent(code -> platformCtx.handleLinkKeepTelegram(userId, code));
            case CALLBACK_LINK_KEEP_VK ->
                cb.getString(1).ifPresent(code -> platformCtx.handleLinkKeepVk(userId, code));
            case CALLBACK_LINK_MERGE ->
                cb.getString(1).ifPresent(code -> platformCtx.handleLinkMerge(userId, code));
            case CALLBACK_LINK_MERGE_SETTINGS_TG ->
                cb.getString(1).ifPresent(code -> platformCtx.handleLinkMergeSettingsTg(userId, code));
            case CALLBACK_LINK_MERGE_SETTINGS_VK ->
                cb.getString(1).ifPresent(code -> platformCtx.handleLinkMergeSettingsVk(userId, code));
            // обратная совместимость
            case CALLBACK_LINK_RESOLVE_KEEP_THIS ->
                cb.getString(1).ifPresent(code -> platformCtx.handleLinkKeepTelegram(userId, code));
            case CALLBACK_LINK_RESOLVE_KEEP_OTHER ->
                cb.getString(1).ifPresent(code -> platformCtx.handleLinkKeepVk(userId, code));

            // --- общие ---
            case CALLBACK_MAIN_MENU -> platformCtx.sendMainMenu(userId, messageId);
            case CALLBACK_BACK -> platformCtx.handleBack(userId, messageId);

            default -> log.warn("[{}] Unknown callback action: {}", platform, action);
        }
    }

    /**
     * Маршрутизирует текстовое сообщение пользователя по текущему состоянию сессии.
     *
     * @param userId      внутренний ID пользователя
     * @param text        текст сообщения (уже обрезан)
     * @param platformCtx платформо-специфичный контекст
     */
    public void dispatchMessage(Long userId, String text, PlatformContext platformCtx) {
        BotState state = sessionService.getCurrentState(userId);
        switch (state) {
            case AWAITING_SEARCH_QUERY -> {
                int pageSize = userSettingsService.getSettings(userId).getPageSize();
                courseNavHandler.handleSearchQuery(userId, text, pageSize);
            }
            case AWAITING_LINK_CODE ->
                platformCtx.handleLinkCode(userId, text);
            case AWAITING_PAGE_SIZE_INPUT -> {
                try {
                    int size = Integer.parseInt(text.trim());
                    settingsHandler.handlePageSizeInput(userId, String.valueOf(size), null);
                } catch (NumberFormatException e) {
                    platformCtx.sendMainMenu(userId, null);
                }
            }
            case AWAITING_BACKUP_FILE ->
                platformCtx.notifyExpectingFile(userId);
            default ->
                platformCtx.sendMainMenu(userId, null);
        }
    }

    private boolean isAdmin(Long userId) {
        return adminUserRepository.existsByUserId(userId);
    }

    /**
     * Интерфейс для платформо-специфичных операций, которые диспетчер не может выполнить сам:
     * отправка главного меню, привязка аккаунтов, PDF-экспорт.
     *
     * <p>Каждая платформа реализует его по-своему:
     * Telegram — через {@code TelegramPlatformContext},
     * VK — через {@code VkPlatformContext}.</p>
     */
    public interface PlatformContext {
        void sendMainMenu(Long userId, Integer messageId);
        void handleBack(Long userId, Integer messageId);
        void handleStatistics(Long userId, Integer messageId, boolean back);
        void handleExportPdf(Long userId, Integer messageId);
        void handleLinkGenerate(Long userId);
        void handleLinkCode(Long userId, String code);
        void handleLinkKeepTelegram(Long userId, String code);
        void handleLinkKeepVk(Long userId, String code);
        void handleLinkMerge(Long userId, String code);
        void handleLinkMergeSettingsTg(Long userId, String code);
        void handleLinkMergeSettingsVk(Long userId, String code);
        void notifyExpectingFile(Long userId);
    }
}
