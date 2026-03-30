package com.lbt.telegram_learning_bot.vk;

import com.fasterxml.jackson.databind.JsonNode;
import com.lbt.telegram_learning_bot.platform.FileDownloader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class VkFileDownloader implements FileDownloader {

    private final VkHttpClient vkHttpClient;

    @Override
    public byte[] downloadFile(Object fileReference) throws Exception {
        if (!(fileReference instanceof Map<?,?> map)) {
            throw new IllegalArgumentException("Expected Map with url or ownerId/docId");
        }
        
        // Приоритет: прямой URL (вариант А)
        if (map.containsKey("url")) {
            String url = (String) map.get("url");
            log.info("Downloading file from direct URL: {}", url);
            return downloadFromUrl(url);
        }
        
        // Fallback: старый метод через docs.getById (требует прав)
        Long ownerId = (Long) map.get("ownerId");
        Long docId = (Long) map.get("docId");
        if (ownerId == null || docId == null) {
            throw new IllegalArgumentException("Missing url or (ownerId, docId)");
        }
        JsonNode docInfo = vkHttpClient.getDocumentInfo(ownerId, docId);
        if (docInfo == null) {
            throw new RuntimeException("Failed to get document info");
        }
        String url = docInfo.path("url").asText();
        if (url == null || url.isEmpty()) {
            throw new RuntimeException("Document URL not found");
        }
        return downloadFromUrl(url);
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
}
