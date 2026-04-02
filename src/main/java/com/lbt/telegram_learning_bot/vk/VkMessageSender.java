package com.lbt.telegram_learning_bot.vk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.platform.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vk.bot.enabled", havingValue = "true")
public class VkMessageSender implements MessageSender {

    private final VkHttpClient vkHttpClient;
    private final ObjectMapper objectMapper; // внедряем Spring-бин

    // Хранилище последних ID сообщений, отправленных ботом для каждого пользователя
    private final Map<Long, Integer> lastBotMessageIds = new ConcurrentHashMap<>();

    @Override
    public Platform getPlatform() {
        return Platform.VK;
    }

    @Override
    public void sendText(long userId, String text) {
        try {
            vkHttpClient.sendMessage((int) userId, text);
        } catch (Exception e) {
            log.error("[VK] sendText failed for user {}: {}", userId, e.getMessage(), e);
        }
    }

    @Override
    public void sendMenu(long userId, String text, BotKeyboard keyboard) {
        Integer newMsgId = sendMenuAndReturnId(userId, text, keyboard);
        if (newMsgId != null) {
            lastBotMessageIds.put(userId, newMsgId);
        }
    }

    /**
     * Отправляет сообщение с клавиатурой и возвращает message_id.
     */
    public Integer sendMenuAndReturnId(long userId, String text, BotKeyboard keyboard) {
        try {
            String vkKeyboard = toVkKeyboard(keyboard);
            log.debug("Sending menu to user {}: text={}, keyboard={}", userId, text, vkKeyboard);
            Integer msgId = vkHttpClient.sendMessageWithKeyboard((int) userId, text, vkKeyboard);
            log.debug("sendMessageWithKeyboard result: {}", msgId);
            return msgId;
        } catch (Exception e) {
            log.error("[VK] sendMenu failed for user {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void editMenu(long userId, int oldMessageIdIgnored, String text, BotKeyboard keyboard) {
        // 1. Удаляем предыдущее сообщение бота, если оно есть
        Integer oldMsgId = lastBotMessageIds.remove(userId);
        if (oldMsgId != null) {
            try {
                vkHttpClient.deleteMessage(oldMsgId);
                log.debug("Deleted previous bot message {} for user {}", oldMsgId, userId);
            } catch (Exception e) {
                log.debug("Could not delete message {}: {}", oldMsgId, e.getMessage());
            }
        }

        // 2. Отправляем новое сообщение
        Integer newMsgId = sendMenuAndReturnId(userId, text, keyboard);
        if (newMsgId != null) {
            lastBotMessageIds.put(userId, newMsgId);
        }
    }

    @Override
    public void deleteMessage(long userId, int messageId) {
        try {
            vkHttpClient.deleteMessage(messageId);
            // Если удаляем последнее сообщение бота, убираем его из хранилища
            Integer lastId = lastBotMessageIds.get(userId);
            if (lastId != null && lastId == messageId) {
                lastBotMessageIds.remove(userId);
            }
        } catch (Exception e) {
            log.debug("[VK] deleteMessage failed: {}", e.getMessage());
        }
    }

    @Override
    public List<Integer> sendImageGroup(long userId, List<?> images) {
        log.warn("[VK] sendImageGroup not implemented");
        return new ArrayList<>();
    }

    @Override
    public Integer sendDocument(long userId, byte[] data, String fileName, String caption, BotKeyboard keyboard) {
        try {
            // VK не поддерживает клавиатуру вместе с документом в одном сообщении.
            // Отправим документ отдельно, а потом, если нужно, отдельное сообщение с клавиатурой.
            Integer msgId = vkHttpClient.sendDocument((int) userId, data, fileName, caption);
            if (keyboard != null && msgId != null) {
                // Отправляем дополнительное сообщение с клавиатурой
                sendMenu(userId, "Выберите действие:", keyboard);
            }
            return msgId;
        } catch (Exception e) {
            log.error("[VK] sendDocument failed for user {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public Integer sendProgress(long userId) {
        try {
            return vkHttpClient.sendMessage((int) userId, "⏳ Обрабатываю файл...");
        } catch (Exception e) {
            log.error("[VK] sendProgress failed: {}", e.getMessage());
            return null;
        }
    }

    private String toVkKeyboard(BotKeyboard keyboard) {
        if (keyboard == null || keyboard.getRows().isEmpty()) return null;
        try {
            List<List<Map<String, Object>>> vkRows = new ArrayList<>();
            for (List<BotButton> row : keyboard.getRows()) {
                List<Map<String, Object>> vkRow = new ArrayList<>();
                for (BotButton btn : row) {
                    Map<String, String> payloadMap = new HashMap<>();
                    payloadMap.put("callback", btn.callbackData());
                    String payloadJson = objectMapper.writeValueAsString(payloadMap);

                    Map<String, Object> action = new LinkedHashMap<>();
                    action.put("type", "callback");
                    action.put("label", btn.label());
                    action.put("payload", payloadJson);

                    Map<String, Object> button = new LinkedHashMap<>();
                    button.put("action", action);
                    vkRow.add(button);
                }
                vkRows.add(vkRow);
            }
            Map<String, Object> keyboardJson = new LinkedHashMap<>();
            keyboardJson.put("one_time", false);
            keyboardJson.put("inline", true);
            keyboardJson.put("buttons", vkRows);
            return objectMapper.writeValueAsString(keyboardJson);
        } catch (Exception e) {
            log.error("Failed to serialize VK keyboard", e);
            return null;
        }
    }
}