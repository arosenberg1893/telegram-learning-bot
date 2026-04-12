package com.lbt.telegram_learning_bot.service.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@Order(1)
public class YandexDiskStorageService implements CloudStorageService {

    private static final String API_BASE_URL = "https://cloud-api.yandex.net/v1/disk";
    private static final long EXPIRY_MINUTES_PDF = 15;
    private static final int MAX_BACKUPS_TO_LIST = 100;

    @Value("${yandex.disk.token:}")
    private String yandexToken;

    @Value("${cloud.storage.folder:}")
    private String rootFolderName;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, Instant> expiryMap = new ConcurrentHashMap<>();

    @Override
    public String upload(byte[] data, String fileName) throws Exception {
        if (yandexToken == null || yandexToken.isBlank()) {
            throw new IllegalStateException("Yandex.Disk token is not configured");
        }

        boolean isBackup = fileName.startsWith("backup_");
        String targetFolder = isBackup ? "bd_dumps" : "temp_pdf";
        // Создаём полный путь lbt_bot_storage/targetFolder
        String fullFolderPath = "/" + rootFolderName + "/" + targetFolder;
        ensureFolderPath(targetFolder); // эта уже создаёт lbt_bot_storage и внутри targetFolder

        String remotePath = fullFolderPath + "/" + fileName;
        log.info("Uploading file to Yandex.Disk: {}", remotePath);

        String uploadUrl = getUploadUrl(remotePath);
        if (uploadUrl == null) {
            throw new RuntimeException("Failed to get upload URL from Yandex.Disk");
        }

        uploadFile(uploadUrl, data);

        String publicLink = getPublicLink(remotePath);
        if (publicLink == null) {
            throw new RuntimeException("Failed to get public link from Yandex.Disk");
        }

        if (!isBackup) {
            expiryMap.put(UUID.randomUUID().toString(), Instant.now().plusSeconds(EXPIRY_MINUTES_PDF * 60));
            scheduleDeletion(remotePath);
        }

        return publicLink;
    }

    @Override
    public String getName() {
        return "Yandex.Disk";
    }

    @Override
    public List<String> listBackups(String prefix) throws Exception {
        if (yandexToken == null || yandexToken.isBlank()) {
            log.warn("Yandex.Disk token not configured, cannot list backups");
            return Collections.emptyList();
        }

        ensureFolderPath("bd_dumps");

        String folderPath = "/" + rootFolderName + "/bd_dumps";
        String encodedPath = URLEncoder.encode(folderPath, StandardCharsets.UTF_8);
        String urlString = API_BASE_URL + "/resources?path=" + encodedPath + "&limit=" + MAX_BACKUPS_TO_LIST;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "OAuth " + yandexToken);

        int responseCode = conn.getResponseCode();
        if (responseCode == 404) {
            log.debug("Backup folder not found on Yandex.Disk (no backups yet)");
            return Collections.emptyList();
        } else if (responseCode != 200) {
            log.error("Failed to list backups, response code: {}", responseCode);
            return Collections.emptyList();
        }

        try (InputStream is = conn.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            JsonNode items = root.path("_embedded").path("items");
            List<String> backups = new ArrayList<>();
            for (JsonNode item : items) {
                String name = item.path("name").asText();
                if (name.startsWith(prefix)) {
                    backups.add(name);
                }
            }
            backups.sort((a, b) -> b.compareTo(a));
            return backups;
        }
    }

    @Override
    public void deleteFile(String fileName) throws Exception {
        if (yandexToken == null || yandexToken.isBlank()) {
            log.warn("Yandex.Disk token not configured, cannot delete file: {}", fileName);
            return;
        }

        boolean isBackup = fileName.startsWith("backup_");
        String targetFolder = isBackup ? "bd_dumps" : "temp_pdf";
        String remotePath = "/" + rootFolderName + "/" + targetFolder + "/" + fileName;

        String encodedPath = URLEncoder.encode(remotePath, StandardCharsets.UTF_8);
        String urlString = API_BASE_URL + "/resources?path=" + encodedPath + "&permanently=true";

        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "OAuth " + yandexToken);

        int responseCode = conn.getResponseCode();
        if (responseCode != 204 && responseCode != 202) {
            log.error("Failed to delete file {}, response code: {}", fileName, responseCode);
            throw new RuntimeException("Delete failed with code: " + responseCode);
        }
        log.info("Deleted file from Yandex.Disk: {}", remotePath);
    }

    @Override
    public byte[] downloadFile(String fileName) throws Exception {
        if (yandexToken == null || yandexToken.isBlank()) {
            throw new IllegalStateException("Yandex.Disk token not configured");
        }

        boolean isBackup = fileName.startsWith("backup_");
        String targetFolder = isBackup ? "bd_dumps" : "temp_pdf";
        String remotePath = "/" + rootFolderName + "/" + targetFolder + "/" + fileName;

        String downloadUrl = getDownloadUrl(remotePath);
        if (downloadUrl == null) {
            throw new RuntimeException("Failed to get download URL for: " + fileName);
        }

        return downloadFromUrl(downloadUrl);
    }

    // ================== Работа с папками ==================

    private void ensureFolderPath(String folderPath) throws Exception {
        // Сначала создаём lbt_bot_storage в корне
        createFolder(rootFolderName, null);
        // Затем внутри lbt_bot_storage создаём folderPath (bd_dumps или temp_pdf)
        createFolder(folderPath, rootFolderName);
    }

    private void createFolder(String folderName, String parentPath) throws Exception {
        String fullPath = (parentPath == null) ? "/" + folderName : "/" + parentPath + "/" + folderName;
        String encodedPath = URLEncoder.encode(fullPath, StandardCharsets.UTF_8);
        String urlString = API_BASE_URL + "/resources?path=" + encodedPath;

        // Проверяем, существует ли уже
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "OAuth " + yandexToken);
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            return; // папка уже есть
        }

        if (responseCode != 404) {
            log.warn("Unexpected response {} while checking folder {}", responseCode, fullPath);
        }

        // Создаём папку
        conn = (HttpURLConnection) new URL(API_BASE_URL + "/resources?path=" + encodedPath).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", "OAuth " + yandexToken);
        responseCode = conn.getResponseCode();
        if (responseCode == 201 || responseCode == 409) {
            log.info("Created folder {} (code {})", fullPath, responseCode);
        } else {
            log.warn("Failed to create folder {}, response code: {}", fullPath, responseCode);
            throw new RuntimeException("Failed to create folder: " + fullPath);
        }
    }

    // ================== Вспомогательные методы загрузки/скачивания ==================

    private String getUploadUrl(String remotePath) throws Exception {
        String encodedPath = URLEncoder.encode(remotePath, StandardCharsets.UTF_8);
        String urlString = API_BASE_URL + "/resources/upload?path=" + encodedPath + "&overwrite=true";

        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "OAuth " + yandexToken);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            log.error("Failed to get upload URL, response code: {}", responseCode);
            return null;
        }

        try (InputStream is = conn.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            return root.path("href").asText(null);
        }
    }

    private void uploadFile(String uploadUrl, byte[] fileData) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(uploadUrl).openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/octet-stream");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(fileData);
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 201) {
            log.error("Failed to upload file, response code: {}", responseCode);
            throw new RuntimeException("File upload failed with code: " + responseCode);
        }
    }

    private String getPublicLink(String remotePath) throws Exception {
        String encodedPath = URLEncoder.encode(remotePath, StandardCharsets.UTF_8);
        String urlString = API_BASE_URL + "/resources/download?path=" + encodedPath;

        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "OAuth " + yandexToken);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            log.error("Failed to get public link, response code: {}", responseCode);
            return null;
        }

        try (InputStream is = conn.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            return root.path("href").asText(null);
        }
    }

    private String getDownloadUrl(String remotePath) throws Exception {
        String encodedPath = URLEncoder.encode(remotePath, StandardCharsets.UTF_8);
        String urlString = API_BASE_URL + "/resources/download?path=" + encodedPath;

        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "OAuth " + yandexToken);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            log.error("Failed to get download URL, response code: {}", responseCode);
            return null;
        }

        try (InputStream is = conn.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            return root.path("href").asText(null);
        }
    }

    private byte[] downloadFromUrl(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        }
    }

    private void scheduleDeletion(String remotePath) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(EXPIRY_MINUTES_PDF * 60 * 1000);
                deleteFileByPath(remotePath);
                log.info("Deleted expired file from Yandex.Disk: {}", remotePath);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Failed to delete expired file: {}", remotePath, e);
            }
        });
    }

    private void deleteFileByPath(String remotePath) throws Exception {
        String encodedPath = URLEncoder.encode(remotePath, StandardCharsets.UTF_8);
        String urlString = API_BASE_URL + "/resources?path=" + encodedPath + "&permanently=true";

        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Authorization", "OAuth " + yandexToken);

        int responseCode = conn.getResponseCode();
        if (responseCode != 204 && responseCode != 202) {
            log.warn("Failed to delete file, response code: {}", responseCode);
        }
    }

    @Override
    public List<BackupFileInfo> listBackupFiles(String prefix) throws Exception {
        if (yandexToken == null || yandexToken.isBlank()) {
            log.warn("Yandex.Disk token not configured, cannot list backup files");
            return Collections.emptyList();
        }

        ensureFolderPath("bd_dumps");
        String folderPath = "/" + rootFolderName + "/" + "bd_dumps";
        String encodedPath = URLEncoder.encode(folderPath, StandardCharsets.UTF_8);
        String urlString = API_BASE_URL + "/resources?path=" + encodedPath + "&limit=" + MAX_BACKUPS_TO_LIST;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "OAuth " + yandexToken);

        int responseCode = conn.getResponseCode();
        if (responseCode == 404) {
            log.debug("Backup folder not found on Yandex.Disk");
            return Collections.emptyList();
        } else if (responseCode != 200) {
            log.error("Failed to list backup files, response code: {}", responseCode);
            return Collections.emptyList();
        }

        try (InputStream is = conn.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            JsonNode items = root.path("_embedded").path("items");
            List<BackupFileInfo> result = new ArrayList<>();
            for (JsonNode item : items) {
                String name = item.path("name").asText();
                if (name.startsWith(prefix)) {
                    long size = item.path("size").asLong();
                    result.add(new BackupFileInfo(name, size));
                }
            }
            // Сортировка по имени (новые сверху, т.к. имя содержит дату)
            result.sort((a, b) -> b.fileName().compareTo(a.fileName()));
            return result;
        }
    }
}