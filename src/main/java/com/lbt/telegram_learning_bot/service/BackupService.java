package com.lbt.telegram_learning_bot.service;

import com.lbt.telegram_learning_bot.dto.BackupInfo;
import com.lbt.telegram_learning_bot.service.cloud.CloudStorageFacade;
import com.lbt.telegram_learning_bot.service.cloud.CloudStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Сервис для работы с резервными копиями базы данных.
 * Использует pg_dump и pg_restore через ProcessBuilder.
 * Хранит последние 7 копий в облачных хранилищах.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final CloudStorageFacade cloudStorageFacade;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${db.superuser.username:}")
    private String superUser;

    @Value("${db.superuser.password:}")
    private String superPassword;

    private static final int MAX_BACKUPS_TO_KEEP = 7;
    private static final String BACKUP_PREFIX = "backup_";
    private static final String BACKUP_SUFFIX = ".dump";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final Pattern BACKUP_FILE_PATTERN = Pattern.compile("backup(?:_pre_restore)?_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})\\.dump");

    /**
     * Извлекает хост и порт из JDBC URL.
     * Пример: jdbc:postgresql://host:5432/dbname -> host, 5432
     */
    private String[] extractHostAndPort(String jdbcUrl) {
        Pattern pattern = Pattern.compile("jdbc:postgresql://([^:/]+)(?::(\\d+))?/");
        Matcher matcher = pattern.matcher(jdbcUrl);
        if (matcher.find()) {
            String host = matcher.group(1);
            String port = matcher.group(2);
            if (port == null) port = "5432";
            return new String[]{host, port};
        }
        return new String[]{"localhost", "5432"};
    }

    public byte[] createDump() throws IOException, InterruptedException {
        String dbName = extractDatabaseName(dbUrl);
        String[] hostPort = extractHostAndPort(dbUrl);
        String host = hostPort[0];
        String port = hostPort[1];
        log.info("Starting pg_dump for database: {} at {}:{}", dbName, host, port);

        ProcessBuilder pb = new ProcessBuilder(
                "pg_dump", "-h", host, "-p", port, "-U", dbUser, "-d", dbName, "-Fc", "-O", "-x"
        );
        pb.environment().put("PGPASSWORD", dbPassword);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (InputStream is = process.getInputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String errorOutput = baos.toString();
            log.error("pg_dump failed with exit code {}: {}", exitCode, errorOutput);
            throw new IOException("pg_dump failed with exit code " + exitCode + ": " + errorOutput);
        }

        log.info("pg_dump completed successfully, size: {} bytes", baos.size());
        return baos.toByteArray();
    }

    public void restoreFromDump(byte[] dumpData) throws IOException, InterruptedException {
        log.info("Starting database restore from dump");

        // 1. Создаём резервную копию перед восстановлением
        byte[] preRestoreBackup = createDump();
        String timestamp = LocalDateTime.now().format(DATE_FORMAT);
        String preRestoreFileName = BACKUP_PREFIX + "pre_restore_" + timestamp + BACKUP_SUFFIX;
        try {
            Map<String, Boolean> uploadResults = cloudStorageFacade.uploadToAll(preRestoreBackup, preRestoreFileName);
            boolean anySuccess = uploadResults.values().stream().anyMatch(success -> success);
            if (anySuccess) {
                log.info("Pre-restore backup uploaded to at least one cloud: {}", preRestoreFileName);
                for (Map.Entry<String, Boolean> entry : uploadResults.entrySet()) {
                    log.info("  {} upload: {}", entry.getKey(), entry.getValue() ? "SUCCESS" : "FAILED");
                }
            } else {
                log.error("Pre-restore backup failed to upload to ANY cloud storage");
            }
        } catch (Exception e) {
            log.error("Failed to upload pre-restore backup", e);
        }

        // 2. Восстанавливаем, используя специального пользователя (если задан) или обычного
        String effectiveUser = (superUser != null && !superUser.isBlank()) ? superUser : dbUser;
        String effectivePassword = (superUser != null && !superUser.isBlank()) ? superPassword : dbPassword;
        String dbName = extractDatabaseName(dbUrl);
        String[] hostPort = extractHostAndPort(dbUrl);
        String host = hostPort[0];
        String port = hostPort[1];

        ProcessBuilder pb = new ProcessBuilder(
                "pg_restore", "-h", host, "-p", port, "-U", effectiveUser, "-d", dbName,
                "-c", "-O", "-x", "--if-exists"
        );
        pb.environment().put("PGPASSWORD", effectivePassword);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try (OutputStream os = process.getOutputStream()) {
            os.write(dumpData);
            os.flush();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            try (InputStream is = process.getInputStream()) {
                ByteArrayOutputStream errorBaos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    errorBaos.write(buffer, 0, len);
                }
                String errorOutput = errorBaos.toString();
                log.error("pg_restore failed with exit code {}: {}", exitCode, errorOutput);
                throw new IOException("pg_restore failed with exit code " + exitCode + ": " + errorOutput);
            }
        }

        log.info("Database restored successfully");
    }

    /**
     * Создаёт и загружает новую резервную копию в облачные хранилища,
     * затем выполняет ротацию (оставляет только MAX_BACKUPS_TO_KEEP последних копий).
     *
     * @return имя созданного файла
     */
    public BackupResult createAndUploadBackup() throws Exception {
        String timestamp = LocalDateTime.now().format(DATE_FORMAT);
        String fileName = BACKUP_PREFIX + timestamp + BACKUP_SUFFIX;
        byte[] dumpData = createDump();
        Map<String, Boolean> uploadResults = cloudStorageFacade.uploadToAll(dumpData, fileName);
        rotateBackups();
        return new BackupResult(fileName, uploadResults);
    }

    public record BackupResult(String fileName, Map<String, Boolean> uploadResults) {
    }

    /**
     * Удаляет старые резервные копии, оставляя только MAX_BACKUPS_TO_KEEP последних.
     * Работает с облачными хранилищами через CloudStorageFacade.
     */
    private void rotateBackups() {
        try {
            List<String> allBackups = cloudStorageFacade.listBackups(BACKUP_PREFIX);
            if (allBackups.size() <= MAX_BACKUPS_TO_KEEP) {
                log.debug("No need to rotate: {} backups, limit {}", allBackups.size(), MAX_BACKUPS_TO_KEEP);
                return;
            }

            // Сортируем от новых к старым по дате в имени файла
            List<String> sorted = allBackups.stream()
                    .sorted(Comparator.comparing(this::extractBackupTimestamp, Comparator.reverseOrder()))
                    .toList();

            List<String> toDelete = sorted.subList(MAX_BACKUPS_TO_KEEP, sorted.size());
            for (String fileName : toDelete) {
                log.info("Deleting old backup: {}", fileName);
                cloudStorageFacade.deleteFile(fileName);
            }
        } catch (Exception e) {
            log.error("Failed to rotate backups", e);
        }
    }

    /**
     * Возвращает список последних резервных копий (до MAX_BACKUPS_TO_KEEP) с информацией.
     *
     * @return список объектов BackupInfo (имя, дата, размер)
     */
    public List<BackupInfo> getLatestBackups() {
        try {
            List<CloudStorageService.BackupFileInfo> files = cloudStorageFacade.listBackupFiles(BACKUP_PREFIX);
            return files.stream()
                    .map(info -> {
                        LocalDateTime ts = extractBackupTimestamp(info.fileName());
                        return new BackupInfo(info.fileName(), ts, info.sizeBytes());
                    })
                    .filter(info -> info.getTimestamp() != null) // отфильтровываем, если дату не удалось распарсить
                    .sorted(Comparator.comparing(BackupInfo::getTimestamp, Comparator.reverseOrder()))
                    .limit(MAX_BACKUPS_TO_KEEP)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to list backups", e);
            return new ArrayList<>();
        }
    }

    /**
     * Получает байты резервной копии по имени файла из облачного хранилища.
     */
    public byte[] downloadBackup(String fileName) throws Exception {
        return cloudStorageFacade.downloadFile(fileName);
    }

    /**
     * Загружает собственный файл дампа от администратора и восстанавливает его.
     */
    public void restoreFromUserUpload(byte[] fileData) throws Exception {
        restoreFromDump(fileData);
    }

    // ================== Вспомогательные методы ==================

    private String extractDatabaseName(String jdbcUrl) {
        // Пример: jdbc:postgresql://localhost:5432/dbname
        int lastSlash = jdbcUrl.lastIndexOf('/');
        if (lastSlash == -1) {
            throw new IllegalArgumentException("Invalid JDBC URL: " + jdbcUrl);
        }
        String dbPart = jdbcUrl.substring(lastSlash + 1);
        int paramStart = dbPart.indexOf('?');
        if (paramStart != -1) {
            dbPart = dbPart.substring(0, paramStart);
        }
        return dbPart;
    }

    private LocalDateTime extractBackupTimestamp(String fileName) {
        Matcher m = BACKUP_FILE_PATTERN.matcher(fileName);
        if (m.matches()) {
            try {
                return LocalDateTime.parse(m.group(1), DATE_FORMAT);
            } catch (Exception e) {
                log.warn("Failed to parse timestamp from backup filename: {}", fileName, e);
            }
        }
        return LocalDateTime.MIN;
    }
}