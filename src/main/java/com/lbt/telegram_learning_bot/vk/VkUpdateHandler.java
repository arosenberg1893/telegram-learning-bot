package com.lbt.telegram_learning_bot.vk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbt.telegram_learning_bot.platform.Platform;
import com.lbt.telegram_learning_bot.service.PlatformUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vk.bot.enabled", havingValue = "true")
public class VkUpdateHandler {

    private final VkBotHandler vkBotHandler;
    private final PlatformUserService platformUserService;
    private final VkHttpClient vkHttpClient;
    private final ObjectMapper objectMapper;

    public void handle(JsonNode update) {
        if (update == null) return;
        String type = update.get("type").asText();
        JsonNode object = update.get("object");

        switch (type) {
            case "message_new" -> {
                JsonNode msg = object.get("message");
                long vkUserId = msg.get("from_id").asLong();
                long internalUserId = platformUserService.resolveUserId(Platform.VK, vkUserId);
                String text = msg.has("text") ? msg.get("text").asText() : "";
                int msgId = msg.get("id").asInt();
                log.debug("[VK] message_new from vkUser={} internalUser={}: {}", vkUserId, internalUserId, text);
                
                // Проверяем наличие документа
                JsonNode attachments = msg.get("attachments");
                if (attachments != null && attachments.isArray()) {
                    for (JsonNode att : attachments) {
                        if ("doc".equals(att.get("type").asText())) {
                            JsonNode doc = att.get("doc");
                            String docUrl = doc.has("url") ? doc.get("url").asText() : null;
                            if (docUrl != null && !docUrl.isEmpty()) {
                                Map<String, Object> fileRef = new HashMap<>();
                                fileRef.put("url", docUrl);
                                vkBotHandler.handleDocument(internalUserId, vkUserId, fileRef, msgId);
                                return;
                            } else {
                                log.warn("Document has no URL, cannot download");
                            }
                        }
                    }
                }
                vkBotHandler.handleMessage(internalUserId, vkUserId, text, msgId);
            }
            case "message_event" -> {
                long vkUserId = object.get("user_id").asLong();
                long internalUserId = platformUserService.resolveUserId(Platform.VK, vkUserId);
                JsonNode payloadNode = object.get("payload");
                String callbackData = extractCallbackData(payloadNode);
                int msgId = object.get("conversation_message_id").asInt();
                String eventId = object.get("event_id").asText();
                log.info("[VK] message_event: userId={}, callbackData={}, msgId={}", internalUserId, callbackData, msgId);
                vkBotHandler.handleCallback(internalUserId, vkUserId, callbackData, msgId, eventId);
                vkHttpClient.sendMessageEventAnswer(eventId, vkUserId);
            }
            default -> log.debug("[VK] Ignored event type: {}", type);
        }
    }

    private String extractCallbackData(JsonNode payloadNode) {
        if (payloadNode == null) {
            log.warn("Payload node is null");
            return "";
        }
        if (payloadNode.isTextual()) {
            String raw = payloadNode.asText();
            log.debug("Payload as text: {}", raw);
            try {
                JsonNode inner = objectMapper.readTree(raw);
                if (inner.has("callback")) {
                    return inner.get("callback").asText();
                }
                return raw;
            } catch (Exception e) {
                return raw;
            }
        }
        if (payloadNode.isObject()) {
            JsonNode callbackNode = payloadNode.get("callback");
            if (callbackNode != null && callbackNode.isTextual()) {
                return callbackNode.asText();
            }
            return payloadNode.toString();
        }
        log.warn("Unexpected payload type: {}", payloadNode.getNodeType());
        return payloadNode.asText();
    }
}
