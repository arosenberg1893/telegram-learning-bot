package com.lbt.telegram_learning_bot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Вспомогательный класс для логирования операций резервного копирования и восстановления базы данных.
 * <p>
 * Использует отдельный логгер с именем "BACKUP", который в конфигурации logback-spring.xml
 * направляется в отдельный файл с ротацией (по умолчанию logs/backup.log).
 * </p>
 * <p>
 * Все методы статические, чтобы упростить вызов из любого места приложения.
 * </p>
 */
public final class BackupLogHelper {

    private static final Logger BACKUP_LOGGER = LoggerFactory.getLogger("BACKUP");

    private BackupLogHelper() {
        // Запрещаем создание экземпляров
    }

    // ================== Автоматическое резервное копирование ==================

    /**
     * Логирует начало автоматического (планового) резервного копирования.
     */
    public static void logBackupStarted() {
        BACKUP_LOGGER.info("Scheduled backup started");
    }

    /**
     * Логирует успешное завершение автоматического резервного копирования.
     *
     * @param fileName имя созданного файла резервной копии
     */
    public static void logBackupSuccess(String fileName) {
        BACKUP_LOGGER.info("Scheduled backup completed successfully: {}", fileName);
    }

    /**
     * Логирует ошибку при автоматическом резервном копировании.
     *
     * @param error сообщение об ошибке
     */
    public static void logBackupError(String error) {
        BACKUP_LOGGER.error("Scheduled backup failed: {}", error);
    }

    // ================== Ручное резервное копирование (по команде администратора) ==================

    /**
     * Логирует начало ручного резервного копирования, инициированного администратором.
     *
     * @param adminUserId идентификатор администратора в Telegram/VK
     */
    public static void logManualBackupStarted(Long adminUserId) {
        BACKUP_LOGGER.info("Manual backup initiated by admin {}", adminUserId);
    }

    /**
     * Логирует успешное завершение ручного резервного копирования.
     *
     * @param adminUserId идентификатор администратора
     * @param fileName    имя созданного файла
     */
    public static void logManualBackupSuccess(Long adminUserId, String fileName) {
        BACKUP_LOGGER.info("Manual backup by admin {} completed: {}", adminUserId, fileName);
    }

    /**
     * Логирует ошибку при ручном резервном копировании.
     *
     * @param adminUserId идентификатор администратора
     * @param error       сообщение об ошибке
     */
    public static void logManualBackupError(Long adminUserId, String error) {
        BACKUP_LOGGER.error("Manual backup by admin {} failed: {}", adminUserId, error);
    }

    // ================== Восстановление из резервной копии ==================

    /**
     * Логирует начало восстановления базы данных из резервной копии.
     *
     * @param adminUserId идентификатор администратора
     * @param fileName    имя файла, из которого выполняется восстановление
     */
    public static void logRestoreStarted(Long adminUserId, String fileName) {
        BACKUP_LOGGER.info("Database restore initiated by admin {} from file {}", adminUserId, fileName);
    }

    /**
     * Логирует успешное восстановление базы данных из резервной копии.
     *
     * @param adminUserId идентификатор администратора
     * @param fileName    имя файла
     */
    public static void logRestoreSuccess(Long adminUserId, String fileName) {
        BACKUP_LOGGER.info("Database restore by admin {} from {} completed successfully", adminUserId, fileName);
    }

    /**
     * Логирует ошибку при восстановлении базы данных из резервной копии.
     *
     * @param adminUserId идентификатор администратора
     * @param fileName    имя файла
     * @param error       сообщение об ошибке
     */
    public static void logRestoreError(Long adminUserId, String fileName, String error) {
        BACKUP_LOGGER.error("Database restore by admin {} from {} failed: {}", adminUserId, fileName, error);
    }

    // ================== Режим обслуживания ==================

    /**
     * Логирует включение режима обслуживания.
     *
     * @param adminUserId идентификатор администратора, выполнившего операцию
     */
    public static void logMaintenanceEnabled(Long adminUserId) {
        BACKUP_LOGGER.info("Maintenance mode enabled by admin {}", adminUserId);
    }

    /**
     * Логирует выключение режима обслуживания.
     *
     * @param adminUserId идентификатор администратора, выполнившего операцию
     */
    public static void logMaintenanceDisabled(Long adminUserId) {
        BACKUP_LOGGER.info("Maintenance mode disabled by admin {}", adminUserId);
    }
}