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
import com.google.api.services.drive.model.Permission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

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

    private Drive driveService;

    @PostConstruct
    public void init() throws Exception {
        if (clientId == null || clientId.isBlank() ||
                clientSecret == null || clientSecret.isBlank() ||
                refreshToken == null || refreshToken.isBlank()) {
            log.warn("Google Drive OAuth2 credentials are not configured. Service will be disabled.");
            return;
        }

        // Создаём GoogleClientSecrets из client id/secret
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

        // Создаём credential с refresh token
        Credential credential = new Credential.Builder(flow.getMethod())
                .setTransport(flow.getTransport())
                .setJsonFactory(flow.getJsonFactory())
                .setTokenServerEncodedUrl(flow.getTokenServerEncodedUrl())
                .setClientAuthentication(flow.getClientAuthentication())
                .build();
        credential.setRefreshToken(refreshToken);
        // Принудительно обновляем access token (можно не вызывать, библиотека сама обновит при необходимости)
        credential.refreshToken();

        this.driveService = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
        log.info("Google Drive service initialized successfully with OAuth2 using refresh token.");
    }

    @Override
    public String upload(byte[] data, String fileName) throws Exception {
        if (driveService == null) {
            throw new IllegalStateException("Google Drive service is not initialized. Check your configuration.");
        }

        String uniqueId = UUID.randomUUID().toString();
        String baseName = fileName.replaceAll("\\.pdf$", "");
        String remoteFileName = uniqueId + "_" + baseName + ".pdf";

        log.info("Uploading PDF to Google Drive: {}", remoteFileName);

        Path tempFile = Files.createTempFile("gdrive_upload_", ".pdf");
        Files.write(tempFile, data);

        try {
            File fileMetadata = new File();
            fileMetadata.setName(remoteFileName);
            fileMetadata.setParents(Collections.singletonList("root"));

            FileContent mediaContent = new FileContent("application/pdf", tempFile.toFile());
            File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, webViewLink")
                    .execute();

            log.info("File uploaded successfully. File ID: {}", uploadedFile.getId());

            // Делаем файл публичным (доступ по ссылке)
            Permission permission = new Permission();
            permission.setType("anyone");
            permission.setRole("reader");
            driveService.permissions().create(uploadedFile.getId(), permission).execute();

            log.info("File permissions updated to 'Anyone with the link'.");

            String publicLink = uploadedFile.getWebViewLink();
            if (publicLink == null) {
                publicLink = "https://drive.google.com/file/d/" + uploadedFile.getId() + "/view";
            }
            log.info("Public link: {}", publicLink);

            scheduleDeletion(uploadedFile.getId());

            return publicLink;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private void scheduleDeletion(String fileId) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(15 * 60 * 1000); // 15 минут
                driveService.files().delete(fileId).execute();
                log.info("Deleted expired file from Google Drive. File ID: {}", fileId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Failed to delete file from Google Drive: {}", e.getMessage());
            }
        });
    }

    @Override
    public String getName() {
        return "Google Drive";
    }
}