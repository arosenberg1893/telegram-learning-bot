package com.lbt.telegram_learning_bot.bot.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.bot.PendingImage;
import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.dto.*;
import com.lbt.telegram_learning_bot.entity.*;
import com.lbt.telegram_learning_bot.exception.InvalidJsonException;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.platform.FileDownloader;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.repository.*;
import com.lbt.telegram_learning_bot.service.*;
import com.lbt.telegram_learning_bot.util.BackupLogHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
public class AdminHandler extends BaseHandler {

    private final CourseImportService courseImportService;
    private final ZipCourseImportService zipCourseImportService;
    private final FileDownloader fileDownloader;
    private final CourseRepository courseRepository;
    private final SectionRepository sectionRepository;
    private final TopicRepository topicRepository;
    private final BlockRepository blockRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;
    private final BlockImageRepository blockImageRepository;
    private final QuestionImageRepository questionImageRepository;
    private final AdminUserRepository adminUserRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserStudyTimeRepository userStudyTimeRepository;
    private final ObjectMapper objectMapper;
    private final KeyboardBuilder keyboardBuilder;
    private final BackupService backupService;
    private final MaintenanceModeService maintenanceModeService;

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
                        MaintenanceModeService maintenanceModeService) {
        super(messageSender, sessionService, navigationService, adminUserRepository, userSettingsService, maintenanceModeService);
        this.courseImportService = courseImportService;
        this.zipCourseImportService = zipCourseImportService;
        this.fileDownloader = fileDownloader;
        this.courseRepository = courseRepository;
        this.sectionRepository = sectionRepository;
        this.topicRepository = topicRepository;
        this.blockRepository = blockRepository;
        this.questionRepository = questionRepository;
        this.answerOptionRepository = answerOptionRepository;
        this.blockImageRepository = blockImageRepository;
        this.questionImageRepository = questionImageRepository;
        this.adminUserRepository = adminUserRepository;
        this.userProgressRepository = userProgressRepository;
        this.userStudyTimeRepository = userStudyTimeRepository;
        this.objectMapper = objectMapper;
        this.keyboardBuilder = keyboardBuilder;
        this.backupService = backupService;
        this.maintenanceModeService = maintenanceModeService;
    }

    // ================== Обработка callback от диспетчера ==================
    public void handleAdminCallback(Long userId, Integer messageId, String data, int pageSize) {
        String[] parts = data.split(":", 3);
        String action = parts[0];

        switch (action) {
            case CALLBACK_CREATE_COURSE:
                promptCreateCourse(userId, messageId);
                break;
            case CALLBACK_EDIT_COURSE:
                promptEditCourse(userId, messageId, pageSize);
                break;
            case CALLBACK_DELETE_COURSE:
                promptDeleteCourse(userId, messageId, pageSize);
                break;
            case CALLBACK_ADMIN_COURSES_MENU:
                showCoursesManagementMenu(userId, messageId);
                break;
            case CALLBACK_SELECT_COURSE_FOR_EDIT:
                handleSelectCourseForEdit(userId, messageId, Long.parseLong(parts[1]));
                break;
            case CALLBACK_SELECT_COURSE_FOR_DELETE:
                handleSelectCourseForDelete(userId, messageId, Long.parseLong(parts[1]));
                break;
            case CALLBACK_EDIT_COURSE_ACTION:
                handleEditCourseAction(userId, messageId, parts[1], pageSize);
                break;
            case CALLBACK_SELECT_SECTION_FOR_EDIT:
                handleSelectSectionForEdit(userId, messageId, Long.parseLong(parts[1]));
                break;
            case CALLBACK_EDIT_SECTION_ACTION:
                handleEditSectionAction(userId, messageId, parts[1], pageSize);
                break;
            case CALLBACK_SELECT_TOPIC_FOR_EDIT:
                handleSelectTopicForEdit(userId, messageId, Long.parseLong(parts[1]));
                break;
            case CALLBACK_CONFIRM_DELETE_COURSE:
                handleConfirmDeleteCourse(userId, messageId, Long.parseLong(parts[1]));
                break;
            case CALLBACK_RETRY:
                handleRetry(userId, messageId);
                break;
            case CALLBACK_ADMIN_COURSES_PAGE:
                handleAdminCoursesPage(userId, messageId, parts[1], Integer.parseInt(parts[2]), pageSize);
                break;
            case CALLBACK_ADMIN_SECTIONS_PAGE:
                handleAdminSectionsPage(userId, messageId, Long.parseLong(parts[1]), Integer.parseInt(parts[2]), pageSize);
                break;
            case CALLBACK_ADMIN_TOPICS_PAGE:
                handleAdminTopicsPage(userId, messageId, Long.parseLong(parts[1]), Integer.parseInt(parts[2]), pageSize);
                break;
            case CALLBACK_ADMIN_BACK_TO_SECTIONS:
                handleBackToSectionsFromEdit(userId, messageId, pageSize);
                break;
            case CALLBACK_ADMIN_BACK_TO_TOPICS:
                if (parts.length >= 3) {
                    Long sectionId = Long.parseLong(parts[1]);
                    int page = Integer.parseInt(parts[2]);
                    handleBackToTopicsFromEdit(userId, messageId, sectionId, page, pageSize);
                } else {
                    handleBackToTopicsFromEdit(userId, messageId, pageSize);
                }
                break;

            case CALLBACK_ADMIN_DB:
                showDatabaseMenu(userId, messageId);
                break;
            case CALLBACK_BACKUP_NOW:
                performBackupNow(userId, messageId);
                break;
            case CALLBACK_RESTORE:
                showRestoreMenu(userId, messageId);
                break;
            case CALLBACK_RESTORE_SELECT:
                restoreFromBackup(userId, messageId, parts[1]);
                break;
            case CALLBACK_UPLOAD_BACKUP_FILE:
                promptBackupFileUpload(userId, messageId);
                break;
            case CALLBACK_TOGGLE_MAINTENANCE:
                toggleMaintenanceMode(userId, messageId);
                break;

            default:
                log.warn("Unknown admin callback action: {}", action);
        }
    }

    // ================== Меню управления курсами ==================
    public void showCoursesManagementMenu(Long userId, Integer messageId) {
        BotKeyboard keyboard = new BotKeyboard()
                .addRow(BotButton.callback(BUTTON_CREATE_COURSE, CALLBACK_CREATE_COURSE))
                .addRow(BotButton.callback(BUTTON_EDIT_COURSE, CALLBACK_EDIT_COURSE))
                .addRow(BotButton.callback(BUTTON_DELETE_COURSE, CALLBACK_DELETE_COURSE))
                .addRow(BotButton.callback(BUTTON_MAIN_MENU, CALLBACK_MAIN_MENU));
        editMessage(userId, messageId, "Управление курсами:", keyboard);
    }

    // ================== Меню управления БД ==================
    public void showDatabaseMenu(Long userId, Integer messageId) {
        try {
            List<BackupInfo> backups = backupService.getLatestBackups();
            String backupInfo = formatBackupInfo(backups);
            String maintenanceStatus = maintenanceModeService.isMaintenance()
                    ? "✅ Включён"
                    : "❌ Выключен";

            String text = String.format("""
                💾 **Управление базой данных**
                
                📋 Последние резервные копии:
                %s
                
                🔧 Режим обслуживания: %s
                """, backupInfo, maintenanceStatus);

            BotKeyboard keyboard = new BotKeyboard()
                    .addRow(BotButton.callback("📤 Создать резервную копию", CALLBACK_BACKUP_NOW))
                    .addRow(BotButton.callback("🔄 Восстановить из копии", CALLBACK_RESTORE))
                    .addRow(BotButton.callback("📁 Загрузить свой файл", CALLBACK_UPLOAD_BACKUP_FILE))
                    .addRow(BotButton.callback(maintenanceModeService.isMaintenance() ? "🔓 Выключить режим обслуживания" : "🔒 Включить режим обслуживания", CALLBACK_TOGGLE_MAINTENANCE))
                    .addRow(BotButton.callback(BUTTON_MAIN_MENU, CALLBACK_MAIN_MENU));

            if (messageId != null) {
                editMessage(userId, messageId, text, keyboard);
            } else {
                sendMessage(userId, text, keyboard);
            }
        } catch (Exception e) {
            log.error("Error showing database menu for user {}", userId, e);
            String errorText = "❌ Не удалось получить информацию о резервных копиях.";
            BotKeyboard errorKeyboard = new BotKeyboard().addRow(BotButton.callback("🔙 Назад", CALLBACK_ADMIN_DB));
            if (messageId != null) {
                editMessage(userId, messageId, errorText, errorKeyboard);
            } else {
                sendMessage(userId, errorText, errorKeyboard);
            }
        }
    }

    private String formatBackupInfo(List<BackupInfo> backups) {
        if (backups.isEmpty()) {
            return "Нет сохранённых резервных копий.";
        }
        StringBuilder sb = new StringBuilder();
        for (BackupInfo backup : backups) {
            sb.append("• ").append(backup.getFormattedTimestamp()).append(" (").append(backup.getFormattedSize()).append(")\n");
        }
        return sb.toString();
    }

    // ================== Резервное копирование ==================
    public void performBackupNow(Long userId, Integer messageId) {
        BackupLogHelper.logManualBackupStarted(userId);
        Integer progressMsgId = sendProgressMessage(userId);
        try {
            BackupService.BackupResult result = backupService.createAndUploadBackup();
            BackupLogHelper.logManualBackupSuccess(userId, result.fileName());

            StringBuilder sb = new StringBuilder();
            sb.append("✅ Резервная копия создана.\nФайл: ").append(result.fileName()).append("\n\n");
            sb.append("Загрузка в облачные хранилища:\n");
            for (Map.Entry<String, Boolean> entry : result.uploadResults().entrySet()) {
                String emoji = entry.getValue() ? "✅" : "❌";
                sb.append(emoji).append(" ").append(entry.getKey()).append("\n");
            }

            BotKeyboard keyboard = new BotKeyboard()
                    .addRow(BotButton.callback("🔙 Назад в Adm БД", CALLBACK_ADMIN_DB));

            editMessage(userId, messageId, sb.toString(), keyboard);
        } catch (Exception e) {
            log.error("Manual backup failed for user {}", userId, e);
            BackupLogHelper.logManualBackupError(userId, e.getMessage());
            String errorMsg = e.getMessage().replace("_", "\\_").replace("*", "\\*").replace("[", "\\[").replace("]", "\\]");
            sendMessage(userId, "❌ Ошибка при создании резервной копии: " + errorMsg, createBackToMainKeyboard());
        } finally {
            if (progressMsgId != null) deleteMessage(userId, progressMsgId);
        }
    }

    public void showRestoreMenu(Long userId, Integer messageId) {
        try {
            List<BackupInfo> backups = backupService.getLatestBackups();
            if (backups.isEmpty()) {
                String text = "❌ Нет сохранённых резервных копий для восстановления.";
                BotKeyboard keyboard = new BotKeyboard().addRow(BotButton.callback("🔙 Назад", CALLBACK_ADMIN_DB));
                if (messageId != null) {
                    editMessage(userId, messageId, text, keyboard);
                } else {
                    sendMessage(userId, text, keyboard);
                }
                return;
            }
            BotKeyboard keyboard = new BotKeyboard();
            for (BackupInfo backup : backups) {
                String buttonText = backup.getFormattedTimestamp() + " (" + backup.getFormattedSize() + ")";
                keyboard.addRow(BotButton.callback(buttonText, CALLBACK_RESTORE_SELECT + ":" + backup.getFileName()));
            }
            keyboard.addRow(BotButton.callback("🔙 Назад", CALLBACK_ADMIN_DB));
            editMessage(userId, messageId, "Выберите резервную копию для восстановления:", keyboard);
        } catch (Exception e) {
            log.error("Error showing restore menu for user {}", userId, e);
            sendMessage(userId, "❌ Не удалось получить список резервных копий.", createBackToMainKeyboard());
        }
    }

    public void restoreFromBackup(Long userId, Integer messageId, String fileName) {
        BackupLogHelper.logRestoreStarted(userId, fileName);
        Integer progressMsgId = sendProgressMessage(userId);
        try {
            byte[] backupData = backupService.downloadBackup(fileName);
            backupService.restoreFromDump(backupData);
            BackupLogHelper.logRestoreSuccess(userId, fileName);
            sendMessage(userId, "✅ База данных успешно восстановлена из резервной копии.\nФайл: " + fileName);
            showDatabaseMenu(userId, messageId);
        } catch (Exception e) {
            log.error("Restore failed for user {} from file {}", userId, fileName, e);
            BackupLogHelper.logRestoreError(userId, fileName, e.getMessage());
            sendMessage(userId, "❌ Ошибка при восстановлении базы данных: " + e.getMessage(), createBackToMainKeyboard());
        } finally {
            if (progressMsgId != null) deleteMessage(userId, progressMsgId);
        }
    }

    public void promptBackupFileUpload(Long userId, Integer messageId) {
        editMessage(userId, messageId, "📁 Отправьте файл резервной копии (дамп БД в формате .dump) для восстановления.\n\nВНИМАНИЕ: текущая база данных будет заменена. Перед восстановлением будет автоматически создана резервная копия.", createCancelKeyboard());
        sessionService.updateSessionState(userId, BotState.AWAITING_BACKUP_FILE);
    }

    public void handleBackupFileUpload(Long userId, Object fileReference) {
        if (!(fileReference instanceof byte[] fileContent)) {
            sendMessage(userId, "❌ Не удалось прочитать файл. Попробуйте ещё раз.", createCancelKeyboard());
            sessionService.updateSessionState(userId, BotState.MAIN_MENU);
            return;
        }
        BackupLogHelper.logRestoreStarted(userId, "user_upload");
        Integer progressMsgId = sendProgressMessage(userId);
        try {
            backupService.restoreFromUserUpload(fileContent);
            BackupLogHelper.logRestoreSuccess(userId, "user_upload");
            sendMessage(userId, "✅ База данных успешно восстановлена из загруженного файла.");
            showDatabaseMenu(userId, null);
        } catch (Exception e) {
            log.error("Restore from user upload failed for user {}", userId, e);
            BackupLogHelper.logRestoreError(userId, "user_upload", e.getMessage());
            sendMessage(userId, "❌ Ошибка при восстановлении из загруженного файла: " + e.getMessage(), createBackToMainKeyboard());
        } finally {
            if (progressMsgId != null) deleteMessage(userId, progressMsgId);
        }
        sessionService.updateSessionState(userId, BotState.MAIN_MENU);
    }

    // ================== Режим обслуживания ==================
    public void toggleMaintenanceMode(Long userId, Integer messageId) {
        boolean wasMaintenance = maintenanceModeService.isMaintenance();
        if (wasMaintenance) {
            maintenanceModeService.disableMaintenance();
            BackupLogHelper.logMaintenanceDisabled(userId);
        } else {
            maintenanceModeService.enableMaintenance();
            BackupLogHelper.logMaintenanceEnabled(userId);
        }
        // Обновляем меню, где уже отображается актуальный статус
        showDatabaseMenu(userId, messageId);
    }

    // ================== Публичные методы обработки сообщений ==================

    public void handleRetry(Long userId, Integer messageId) {
        BotState state = sessionService.getCurrentState(userId);
        if (state == BotState.AWAITING_COURSE_JSON) {
            promptCreateCourse(userId, messageId);
        } else if (state == BotState.AWAITING_IMAGE) {
            promptCurrentImage(userId, messageId);
        } else if (state == BotState.EDIT_TOPIC_JSON) {
            editMessage(userId, messageId, MSG_SEND_JSON_TOPIC, createAdminCancelKeyboardWithBackToTopics());
        } else if (state == BotState.AWAITING_SECTION_JSON) {
            promptEditSectionJson(userId, messageId);
        }
    }

    public void handleDocument(Long userId, Object fileReference) {
        try {
            byte[] fileContent = fileDownloader.downloadFile(fileReference);
            BotState currentState = sessionService.getCurrentState(userId);

            if (isZipFile(fileContent)) {
                if (currentState == BotState.AWAITING_COURSE_JSON) {
                    processCourseZip(userId, fileContent);
                } else {
                    sendMessage(userId, MSG_UNEXPECTED_FILE, createCancelKeyboard());
                }
                return;
            }

            if (currentState == BotState.AWAITING_COURSE_JSON) {
                processCourseJson(userId, fileContent);
            } else if (currentState == BotState.EDIT_COURSE_NAME_DESC) {
                processCourseNameDescJson(userId, fileContent);
            } else if (currentState == BotState.EDIT_SECTION_NAME_DESC) {
                processSectionNameDescJson(userId, fileContent);
            } else if (currentState == BotState.EDIT_TOPIC_JSON) {
                processTopicJson(userId, fileContent);
            } else if (currentState == BotState.AWAITING_SECTION_JSON) {
                processSectionJson(userId, fileContent);
            } else if (currentState == BotState.AWAITING_BACKUP_FILE) {
                handleBackupFileUpload(userId, fileContent);
            } else {
                sendMessage(userId, MSG_UNEXPECTED_FILE, createCancelKeyboard());
            }
        } catch (Exception e) {
            log.error("Error processing document for user {}", userId, e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        }
    }

    /**
     * Обрабатывает загрузку изображения от администратора.
     *
     * @param userId        внутренний ID пользователя
     * @param fileReference объект-ссылка на файл (byte[] для Telegram,
     *                      Map для VK) или {@code null}, если платформа не поддерживает загрузку
     */
    public void handleImageUpload(Long userId, Object fileReference) {
        BotState currentState = sessionService.getCurrentState(userId);
        if (currentState != BotState.AWAITING_IMAGE) {
            // Изображение получено вне ожидаемого потока — игнорируем
            return;
        }
        if (fileReference == null) {
            sendMessage(userId, "Загрузка изображений через VK не поддерживается.", createCancelKeyboard());
            return;
        }
        try {
            byte[] imageBytes = fileDownloader.downloadFile(fileReference);
            saveImageToCurrentSlot(userId, imageBytes);
        } catch (Exception e) {
            log.error("Error processing image upload for user {}", userId, e);
            sendMessage(userId, MSG_SAVE_IMAGE_ERROR, createCancelKeyboard());
        }
    }

    private void saveImageToCurrentSlot(Long userId, byte[] imageBytes) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long entityId = context.getTargetEntityId();
        String entityType = context.getTargetEntityType();

        if (entityId == null || entityType == null) {
            log.warn("saveImageToCurrentSlot: entityId or entityType is null for user {}", userId);
            sendMessage(userId, MSG_SAVE_IMAGE_ERROR, createCancelKeyboard());
            return;
        }

        try {
            // Сохраняем файл на диск во временную директорию
            java.nio.file.Path dir = java.nio.file.Paths.get("images", entityType);
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path filePath = dir.resolve(entityId + ".jpg");
            java.nio.file.Files.write(filePath, imageBytes);

            // Обновляем путь к файлу в базе данных
            if (ENTITY_BLOCK.equals(entityType)) {
                blockImageRepository.findById(entityId).ifPresent(bi -> {
                    bi.setFilePath(filePath.toString());
                    blockImageRepository.save(bi);
                });
            } else if (ENTITY_QUESTION.equals(entityType)) {
                questionImageRepository.findById(entityId).ifPresent(qi -> {
                    qi.setFilePath(filePath.toString());
                    questionImageRepository.save(qi);
                });
            }

            // Переходим к следующему изображению
            context.setCurrentImageIndex(context.getCurrentImageIndex() + 1);
            sessionService.updateSessionContext(userId, context);
            requestNextImage(userId, null);
        } catch (Exception e) {
            log.error("Failed to save image for entity {} {}", entityType, entityId, e);
            sendMessage(userId, MSG_SAVE_IMAGE_ERROR, createCancelKeyboard());
        }
    }

    public void promptCreateCourse(Long userId, Integer messageId) {
        String text = MSG_SEND_JSON_COURSE;
        if (messageId != null) {
            editMessage(userId, messageId, text, createCancelKeyboard());
        } else {
            sendMessage(userId, text, createCancelKeyboard());
        }
        sessionService.updateSessionState(userId, BotState.AWAITING_COURSE_JSON);
    }

    public void promptEditCourse(Long userId, Integer messageId, int pageSize) {
        showEditCoursesPage(userId, messageId, 0, pageSize);
    }

    public void promptDeleteCourse(Long userId, Integer messageId, int pageSize) {
        showDeleteCoursesPage(userId, messageId, 0, pageSize);
    }

    public void promptCurrentImage(Long userId, Integer messageId) {
        requestNextImage(userId, messageId);
    }

    public void showEditCoursesPage(Long userId, Integer messageId, int page, int pageSize) {
        var result = navigationService.getAllCoursesPage(page, pageSize);
        if (result.getItems().isEmpty()) {
            editMessage(userId, messageId, MSG_NO_COURSES_TO_EDIT, createBackToMainKeyboard());
            return;
        }
        String text = String.format(FORMAT_EDIT_COURSES_HEADER,
                page + 1, result.getTotalPages(), result.getTotalItems());
        BotKeyboard keyboard = keyboardBuilder.buildCoursesKeyboardForAdminBot(result, SOURCE_MY_COURSES, CALLBACK_SELECT_COURSE_FOR_EDIT);
        editMessage(userId, messageId, text, keyboard);
    }

    private void showDeleteCoursesPage(Long userId, Integer messageId, int page, int pageSize) {
        var result = navigationService.getAllCoursesPage(page, pageSize);
        if (result.getItems().isEmpty()) {
            editMessage(userId, messageId, MSG_NO_COURSES_TO_DELETE, createBackToMainKeyboard());
            return;
        }
        String text = String.format(FORMAT_DELETE_COURSES_HEADER,
                page + 1, result.getTotalPages(), result.getTotalItems());
        BotKeyboard keyboard = keyboardBuilder.buildCoursesKeyboardForAdminBot(
                result, SOURCE_MY_COURSES, CALLBACK_SELECT_COURSE_FOR_DELETE);
        editMessage(userId, messageId, text, keyboard);
    }

    public void handleSelectCourseForEdit(Long userId, Integer messageId, Long courseId) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setEditingCourseId(courseId);
        sessionService.updateSessionContext(userId, context);

        String text = MSG_WHAT_TO_CHANGE;
        BotKeyboard keyboard = new BotKeyboard().addRow(
                BotButton.callback(BUTTON_NAME_DESC, CALLBACK_EDIT_COURSE_ACTION + ":" + ACTION_NAME_DESC),
                BotButton.callback(BUTTON_SECTIONS, CALLBACK_EDIT_COURSE_ACTION + ":" + ACTION_SECTIONS)
        ).addRow(BotButton.callback(BUTTON_BACK, CALLBACK_EDIT_COURSE));
        editMessage(userId, messageId, text, keyboard);
        sessionService.updateSessionState(userId, BotState.EDIT_COURSE_CHOOSE_ACTION);
    }

    public void handleSelectCourseForDelete(Long userId, Integer messageId, Long courseId) {
        String text = MSG_CONFIRM_DELETE_COURSE;
        BotKeyboard keyboard = new BotKeyboard().addRow(
                BotButton.callback(BUTTON_YES_DELETE, CALLBACK_CONFIRM_DELETE_COURSE + ":" + courseId),
                BotButton.callback(BUTTON_NO, CALLBACK_EDIT_COURSE)
        );
        editMessage(userId, messageId, text, keyboard);
    }

    public void handleEditCourseAction(Long userId, Integer messageId, String action, int pageSize) {
        if (ACTION_NAME_DESC.equals(action)) {
            editMessage(userId, messageId, MSG_SEND_JSON_COURSE_NAME_DESC, createCancelKeyboard());
            sessionService.updateSessionState(userId, BotState.EDIT_COURSE_NAME_DESC);
        } else if (ACTION_SECTIONS.equals(action)) {
            UserContext context = sessionService.getCurrentContext(userId);
            Long courseId = context.getEditingCourseId();
            var result = navigationService.getSectionsPage(courseId, 0, pageSize);
            String text = MSG_SELECT_SECTION;
            BotKeyboard keyboard = keyboardBuilder.buildSectionsKeyboardForAdminBot(
                    result, courseId, CALLBACK_SELECT_SECTION_FOR_EDIT, CALLBACK_EDIT_COURSE);
            editMessage(userId, messageId, text, keyboard);
            sessionService.updateSessionState(userId, BotState.EDIT_COURSE_SECTION_CHOOSE);
        }
    }

    public void handleSelectSectionForEdit(Long userId, Integer messageId, Long sectionId) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setEditingSectionId(sectionId);
        sessionService.updateSessionContext(userId, context);

        String text = MSG_WHAT_TO_CHANGE_SECTION;
        BotKeyboard keyboard = new BotKeyboard().addRow(
                BotButton.callback(BUTTON_NAME_DESC, CALLBACK_EDIT_SECTION_ACTION + ":" + ACTION_NAME_DESC),
                BotButton.callback(BUTTON_TOPICS, CALLBACK_EDIT_SECTION_ACTION + ":" + ACTION_TOPICS),
                BotButton.callback("📦 Заменить раздел (JSON)", CALLBACK_EDIT_SECTION_ACTION + ":" + ACTION_REPLACE_SECTION)
        ).addRow(BotButton.callback(BUTTON_BACK, CALLBACK_ADMIN_BACK_TO_SECTIONS));
        editMessage(userId, messageId, text, keyboard);
        sessionService.updateSessionState(userId, BotState.EDIT_SECTION_NAME_DESC);
    }

    public void handleEditSectionAction(Long userId, Integer messageId, String action, int pageSize) {
        if (ACTION_NAME_DESC.equals(action)) {
            editMessage(userId, messageId, MSG_SEND_JSON_SECTION_NAME_DESC, createCancelKeyboard());
            sessionService.updateSessionState(userId, BotState.EDIT_SECTION_NAME_DESC);
        } else if (ACTION_TOPICS.equals(action)) {
            UserContext context = sessionService.getCurrentContext(userId);
            Long sectionId = context.getEditingSectionId();
            var result = navigationService.getTopicsPage(sectionId, 0, pageSize);
            String text = MSG_SELECT_TOPIC;
            BotKeyboard keyboard = keyboardBuilder.buildTopicsKeyboardForAdminBot(
                    result, sectionId, CALLBACK_SELECT_TOPIC_FOR_EDIT, CALLBACK_ADMIN_BACK_TO_SECTIONS);
            editMessage(userId, messageId, text, keyboard);
            sessionService.updateSessionState(userId, BotState.EDIT_SECTION_CHOOSE_TOPIC);
        } else if (ACTION_REPLACE_SECTION.equals(action)) {
            promptEditSectionJson(userId, messageId);
        }
    }

    private void promptEditSectionJson(Long userId, Integer messageId) {
        editMessage(userId, messageId, MSG_SEND_JSON_SECTION, createAdminCancelKeyboardWithBackToSections());
        sessionService.updateSessionState(userId, BotState.AWAITING_SECTION_JSON);
    }

    private void processSectionJson(Long userId, byte[] fileContent) {
        Integer progressMessageId = sendProgressMessage(userId);
        try {
            InputStream inputStream = new ByteArrayInputStream(fileContent);
            SectionImportDto dto = objectMapper.readValue(inputStream, SectionImportDto.class);
            UserContext context = sessionService.getCurrentContext(userId);
            Long sectionId = context.getEditingSectionId();
            Section existingSection = sectionRepository.findById(sectionId)
                    .orElseThrow(() -> new RuntimeException("Section not found"));
            Section updatedSection = courseImportService.importSection(dto, existingSection);
            sendMessage(userId, "Раздел \"" + updatedSection.getTitle() + "\" успешно заменён.");
            Long courseId = updatedSection.getCourse().getId();
            context.setEditingCourseId(courseId);
            sessionService.updateSessionContext(userId, context);
            showEditCourseSectionsPage(userId, null, courseId, 0, ADMIN_DEFAULT_PAGE_SIZE);
            sessionService.updateSessionState(userId, BotState.EDIT_COURSE_SECTION_CHOOSE);
        } catch (InvalidJsonException e) {
            log.warn("Section JSON validation error: {}", e.getMessage());
            sendMessage(userId, e.getMessage(), createRetryOrCancelKeyboard());
        } catch (Exception e) {
            log.error("Error processing section JSON", e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        } finally {
            if (progressMessageId != null) deleteMessage(userId, progressMessageId);
        }
    }

    public void handleSelectTopicForEdit(Long userId, Integer messageId, Long topicId) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setEditingTopicId(topicId);
        Topic topic = topicRepository.findById(topicId).orElse(null);
        if (topic != null) {
            context.setEditingSectionId(topic.getSection().getId());
        }
        sessionService.updateSessionContext(userId, context);

        editMessage(userId, messageId, MSG_SEND_JSON_TOPIC, createAdminCancelKeyboardWithBackToTopics());
        sessionService.updateSessionState(userId, BotState.EDIT_TOPIC_JSON);
    }

    @Transactional
    public void handleConfirmDeleteCourse(Long userId, Integer messageId, Long courseId) {
        try {
            userStudyTimeRepository.deleteByCourseId(courseId);
            userProgressRepository.deleteByCourseId(courseId);
            courseRepository.deleteById(courseId);
            UserContext context = sessionService.getCurrentContext(userId);
            if (context.getCurrentCourseId() != null && context.getCurrentCourseId().equals(courseId)) {
                context.setCurrentCourseId(null);
                sessionService.updateSessionContext(userId, context);
            }
            editMessage(userId, messageId, MSG_COURSE_DELETED, createBackToMainKeyboard());
        } catch (Exception e) {
            log.error("Error deleting course", e);
            editMessage(userId, messageId, MSG_ERROR_DELETING_COURSE, createBackToMainKeyboard());
        }
        sessionService.updateSessionState(userId, BotState.MAIN_MENU);
    }

    // ================== Внутренние методы обработки JSON ==================

    private void processCourseJson(Long userId, byte[] fileContent) {
        Integer progressMessageId = sendProgressMessage(userId);
        try {
            InputStream inputStream = new ByteArrayInputStream(fileContent);
            Course course = courseImportService.importCourse(inputStream);
            List<PendingImage> pending = courseImportService.collectCourseImages(course.getId());
            if (!pending.isEmpty()) {
                UserContext context = sessionService.getCurrentContext(userId);
                context.setPendingImages(pending);
                context.setCurrentImageIndex(0);
                sessionService.updateSession(userId, BotState.AWAITING_IMAGE, context);
                requestNextImage(userId, null);
            } else {
                sendMessage(userId, "Успешно. Курс \"" + course.getTitle() + "\" добавлен. Изображения не требуются.", createBackToMainKeyboard());
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
            }
        } catch (InvalidJsonException e) {
            log.warn("JSON validation error: {}", e.getMessage());
            sendMessage(userId, e.getMessage(), createRetryOrCancelKeyboard());
        } catch (Exception e) {
            log.error("Error importing course from JSON", e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        } finally {
            if (progressMessageId != null) deleteMessage(userId, progressMessageId);
        }
    }

    private void processCourseNameDescJson(Long userId, byte[] fileContent) {
        try {
            InputStream inputStream = new ByteArrayInputStream(fileContent);
            CourseNameDescDto dto = objectMapper.readValue(inputStream, CourseNameDescDto.class);
            UserContext context = sessionService.getCurrentContext(userId);
            Long courseId = context.getEditingCourseId();
            if (courseId == null) {
                sendMessage(userId, MSG_COURSE_NOT_SELECTED, createBackToMainKeyboard());
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
                return;
            }
            Course oldCourse = courseRepository.findById(courseId).orElseThrow();
            String oldTitle = oldCourse.getTitle();
            String oldDesc = oldCourse.getDescription();
            Course updated = courseImportService.updateCourseNameDesc(courseId, dto.getTitle(), dto.getDescription());
            String response = String.format(FORMAT_COURSE_UPDATE,
                    oldTitle, updated.getTitle(), oldDesc, updated.getDescription());
            sendMessage(userId, response, createBackToMainKeyboard());
            sessionService.updateSessionState(userId, BotState.MAIN_MENU);
        } catch (Exception e) {
            log.error("Error processing course name/desc JSON", e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        }
    }

    private void processSectionNameDescJson(Long userId, byte[] fileContent) {
        try {
            InputStream inputStream = new ByteArrayInputStream(fileContent);
            SectionNameDescDto dto = objectMapper.readValue(inputStream, SectionNameDescDto.class);
            UserContext context = sessionService.getCurrentContext(userId);
            Long sectionId = context.getEditingSectionId();
            if (sectionId == null) {
                sendMessage(userId, MSG_SECTION_NOT_SELECTED, createBackToMainKeyboard());
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
                return;
            }
            Section oldSection = sectionRepository.findById(sectionId).orElseThrow();
            String oldTitle = oldSection.getTitle();
            String oldDesc = oldSection.getDescription();
            Section updated = courseImportService.updateSectionNameDesc(sectionId, dto.getTitle(), dto.getDescription());
            Long courseId = updated.getCourse().getId();
            String response = String.format(FORMAT_SECTION_UPDATE,
                    oldTitle, updated.getTitle(), oldDesc, updated.getDescription());
            sendMessage(userId, response);
            context.setEditingCourseId(courseId);
            sessionService.updateSessionContext(userId, context);
            var result = navigationService.getSectionsPage(courseId, 0, ADMIN_DEFAULT_PAGE_SIZE);
            String text = MSG_SELECT_SECTION;
            BotKeyboard keyboard = keyboardBuilder.buildSectionsKeyboardForAdminBot(
                    result, courseId, CALLBACK_SELECT_SECTION_FOR_EDIT, CALLBACK_EDIT_COURSE);
            sendMessage(userId, text, keyboard);
            sessionService.updateSessionState(userId, BotState.EDIT_COURSE_SECTION_CHOOSE);
        } catch (Exception e) {
            log.error("Error processing section name/desc JSON", e);
            sendMessage(userId, MSG_JSON_PARSE_ERROR, createRetryOrCancelKeyboard());
        }
    }

    private void processTopicJson(Long userId, byte[] fileContent) {
        Integer progressMessageId = sendProgressMessage(userId);
        try {
            InputStream inputStream = new ByteArrayInputStream(fileContent);
            TopicImportDto dto = objectMapper.readValue(inputStream, TopicImportDto.class);
            UserContext context = sessionService.getCurrentContext(userId);
            Long topicId = context.getEditingTopicId();
            Topic existingTopic = topicRepository.findById(topicId)
                    .orElseThrow(() -> new RuntimeException("Topic not found"));
            Topic updatedTopic = courseImportService.importTopic(dto, existingTopic);
            sendMessage(userId, MSG_TOPIC_UPDATED);
            startImageUploadSequence(userId, null, updatedTopic.getId());
        } catch (InvalidJsonException e) {
            log.warn("Topic JSON validation error: {}", e.getMessage());
            sendJsonErrorWithBackToTopics(userId, e.getMessage());
        } catch (Exception e) {
            log.error("Error processing topic JSON", e);
            sendJsonErrorWithBackToTopics(userId, MSG_JSON_PARSE_ERROR);
        } finally {
            if (progressMessageId != null) deleteMessage(userId, progressMessageId);
        }
    }

    // ================== Изображения ==================

    private void startImageUploadSequence(Long userId, Integer messageId, Long topicId) {
        List<BlockImage> blockImages = blockImageRepository.findPendingImagesByTopicId(topicId);
        List<QuestionImage> questionImages = questionImageRepository.findPendingImagesByTopicId(topicId);
        List<PendingImage> pending = new ArrayList<>();
        for (BlockImage bi : blockImages) {
            pending.add(new PendingImage(ENTITY_BLOCK, bi.getId(), bi.getDescription()));
        }
        for (QuestionImage qi : questionImages) {
            pending.add(new PendingImage(ENTITY_QUESTION, qi.getId(), qi.getDescription()));
        }
        if (pending.isEmpty()) {
            sendMessage(userId, MSG_TOPIC_UPDATED_NO_IMAGES);
            Long sectionId = sessionService.getCurrentContext(userId).getEditingSectionId();
            if (sectionId != null) {
                showEditTopicsPage(userId, null, sectionId, 0, ADMIN_DEFAULT_PAGE_SIZE);
            } else {
                sendMainMenu(userId, null);
            }
            return;
        }
        UserContext context = sessionService.getCurrentContext(userId);
        context.setPendingImages(pending);
        context.setCurrentImageIndex(0);
        sessionService.updateSession(userId, BotState.AWAITING_IMAGE, context);
        requestNextImage(userId, messageId);
    }

    private void requestNextImage(Long userId, Integer messageId) {
        UserContext context = sessionService.getCurrentContext(userId);
        List<PendingImage> pending = context.getPendingImages();
        int idx = context.getCurrentImageIndex();
        if (idx >= pending.size()) {
            sendMessage(userId, MSG_IMAGES_COMPLETE);
            Long sectionId = context.getEditingSectionId();
            if (sectionId != null) {
                showEditTopicsPage(userId, null, sectionId, 0, ADMIN_DEFAULT_PAGE_SIZE);
            } else {
                sendMainMenu(userId, null);
            }
            return;
        }
        PendingImage next = pending.get(idx);
        String text = String.format(MSG_IMAGE_REQUEST,
                idx + 1, pending.size(), next.getDescription());
        sendMessage(userId, text, createCancelKeyboard());
        context.setTargetEntityId(next.getEntityId());
        context.setTargetEntityType(next.getEntityType());
        sessionService.updateSession(userId, BotState.AWAITING_IMAGE, context);
    }

    // ================== Административные страницы ==================

    public void showEditCourseSectionsPage(Long userId, Integer messageId, Long courseId, int page, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setAdminSectionsPage(page);
        sessionService.updateSessionContext(userId, context);
        var result = navigationService.getSectionsPage(courseId, page, pageSize);
        String courseTitle = navigationService.getCourseTitle(courseId);
        String text = String.format(FORMAT_EDIT_SECTIONS_HEADER,
                courseTitle, page + 1, result.getTotalPages(), result.getTotalItems());
        BotKeyboard keyboard = keyboardBuilder.buildSectionsKeyboardForAdminBot(result, courseId,
                CALLBACK_SELECT_SECTION_FOR_EDIT, CALLBACK_EDIT_COURSE);
        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    public void showEditTopicsPage(Long userId, Integer messageId, Long sectionId, int page, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        context.setAdminTopicsPage(page);
        sessionService.updateSessionContext(userId, context);
        var result = navigationService.getTopicsPage(sectionId, page, pageSize);
        String sectionTitle = navigationService.getSectionTitle(sectionId);
        String text = String.format(FORMAT_EDIT_TOPICS_HEADER,
                sectionTitle, page + 1, result.getTotalPages(), result.getTotalItems());
        BotKeyboard keyboard = keyboardBuilder.buildTopicsKeyboardForAdminBot(result, sectionId,
                CALLBACK_SELECT_TOPIC_FOR_EDIT, CALLBACK_ADMIN_BACK_TO_SECTIONS);
        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    public void handleBackToCoursesFromEdit(Long userId, Integer messageId, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long editingCourseId = context.getEditingCourseId();
        if (editingCourseId != null) {
            showEditCoursesPage(userId, messageId, 0, pageSize);
        } else {
            sendMainMenu(userId, messageId);
        }
    }

    public void handleBackToSectionsFromEdit(Long userId, Integer messageId, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long courseId = context.getEditingCourseId();
        if (courseId != null) {
            Integer page = context.getAdminSectionsPage();
            if (page == null) page = 0;
            showEditCourseSectionsPage(userId, messageId, courseId, page, pageSize);
        } else {
            sendMainMenu(userId, messageId);
        }
    }

    public void handleBackToTopicsFromEdit(Long userId, Integer messageId, int pageSize) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long sectionId = context.getEditingSectionId();
        if (sectionId != null) {
            Integer page = context.getAdminTopicsPage();
            if (page == null) page = 0;
            showEditTopicsPage(userId, messageId, sectionId, page, pageSize);
        } else {
            sendMainMenu(userId, messageId);
        }
    }

    public void handleBackToTopicsFromEdit(Long userId, Integer messageId, Long sectionId, int page, int pageSize) {
        showEditTopicsPage(userId, messageId, sectionId, page, pageSize);
    }

    public boolean isAdmin(Long userId) {
        return adminUserRepository.existsByUserId(userId);
    }

    public void handleAdminCoursesPage(Long userId, Integer messageId, String source, int page, int pageSize) {
        showEditCoursesPage(userId, messageId, page, pageSize);
    }

    public void handleAdminSectionsPage(Long userId, Integer messageId, Long courseId, int page, int pageSize) {
        showEditCourseSectionsPage(userId, messageId, courseId, page, pageSize);
    }

    public void handleAdminTopicsPage(Long userId, Integer messageId, Long sectionId, int page, int pageSize) {
        showEditTopicsPage(userId, messageId, sectionId, page, pageSize);
    }

    // ================== Вспомогательные ==================

    private void sendJsonErrorWithBackToTopics(Long userId, String errorMessage) {
        UserContext context = sessionService.getCurrentContext(userId);
        Long topicId = context.getEditingTopicId();
        Long sectionId = null;
        if (topicId != null) {
            Topic topic = topicRepository.findById(topicId).orElse(null);
            if (topic != null) {
                sectionId = topic.getSection().getId();
            }
        }
        Integer page = context.getAdminTopicsPage();
        if (sectionId == null) {
            sendMessage(userId, errorMessage, createBackToMainKeyboard());
            return;
        }
        String backCallback = CALLBACK_ADMIN_BACK_TO_TOPICS + ":" + sectionId + ":" + page;
        BotKeyboard keyboard = new BotKeyboard().addRow(BotButton.callback(BUTTON_RETRY, CALLBACK_RETRY))
                .addRow(BotButton.callback(BUTTON_CANCEL, backCallback));
        sendMessage(userId, errorMessage, keyboard);
    }

    private BotKeyboard createAdminCancelKeyboardWithBackToSections() {
        return BotKeyboard.of(BotButton.callback(BUTTON_CANCEL, CALLBACK_ADMIN_BACK_TO_SECTIONS));
    }

    private boolean isZipFile(byte[] content) {
        return content.length > 4 &&
                content[0] == 0x50 && content[1] == 0x4B &&
                content[2] == 0x03 && content[3] == 0x04;
    }

    private void processCourseZip(Long userId, byte[] fileContent) {
        Integer progressMessageId = sendProgressMessage(userId);
        try {
            InputStream inputStream = new ByteArrayInputStream(fileContent);
            Course course = zipCourseImportService.importCourseFromZip(inputStream);
            List<PendingImage> pending = courseImportService.collectCourseImages(course.getId());
            if (!pending.isEmpty()) {
                UserContext context = sessionService.getCurrentContext(userId);
                context.setPendingImages(pending);
                context.setCurrentImageIndex(0);
                sessionService.updateSession(userId, BotState.AWAITING_IMAGE, context);
                requestNextImage(userId, null);
            } else {
                sendMessage(userId, "Успешно. Курс \"" + course.getTitle() + "\" добавлен. Изображения не требуются.", createBackToMainKeyboard());
                sessionService.updateSessionState(userId, BotState.MAIN_MENU);
            }
        } catch (InvalidJsonException e) {
            log.warn("JSON validation error: {}", e.getMessage());
            sendMessage(userId, e.getMessage(), createRetryOrCancelKeyboard());
        } catch (Exception e) {
            log.error("Error importing course from ZIP", e);
            sendMessage(userId, "Ошибка импорта курса из ZIP: " + e.getMessage(), createRetryOrCancelKeyboard());
        } finally {
            if (progressMessageId != null) deleteMessage(userId, progressMessageId);
        }
    }
}