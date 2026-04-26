package com.lbt.telegram_learning_bot.bot.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbt.telegram_learning_bot.platform.FileDownloader;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.repository.*;
import com.lbt.telegram_learning_bot.service.*;
import com.lbt.telegram_learning_bot.util.CallbackData;
import lombok.extern.slf4j.Slf4j;

import static com.lbt.telegram_learning_bot.util.Constants.*;

/**
 * Тонкий диспетчер административных команд.
 *
 * <p>Не содержит бизнес-логики — только маршрутизирует запросы в два
 * специализированных обработчика:</p>
 * <ul>
 *   <li>{@link AdminCourseHandler} — CRUD курсов / разделов / тем, импорт, изображения</li>
 *   <li>{@link AdminDatabaseHandler} — резервные копии, восстановление, режим обслуживания</li>
 * </ul>
 *
 * <p>Платформо-специфичные хендлеры ({@code TelegramBotHandler}, {@code VkBotHandler})
 * работают только с этим классом; внутреннее разделение от них скрыто.</p>
 */
@Slf4j
public class AdminHandler extends BaseHandler {

    private final AdminCourseHandler courseHandler;
    private final AdminDatabaseHandler databaseHandler;

    public AdminHandler(MessageSender messageSender,
                        FileDownloader fileDownloader,
                        UserSessionService sessionService,
                        NavigationService navigationService,
                        CourseImportService courseImportService,
                        ZipCourseImportService zipCourseImportService,
                        CourseRepository courseRepository,
                        KeyboardBuilder keyboardBuilder,
                        SectionRepository sectionRepository,
                        TopicRepository topicRepository,
                        BlockRepository blockRepository,
                        QuestionRepository questionRepository,
                        AnswerOptionRepository answerOptionRepository,
                        BlockImageRepository blockImageRepository,
                        QuestionImageRepository questionImageRepository,
                        AdminUserRepository adminUserRepository,
                        UserProgressRepository userProgressRepository,
                        UserStudyTimeRepository userStudyTimeRepository,
                        ObjectMapper objectMapper,
                        UserSettingsService userSettingsService,
                        BackupService backupService,
                        MaintenanceModeService maintenanceModeService,
                        ImageStorageService imageStorageService) {
        super(messageSender, sessionService, navigationService,
                adminUserRepository, userSettingsService, maintenanceModeService);

        this.courseHandler = new AdminCourseHandler(
                messageSender, fileDownloader, sessionService, navigationService,
                courseImportService, zipCourseImportService, courseRepository, keyboardBuilder,
                sectionRepository, topicRepository, blockRepository, questionRepository,
                answerOptionRepository, blockImageRepository, questionImageRepository,
                adminUserRepository, objectMapper, userSettingsService, maintenanceModeService, imageStorageService);

        this.databaseHandler = new AdminDatabaseHandler(
                messageSender, fileDownloader, sessionService, navigationService,
                adminUserRepository, userSettingsService, backupService, maintenanceModeService);
    }

    // ================== Диспетчер callback-ов ==================

    public void handleAdminCallback(Long userId, Integer messageId, String data, int pageSize) {
        CallbackData cb = CallbackData.parse(data);
        String action = cb.action();

        switch (action) {
            // --- курсы ---
            case CALLBACK_CREATE_COURSE:
                courseHandler.promptCreateCourse(userId, messageId);
                break;
            case CALLBACK_EDIT_COURSE:
                courseHandler.promptEditCourse(userId, messageId, pageSize);
                break;
            case CALLBACK_DELETE_COURSE:
                courseHandler.promptDeleteCourse(userId, messageId, pageSize);
                break;
            case CALLBACK_ADMIN_COURSES_MENU:
                courseHandler.showCoursesManagementMenu(userId, messageId);
                break;
            case CALLBACK_SELECT_COURSE_FOR_EDIT:
                cb.getLong(1).ifPresent(id -> courseHandler.handleSelectCourseForEdit(userId, messageId, id));
                break;
            case CALLBACK_SELECT_COURSE_FOR_DELETE:
                cb.getLong(1).ifPresent(id -> courseHandler.handleSelectCourseForDelete(userId, messageId, id));
                break;
            case CALLBACK_EDIT_COURSE_ACTION:
                cb.getString(1).ifPresent(act -> courseHandler.handleEditCourseAction(userId, messageId, act, pageSize));
                break;
            case CALLBACK_SELECT_SECTION_FOR_EDIT:
                cb.getLong(1).ifPresent(id -> courseHandler.handleSelectSectionForEdit(userId, messageId, id));
                break;
            case CALLBACK_EDIT_SECTION_ACTION:
                cb.getString(1).ifPresent(act -> courseHandler.handleEditSectionAction(userId, messageId, act, pageSize));
                break;
            case CALLBACK_SELECT_TOPIC_FOR_EDIT:
                cb.getLong(1).ifPresent(id -> courseHandler.handleSelectTopicForEdit(userId, messageId, id));
                break;
            case CALLBACK_CONFIRM_DELETE_COURSE:
                cb.getLong(1).ifPresent(id -> courseHandler.handleConfirmDeleteCourse(userId, messageId, id));
                break;
            case CALLBACK_RETRY:
                courseHandler.handleRetry(userId, messageId);
                break;
            case CALLBACK_ADMIN_COURSES_PAGE:
                cb.getString(1).ifPresent(src ->
                    cb.getInt(2).ifPresent(page ->
                        courseHandler.handleAdminCoursesPage(userId, messageId, src, page, pageSize)));
                break;
            case CALLBACK_ADMIN_SECTIONS_PAGE:
                cb.getLong(1).ifPresent(courseId ->
                    cb.getInt(2).ifPresent(page ->
                        courseHandler.handleAdminSectionsPage(userId, messageId, courseId, page, pageSize)));
                break;
            case CALLBACK_ADMIN_TOPICS_PAGE:
                cb.getLong(1).ifPresent(sectionId ->
                    cb.getInt(2).ifPresent(page ->
                        courseHandler.handleAdminTopicsPage(userId, messageId, sectionId, page, pageSize)));
                break;
            case CALLBACK_ADMIN_BACK_TO_SECTIONS:
                courseHandler.handleBackToSectionsFromEdit(userId, messageId, pageSize);
                break;
            case CALLBACK_ADMIN_BACK_TO_TOPICS:
                if (cb.size() >= 3) {
                    cb.getLong(1).ifPresent(sectionId ->
                        cb.getInt(2).ifPresent(page ->
                            courseHandler.handleBackToTopicsFromEdit(userId, messageId, sectionId, page, pageSize)));
                } else {
                    courseHandler.handleBackToTopicsFromEdit(userId, messageId, pageSize);
                }
                break;

            // --- база данных ---
            case CALLBACK_ADMIN_DB:
                databaseHandler.showDatabaseMenu(userId, messageId);
                break;
            case CALLBACK_BACKUP_NOW:
                databaseHandler.performBackupNow(userId, messageId);
                break;
            case CALLBACK_RESTORE:
                databaseHandler.showRestoreMenu(userId, messageId);
                break;
            case CALLBACK_RESTORE_SELECT:
                cb.getString(1).ifPresent(fileName ->
                    databaseHandler.restoreFromBackup(userId, messageId, fileName));
                break;
            case CALLBACK_UPLOAD_BACKUP_FILE:
                databaseHandler.promptBackupFileUpload(userId, messageId);
                break;
            case CALLBACK_TOGGLE_MAINTENANCE:
                databaseHandler.toggleMaintenanceMode(userId, messageId);
                break;

            default:
                log.warn("Unknown admin callback action: {}", action);
        }
    }

    // ================== Публичный API (фасад для платформенных хендлеров) ==================

    public void handleDocument(Long userId, Object fileReference) {
        courseHandler.handleDocument(userId, fileReference, databaseHandler);
    }

    public void handleImageUpload(Long userId, Object fileReference) {
        courseHandler.handleImageUpload(userId, fileReference);
    }

    public void showDatabaseMenu(Long userId, Integer messageId) {
        databaseHandler.showDatabaseMenu(userId, messageId);
    }

    public void performBackupNow(Long userId, Integer messageId) {
        databaseHandler.performBackupNow(userId, messageId);
    }

    public void showRestoreMenu(Long userId, Integer messageId) {
        databaseHandler.showRestoreMenu(userId, messageId);
    }

    public void restoreFromBackup(Long userId, Integer messageId, String fileName) {
        databaseHandler.restoreFromBackup(userId, messageId, fileName);
    }

    public void promptBackupFileUpload(Long userId, Integer messageId) {
        databaseHandler.promptBackupFileUpload(userId, messageId);
    }

    public void toggleMaintenanceMode(Long userId, Integer messageId) {
        databaseHandler.toggleMaintenanceMode(userId, messageId);
    }

    public void handleBackToCoursesFromEdit(Long userId, Integer messageId, int pageSize) {
        courseHandler.handleBackToCoursesFromEdit(userId, messageId, pageSize);
    }

    public void handleRetry(Long userId, Integer messageId) {
        courseHandler.handleRetry(userId, messageId);
    }
}
