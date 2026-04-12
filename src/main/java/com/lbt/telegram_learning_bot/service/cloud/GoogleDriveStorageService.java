package com.lbt.telegram_learning_bot.service.cloud;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.Permission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Order(2)
public class GoogleDriveStorageService implements CloudStorageService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String APPLICATION_NAME = "LBT Telegram Learning Bot";

    @Value("${google.drive.client.id:}")
    private String clientId;

    @Value("${google.drive.client.secret:}")
    private String clientSecret;

    @Value("${google.drive.refresh.token:}")
    private String refreshToken;

    @Value("${cloud.storage.folder:lbt_bot_storage}")
    private String rootFolderName;

    private Drive driveService;
    private String rootFolderId; // ID папки lbt_bot_storage

    @PostConstruct
    public void init() {
        try {
            if (clientId == null || clientId.isBlank() ||
                    clientSecret == null || clientSecret.isBlank() ||
                    refreshToken == null || refreshToken.isBlank()) {
                log.warn("Google Drive OAuth2 credentials are not configured. Service will be disabled.");
                this.driveService = null;
                return;
            }

            GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
            details.setClientId(clientId);
            details.setClientSecret(clientSecret);
            GoogleClientSecrets secrets = new GoogleClientSecrets();
            secrets.setInstalled(details);

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JSON_FACTORY,
                    secrets,
                    Collections.singleton(DriveScopes.DRIVE_FILE))
                    .setAccessType("offline")
                    .build();

            Credential credential = new Credential.Builder(flow.getMethod())
                    .setTransport(flow.getTransport())
                    .setJsonFactory(flow.getJsonFactory())
                    .setTokenServerEncodedUrl(flow.getTokenServerEncodedUrl())
                    .setClientAuthentication(flow.getClientAuthentication())
                    .build();
            credential.setRefreshToken(refreshToken);
            credential.refreshToken();

            this.driveService = new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JSON_FACTORY,
                    credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            // Создаём или получаем корневую папку lbt_bot_storage
            rootFolderId = getOrCreateFolder(rootFolderName, "root");

            log.info("Google Drive service initialized successfully. Root folder ID: {}", rootFolderId);
        } catch (Exception e) {
            log.error("Failed to initialize Google Drive service: {}. Service will be disabled.", e.getMessage(), e);
            this.driveService = null;
            // Не выбрасываем исключение, чтобы приложение запустилось без Google Drive
        }
    }

    // ================== Основные методы интерфейса ==================

    @Override
    public String upload(byte[] data, String fileName) throws Exception {
        if (driveService == null) {
            throw new IllegalStateException("Google Drive service is not initialized. Check your configuration.");
        }

        boolean isBackup = fileName.startsWith("backup_");
        String folderName = isBackup ? "bd_dumps" : "temp_pdf";

        // Получаем ID папки назначения внутри lbt_bot_storage
        String targetFolderId = getOrCreateFolder(folderName, rootFolderId);

        // Генерируем уникальное имя для временных PDF, для бэкапов оставляем оригинальное
        String remoteFileName;
        if (isBackup) {
            remoteFileName = fileName;
        } else {
            // Для PDF добавляем уникальный префикс, чтобы не было коллизий
            String uniqueId = UUID.randomUUID().toString();
            String baseName = fileName.replaceAll("\\.pdf$", "");
            remoteFileName = uniqueId + "_" + baseName + ".pdf";
        }

        log.info("Uploading file to Google Drive: folder='{}', fileName='{}'", folderName, remoteFileName);

        // Создаём метаданные файла
        File fileMetadata = new File();
        fileMetadata.setName(remoteFileName);
        fileMetadata.setParents(Collections.singletonList(targetFolderId));

        // Загружаем содержимое
        Path tempFile = Files.createTempFile("gdrive_upload_", ".tmp");
        Files.write(tempFile, data);
        try {
            FileContent mediaContent = new FileContent("application/octet-stream", tempFile.toFile());
            File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, webViewLink")
                    .execute();

            log.info("File uploaded successfully. File ID: {}", uploadedFile.getId());

            // Делаем файл общедоступным
            Permission permission = new Permission();
            permission.setType("anyone");
            permission.setRole("reader");
            driveService.permissions().create(uploadedFile.getId(), permission).execute();

            String publicLink = uploadedFile.getWebViewLink();
            if (publicLink == null) {
                publicLink = "https://drive.google.com/file/d/" + uploadedFile.getId() + "/view";
            }
            log.info("Public link: {}", publicLink);

            // Для временных PDF планируем удаление через 15 минут
            if (!isBackup) {
                scheduleDeletion(uploadedFile.getId());
            }

            return publicLink;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Override
    public String getName() {
        return "Google Drive";
    }

    @Override
    public List<String> listBackups(String prefix) throws Exception {
        if (driveService == null) {
            log.warn("Google Drive service not initialized, cannot list backups");
            return Collections.emptyList();
        }

        String bdDumpsFolderId = getOrCreateFolder("bd_dumps", rootFolderId);
        String query = String.format("name contains '%s' and trashed = false and '%s' in parents", prefix, bdDumpsFolderId);
        FileList result = driveService.files().list()
                .setQ(query)
                .setFields("files(id, name, createdTime)")
                .setOrderBy("createdTime desc")
                .execute();

        if (result.getFiles() == null || result.getFiles().isEmpty()) {
            return Collections.emptyList();
        }
        return result.getFiles().stream()
                .map(File::getName)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteFile(String fileName) throws Exception {
        if (driveService == null) {
            log.warn("Google Drive service not initialized, cannot delete file: {}", fileName);
            return;
        }

        String fileId = findFileIdByName(fileName);
        if (fileId == null) {
            log.warn("File not found for deletion: {}", fileName);
            return;
        }
        driveService.files().delete(fileId).execute();
        log.info("Deleted file from Google Drive: {} (ID: {})", fileName, fileId);
    }

    @Override
    public byte[] downloadFile(String fileName) throws Exception {
        if (driveService == null) {
            throw new IllegalStateException("Google Drive service not initialized");
        }

        String fileId = findFileIdByName(fileName);
        if (fileId == null) {
            throw new IllegalArgumentException("File not found: " + fileName);
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             InputStream is = driveService.files().get(fileId).executeMediaAsInputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    // ================== Вспомогательные методы ==================

    /**
     * Получает ID папки по имени внутри родительской папки.
     * Если папки нет, создаёт её.
     */
    private String getOrCreateFolder(String folderName, String parentId) throws IOException {
        String query = String.format("name = '%s' and mimeType = 'application/vnd.google-apps.folder' and '%s' in parents and trashed = false",
                folderName, parentId);
        FileList result = driveService.files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .setPageSize(1)
                .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }

        // Создаём папку
        File folderMetadata = new File();
        folderMetadata.setName(folderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        folderMetadata.setParents(Collections.singletonList(parentId));
        File folder = driveService.files().create(folderMetadata).setFields("id").execute();
        log.info("Created folder '{}' with ID: {}", folderName, folder.getId());
        return folder.getId();
    }

    /**
     * Находит ID файла по его имени в соответствующей папке (bd_dumps или temp_pdf).
     */
    private String findFileIdByName(String fileName) throws IOException {
        boolean isBackup = fileName.startsWith("backup_");
        String folderName = isBackup ? "bd_dumps" : "temp_pdf";
        String folderId = getOrCreateFolder(folderName, rootFolderId);

        String query = String.format("name = '%s' and trashed = false and '%s' in parents", fileName, folderId);
        FileList result = driveService.files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .setPageSize(1)
                .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }
        return null;
    }

    /**
     * Планирует удаление файла через 15 минут.
     */
    private void scheduleDeletion(String fileId) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(15 * 60 * 1000); // 15 минут
                driveService.files().delete(fileId).execute();
                log.info("Deleted expired temporary file from Google Drive. File ID: {}", fileId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Failed to delete expired file from Google Drive: {}", e.getMessage());
            }
        });
    }

    @Override
    public List<BackupFileInfo> listBackupFiles(String prefix) throws Exception {
        if (driveService == null) {
            log.warn("Google Drive service not initialized, cannot list backup files");
            return Collections.emptyList();
        }

        String bdDumpsFolderId = getOrCreateFolder("bd_dumps", rootFolderId);
        String query = String.format("name contains '%s' and trashed = false and '%s' in parents", prefix, bdDumpsFolderId);
        FileList result = driveService.files().list()
                .setQ(query)
                .setFields("files(id, name, size, createdTime)")
                .setOrderBy("createdTime desc")
                .execute();

        if (result.getFiles() == null || result.getFiles().isEmpty()) {
            return Collections.emptyList();
        }

        List<BackupFileInfo> list = new ArrayList<>();
        for (File file : result.getFiles()) {
            long size = file.getSize() == null ? 0 : file.getSize();
            list.add(new BackupFileInfo(file.getName(), size));
        }
        return list;
    }
}