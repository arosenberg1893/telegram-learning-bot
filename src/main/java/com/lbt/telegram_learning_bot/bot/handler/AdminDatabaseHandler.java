package com.lbt.telegram_learning_bot.bot.handler;

import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.dto.BackupInfo;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.platform.FileDownloader;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.repository.AdminUserRepository;
import com.lbt.telegram_learning_bot.service.*;
import com.lbt.telegram_learning_bot.util.BackupLogHelper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

import static com.lbt.telegram_learning_bot.util.Constants.*;

/**
 * Обрабатывает административные операции с базой данных:
 * резервное копирование, восстановление, режим обслуживания.
 *
 * <p>Выделен из {@link AdminHandler} по принципу единой ответственности.
 * Не имеет зависимостей от репозиториев курсов — работает только с
 * {@link BackupService} и {@link MaintenanceModeService}.</p>
 */
@Slf4j
public class AdminDatabaseHandler extends BaseHandler {

    private final BackupService backupService;
    private final MaintenanceModeService maintenanceModeService;
    private final FileDownloader fileDownloader;

    public AdminDatabaseHandler(MessageSender messageSender,
                                FileDownloader fileDownloader,
                                UserSessionService sessionService,
                                NavigationService navigationService,
                                AdminUserRepository adminUserRepository,
                                UserSettingsService userSettingsService,
                                BackupService backupService,
                                MaintenanceModeService maintenanceModeService) {
        super(messageSender, sessionService, navigationService,
                adminUserRepository, userSettingsService, maintenanceModeService);
        this.backupService = backupService;
        this.maintenanceModeService = maintenanceModeService;
        this.fileDownloader = fileDownloader;
    }

    // ================== Меню управления БД ==================

    public void showDatabaseMenu(Long userId, Integer messageId) {
        try {
            List<BackupInfo> backups = backupService.getLatestBackups();
            String backupInfo = formatBackupInfo(backups);
            String maintenanceStatus = maintenanceModeService.isMaintenance() ? "✅ Включён" : "❌ Выключен";

            String text = String.format("""
                💾 **Управление базой данных**
                
                📋 Последние резервные копии:
                %s
                
                🔧 Режим обслуживания: %s
                """, backupInfo, maintenanceStatus);

            String maintenanceButtonLabel = maintenanceModeService.isMaintenance()
                    ? "🔓 Выключить режим обслуживания"
                    : "🔒 Включить режим обслуживания";

            BotKeyboard keyboard = new BotKeyboard()
                    .addRow(BotButton.callback("📤 Создать резервную копию", CALLBACK_BACKUP_NOW))
                    .addRow(BotButton.callback("🔄 Восстановить из копии", CALLBACK_RESTORE))
                    .addRow(BotButton.callback("📁 Загрузить свой файл", CALLBACK_UPLOAD_BACKUP_FILE))
                    .addRow(BotButton.callback(maintenanceButtonLabel, CALLBACK_TOGGLE_MAINTENANCE))
                    .addRow(BotButton.callback(BUTTON_MAIN_MENU, CALLBACK_MAIN_MENU));

            if (messageId != null) {
                editMessage(userId, messageId, text, keyboard);
            } else {
                sendMessage(userId, text, keyboard);
            }
        } catch (Exception e) {
            log.error("Error showing database menu for user {}", userId, e);
            BotKeyboard errorKeyboard = new BotKeyboard()
                    .addRow(BotButton.callback("🔙 Назад", CALLBACK_ADMIN_DB));
            String errorText = "❌ Не удалось получить информацию о резервных копиях.";
            if (messageId != null) {
                editMessage(userId, messageId, errorText, errorKeyboard);
            } else {
                sendMessage(userId, errorText, errorKeyboard);
            }
        }
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
                sb.append(entry.getValue() ? "✅" : "❌").append(" ").append(entry.getKey()).append("\n");
            }

            editMessage(userId, messageId, sb.toString(),
                    new BotKeyboard().addRow(BotButton.callback("🔙 Назад в Adm БД", CALLBACK_ADMIN_DB)));
        } catch (Exception e) {
            log.error("Manual backup failed for user {}", userId, e);
            BackupLogHelper.logManualBackupError(userId, e.getMessage());
            String errorMsg = e.getMessage()
                    .replace("_", "\\_").replace("*", "\\*")
                    .replace("[", "\\[").replace("]", "\\]");
            sendMessage(userId, "❌ Ошибка при создании резервной копии: " + errorMsg,
                    createBackToMainKeyboard());
        } finally {
            if (progressMsgId != null) deleteMessage(userId, progressMsgId);
        }
    }

    public void showRestoreMenu(Long userId, Integer messageId) {
        try {
            List<BackupInfo> backups = backupService.getLatestBackups();
            if (backups.isEmpty()) {
                String text = "❌ Нет сохранённых резервных копий для восстановления.";
                BotKeyboard keyboard = new BotKeyboard()
                        .addRow(BotButton.callback("🔙 Назад", CALLBACK_ADMIN_DB));
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
                keyboard.addRow(BotButton.callback(buttonText,
                        CALLBACK_RESTORE_SELECT + ":" + backup.getFileName()));
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
            sendMessage(userId, "❌ Ошибка при восстановлении базы данных: " + e.getMessage(),
                    createBackToMainKeyboard());
        } finally {
            if (progressMsgId != null) deleteMessage(userId, progressMsgId);
        }
    }

    public void promptBackupFileUpload(Long userId, Integer messageId) {
        editMessage(userId, messageId,
                "📁 Отправьте файл резервной копии (дамп БД в формате .dump) для восстановления.\n\n"
                        + "ВНИМАНИЕ: текущая база данных будет заменена. "
                        + "Перед восстановлением будет автоматически создана резервная копия.",
                createCancelKeyboard());
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
            sendMessage(userId, "❌ Ошибка при восстановлении из загруженного файла: " + e.getMessage(),
                    createBackToMainKeyboard());
        } finally {
            if (progressMsgId != null) deleteMessage(userId, progressMsgId);
        }
        sessionService.updateSessionState(userId, BotState.MAIN_MENU);
    }

    // ================== Режим обслуживания ==================

    public void toggleMaintenanceMode(Long userId, Integer messageId) {
        if (maintenanceModeService.isMaintenance()) {
            maintenanceModeService.disableMaintenance();
            BackupLogHelper.logMaintenanceDisabled(userId);
        } else {
            maintenanceModeService.enableMaintenance();
            BackupLogHelper.logMaintenanceEnabled(userId);
        }
        showDatabaseMenu(userId, messageId);
    }

    // ================== Вспомогательные ==================

    private String formatBackupInfo(List<BackupInfo> backups) {
        if (backups.isEmpty()) return "Нет сохранённых резервных копий.";
        StringBuilder sb = new StringBuilder();
        for (BackupInfo backup : backups) {
            sb.append("• ").append(backup.getFormattedTimestamp())
                    .append(" (").append(backup.getFormattedSize()).append(")\n");
        }
        return sb.toString();
    }
}
