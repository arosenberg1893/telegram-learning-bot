package com.lbt.telegram_learning_bot.vk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class VkHttpClient {

    private final String accessToken;
    private final int groupId;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VkHttpClient(@Value("${vk.bot.token}") String accessToken,
                        @Value("${vk.bot.group-id}") int groupId) {
        this.accessToken = accessToken;
        this.groupId = groupId;
    }

    public Integer sendMessage(int peerId, String message) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("message", message);
        params.put("random_id", String.valueOf(ThreadLocalRandom.current().nextInt()));
        return execute("messages.send", params).path("response").asInt();
    }

    public Integer sendMessageWithKeyboard(int peerId, String message, String keyboard) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("message", message);
        params.put("random_id", String.valueOf(ThreadLocalRandom.current().nextInt()));
        if (keyboard != null) {
            params.put("keyboard", keyboard);
        }
        JsonNode response = execute("messages.send", params);
        if (response == null || !response.has("response")) {
            log.error("sendMessageWithKeyboard: invalid response, response={}", response);
            return null;
        }
        int msgId = response.path("response").asInt();
        log.debug("Message sent successfully, msgId={}", msgId);
        return msgId;
    }

    public boolean editMessage(int peerId, int messageId, String message, String keyboard) {
        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("message_id", String.valueOf(messageId));
        params.put("message", message);
        if (keyboard != null) {
            params.put("keyboard", keyboard);
        }
        JsonNode response = execute("messages.edit", params);
        if (response == null || !response.has("response")) {
            log.error("editMessage: invalid response");
            return false;
        }
        return response.path("response").asInt() == 1;
    }

    public boolean deleteMessage(int... messageIds) {
        StringBuilder ids = new StringBuilder();
        for (int id : messageIds) {
            if (ids.length() > 0) ids.append(",");
            ids.append(id);
        }
        Map<String, String> params = new HashMap<>();
        params.put("message_ids", ids.toString());
        params.put("delete_for_all", "1");
        JsonNode response = execute("messages.delete", params);
        if (response == null || !response.has("response")) {
            log.error("deleteMessage: invalid response");
            return false;
        }
        return response.path("response").path(String.valueOf(messageIds[0])).asInt() == 1;
    }

    public String getUserName(long userId) {
        Map<String, String> params = new HashMap<>();
        params.put("user_ids", String.valueOf(userId));
        params.put("fields", "first_name");
        JsonNode response = execute("users.get", params);
        JsonNode first = response.path("response").get(0);
        if (first != null && !first.isMissingNode()) {
            return first.path("first_name").asText();
        }
        return null;
    }

    public Map<String, String> getLongPollServer() {
        Map<String, String> params = new HashMap<>();
        params.put("group_id", String.valueOf(groupId));
        JsonNode response = execute("groups.getLongPollServer", params);
        JsonNode responseNode = response.path("response");
        Map<String, String> result = new HashMap<>();
        result.put("key", responseNode.path("key").asText());
        result.put("server", responseNode.path("server").asText());
        result.put("ts", responseNode.path("ts").asText());
        return result;
    }

    public JsonNode getLongPollEvents(String server, String key, String ts) {
        try {
            String url = server + "?act=a_check&key=" + key + "&ts=" + ts + "&wait=25";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                return objectMapper.readTree(sb.toString());
            }
        } catch (Exception e) {
            log.error("Error getting Long Poll events", e);
            return null;
        }
    }

    public void sendMessageEventAnswer(String eventId, long userId) {
        Map<String, String> params = new HashMap<>();
        params.put("event_id", eventId);
        params.put("user_id", String.valueOf(userId));
        params.put("peer_id", String.valueOf(userId));
        execute("messages.sendMessageEventAnswer", params);
    }

    public JsonNode execute(String method, Map<String, String> params) {
        try {
            StringBuilder urlBuilder = new StringBuilder("https://api.vk.com/method/")
                    .append(method)
                    .append("?access_token=").append(accessToken)
                    .append("&v=5.131");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                urlBuilder.append("&").append(entry.getKey()).append("=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
            HttpURLConnection conn = (HttpURLConnection) new URL(urlBuilder.toString()).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                log.error("VK API error: HTTP {}", responseCode);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                JsonNode root = objectMapper.readTree(sb.toString());
                if (root.has("error")) {
                    JsonNode error = root.path("error");
                    log.error("VK API error: {} - {}", error.path("error_code").asInt(), error.path("error_msg").asText());
                    return null;
                }
                return root;
            }
        } catch (Exception e) {
            log.error("Error executing VK method {}", method, e);
            return null;
        }
    }

    // VkHttpClient.java — добавьте следующие методы

    /**
     * Получить URL для загрузки документа.
     */
    public String getDocumentUploadServer() {
        Map<String, String> params = new HashMap<>();
        params.put("type", "doc");
        JsonNode response = execute("docs.getUploadServer", params);
        if (response == null || !response.has("response")) {
            log.error("Failed to get upload server");
            return null;
        }
        return response.path("response").path("upload_url").asText();
    }

    /**
     * Загрузить файл на полученный URL.
     * @param uploadUrl URL из getDocumentUploadServer
     * @param data байты файла
     * @param fileName имя файла
     * @return JSON ответ от сервера (содержит file)
     */
    public String uploadDocument(String uploadUrl, byte[] data, String fileName) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(uploadUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (java.io.OutputStream out = conn.getOutputStream()) {
                // Запись файла
                out.write(("--" + boundary + "\r\n").getBytes());
                out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n").getBytes());
                out.write("Content-Type: application/pdf\r\n\r\n".getBytes());
                out.write(data);
                out.write(("\r\n--" + boundary + "--\r\n").getBytes());
                out.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                log.error("Upload failed with HTTP {}", responseCode);
                return null;
            }

            try (java.io.BufferedReader reader = new java.io.BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        } catch (Exception e) {
            log.error("Error uploading document", e);
            return null;
        }
    }

    /**
     * Сохранить загруженный документ.
     * @param uploadResponse ответ от uploadDocument (JSON строка)
     * @return attachment строка вида "doc{owner_id}_{doc_id}", или null
     */
    public String saveDocument(String uploadResponse) {
        try {
            JsonNode fileNode = objectMapper.readTree(uploadResponse).path("file");
            if (fileNode.isMissingNode()) {
                log.error("No 'file' field in upload response");
                return null;
            }
            Map<String, String> params = new HashMap<>();
            params.put("file", fileNode.asText());
            JsonNode response = execute("docs.save", params);
            if (response == null || !response.has("response")) {
                log.error("Failed to save document");
                return null;
            }
            JsonNode doc = response.path("response").get(0);
            long ownerId = doc.path("owner_id").asLong();
            long docId = doc.path("id").asLong();
            return "doc" + ownerId + "_" + docId;
        } catch (Exception e) {
            log.error("Error saving document", e);
            return null;
        }
    }

    /**
     * Отправить документ пользователю.
     * @param peerId ID получателя
     * @param data байты файла
     * @param fileName имя файла
     * @param caption подпись
     * @return messageId или null
     */
    public Integer sendDocument(int peerId, byte[] data, String fileName, String caption) {
        String uploadUrl = getDocumentUploadServer();
        if (uploadUrl == null) return null;

        String uploadResponse = uploadDocument(uploadUrl, data, fileName);
        if (uploadResponse == null) return null;

        String attachment = saveDocument(uploadResponse);
        if (attachment == null) return null;

        Map<String, String> params = new HashMap<>();
        params.put("peer_id", String.valueOf(peerId));
        params.put("random_id", String.valueOf(ThreadLocalRandom.current().nextInt()));
        params.put("attachment", attachment);
        if (caption != null && !caption.isEmpty()) {
            params.put("message", caption);
        }
        JsonNode response = execute("messages.send", params);
        if (response == null || !response.has("response")) {
            log.error("Failed to send document message");
            return null;
        }
        return response.path("response").asInt();
    }

    public JsonNode getDocumentInfo(long ownerId, long docId) {
        Map<String, String> params = new HashMap<>();
        params.put("docs", ownerId + "_" + docId);
        JsonNode response = execute("docs.getById", params);
        if (response == null || !response.has("response")) {
            log.error("Failed to get document info for {}_{}", ownerId, docId);
            return null;
        }
        JsonNode items = response.path("response");
        if (items.isArray() && items.size() > 0) {
            return items.get(0);
        }
        return null;
    }

}