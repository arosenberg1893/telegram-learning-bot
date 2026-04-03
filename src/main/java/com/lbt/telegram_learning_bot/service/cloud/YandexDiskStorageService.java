package com.lbt.telegram_learning_bot.service.cloud;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Реализация облачного хранилища для Яндекс.Диска.
 */
@Slf4j
@Service
@Order(1)
public class YandexDiskStorageService implements CloudStorageService {

    private static final long EXPIRY_MINUTES = 15;
    private static final String API_BASE_URL = "https://cloud-api.yandex.net/v1/disk";

    @Value("${yandex.disk.token:}")
    private String yandexToken;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentMap<String, Instant> expiryMap = new ConcurrentHashMap<>();

    @Override
    public String upload(byte[] data, String fileName) throws Exception {
        if (yandexToken == null || yandexToken.isBlank()) {
            throw new IllegalStateException("Yandex.Disk token is not configured");
        }

        // Генерируем уникальный путь на диске
        String uniqueId = UUID.randomUUID().toString();
        // Убираем возможные дублирующиеся .pdf в имени (если исходное имя уже содержит .pdf)
        String baseName = fileName.replaceAll("\\.pdf$", "");
        String remotePath = String.format("/temp_stats/%s_%s.pdf", uniqueId, baseName);
        log.info("Uploading PDF to Yandex.Disk: {}", remotePath);

        // Шаг 1: Запрашиваем URL для загрузки (создаём папку при необходимости)
        ensureFolderExists("/temp_stats");
        String uploadUrl = getUploadUrl(remotePath);
        if (uploadUrl == null) {
            throw new RuntimeException("Failed to get upload URL from Yandex.Disk");
        }

        // Шаг 2: Загружаем файл по полученному URL
        uploadFile(uploadUrl, data);

        // Шаг 3: Получаем публичную ссылку на файл
        String publicLink = getPublicLink(remotePath);
        if (publicLink == null) {
            throw new RuntimeException("Failed to get public link from Yandex.Disk");
        }

        // Запоминаем время создания для автоматической очистки
        expiryMap.put(uniqueId, Instant.now().plusSeconds(EXPIRY_MINUTES * 60));

        // Планируем удаление файла через 15 минут
        scheduleDeletion(remotePath, uniqueId);

        return publicLink;
    }

    private void ensureFolderExists(String folderPath) throws Exception {
        String encodedPath = URLEncoder.encode(folderPath, StandardCharsets.UTF_8);
        String urlString = API_BASE_URL + "/resources?path=" + encodedPath;
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "OAuth " + yandexToken);
        int responseCode = conn.getResponseCode();
        if (responseCode == 200) {
            // Папка существует
            return;
        }
        // Папка не найдена, создаём
        conn = (HttpURLConnection) new URL(API_BASE_URL + "/resources?path=" + encodedPath).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", "OAuth " + yandexToken);
        responseCode = conn.getResponseCode();
        if (responseCode != 201) {
            log.warn("Failed to create folder {}, response code: {}", folderPath, responseCode);
        }
    }

    private String getUploadUrl(String remotePath) throws Exception {
        String encodedPath = URLEncoder.encode(remotePath, StandardCharsets.UTF_8);
        String urlString = API_BASE_URL + "/resources/upload?path=" + encodedPath + "&overwrite=true";

        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "OAuth " + yandexToken);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            log.error("Failed to get upload URL, response code: {}", responseCode);
            return null;
        }

        try (InputStream is = connection.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            return root.path("href").asText(null);
        }
    }

    private void uploadFile(String uploadUrl, byte[] fileData) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/octet-stream");

        try (OutputStream os = connection.getOutputStream()) {
            os.write(fileData);
            os.flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 201) {
            log.error("Failed to upload file, response code: {}", responseCode);
            throw new RuntimeException("File upload failed with code: " + responseCode);
        }
    }

    private String getPublicLink(String remotePath) throws Exception {
        String encodedPath = URLEncoder.encode(remotePath, StandardCharsets.UTF_8);
        String urlString = API_BASE_URL + "/resources/download?path=" + encodedPath;

        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "OAuth " + yandexToken);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            log.error("Failed to get public link, response code: {}", responseCode);
            return null;
        }

        try (InputStream is = connection.getInputStream()) {
            JsonNode root = objectMapper.readTree(is);
            return root.path("href").asText(null);
        }
    }

    private void scheduleDeletion(String remotePath, String uniqueId) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(EXPIRY_MINUTES * 60 * 1000);
                deleteFile(remotePath);
                expiryMap.remove(uniqueId);
                log.info("Deleted expired file from Yandex.Disk: {}", remotePath);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Failed to delete expired file: {}", remotePath, e);
            }
        });
    }

    private void deleteFile(String remotePath) throws Exception {
        String encodedPath = URLEncoder.encode(remotePath, StandardCharsets.UTF_8);
        String urlString = API_BASE_URL + "/resources?path=" + encodedPath + "&permanently=true";

        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("DELETE");
        connection.setRequestProperty("Authorization", "OAuth " + yandexToken);

        int responseCode = connection.getResponseCode();
        if (responseCode != 204 && responseCode != 202) {
            log.warn("Failed to delete file, response code: {}", responseCode);
        }
    }

    @Override
    public String getName() {
        return "Yandex.Disk";
    }
}