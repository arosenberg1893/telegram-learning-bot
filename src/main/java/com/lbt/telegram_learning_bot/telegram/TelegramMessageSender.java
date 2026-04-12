package com.lbt.telegram_learning_bot.telegram;

import com.lbt.telegram_learning_bot.entity.BlockImage;
import com.lbt.telegram_learning_bot.entity.QuestionImage;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.platform.Platform;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.*;
import com.pengrad.telegrambot.request.*;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramMessageSender implements MessageSender {

    private final TelegramBot bot;

    @Override
    public Platform getPlatform() {
        return Platform.TELEGRAM;
    }

    @Override
    public void sendText(long userId, String text) {
        var req = new SendMessage(userId, text).parseMode(ParseMode.Markdown);
        var resp = bot.execute(req);
        if (!resp.isOk()) {
            log.error("[TG] sendText failed for user {}: {}", userId, resp.description());
        }
    }

    @Override
    public void sendMenu(long userId, String text, BotKeyboard keyboard) {
        var req = new SendMessage(userId, text)
                .replyMarkup(toInlineKeyboardMarkup(keyboard))
                .parseMode(ParseMode.Markdown);
        var resp = bot.execute(req);
        if (!resp.isOk()) {
            log.error("[TG] sendMenu failed for user {}: {}", userId, resp.description());
        }
    }

    @Override
    public void editMenu(long userId, int messageId, String text, BotKeyboard keyboard) {
        var req = new EditMessageText(userId, messageId, text)
                .parseMode(ParseMode.Markdown);
        if (keyboard != null) req.replyMarkup(toInlineKeyboardMarkup(keyboard));
        var resp = bot.execute(req);
        if (!resp.isOk()) {
            // Telegram не позволяет редактировать сообщения без текста (фото, документы и т.п.)
            // В таком случае удаляем старое и отправляем новое сообщение
            if (resp.description() != null && resp.description().contains("there is no text in the message")) {
                log.debug("[TG] editMenu: message {} has no text, deleting and sending new", messageId);
                bot.execute(new DeleteMessage(userId, messageId));
                var sendReq = new SendMessage(userId, text)
                        .parseMode(ParseMode.Markdown);
                if (keyboard != null) sendReq.replyMarkup(toInlineKeyboardMarkup(keyboard));
                bot.execute(sendReq);
            } else {
                log.error("[TG] editMenu failed for user {}: {}", userId, resp.description());
            }
        }
    }

    @Override
    public void deleteMessage(long userId, int messageId) {
        var resp = bot.execute(new DeleteMessage(userId, messageId));
        if (!resp.isOk()) {
            log.debug("[TG] deleteMessage failed for user {}, msgId {}: {}", userId, messageId, resp.description());
        }
    }

    @Override
    public List<Integer> sendImageGroup(long userId, List<?> images) {
        List<InputMedia> media = buildMediaList(images);
        if (media.isEmpty()) return List.of();

        var req = new SendMediaGroup(userId, media.toArray(new InputMedia[0]));
        var resp = bot.execute(req);
        if (resp.isOk()) {
            return Arrays.stream(resp.messages())
                    .map(com.pengrad.telegrambot.model.Message::messageId)
                    .toList();
        } else {
            log.error("[TG] sendImageGroup failed for user {}: {}", userId, resp.description());
            return List.of();
        }
    }

    @Override
    public Integer sendDocument(long userId, byte[] data, String fileName, String caption, BotKeyboard keyboard) {
        var req = new SendDocument(userId, data)
                .fileName(fileName);
        // caption(null) вызывает NPE внутри библиотеки — передаём только непустой caption
        if (caption != null && !caption.isEmpty()) {
            req.caption(caption);
        }
        if (keyboard != null) req.replyMarkup(toInlineKeyboardMarkup(keyboard));
        var resp = bot.execute(req);
        if (resp.isOk()) {
            return resp.message().messageId();
        } else {
            log.error("[TG] sendDocument failed for user {}: {}", userId, resp.description());
            return null;
        }
    }

    @Override
    public Integer sendProgress(long userId) {
        SendResponse resp = bot.execute(new SendMessage(userId, "⏳ Обрабатываю файл..."));
        return resp.isOk() ? resp.message().messageId() : null;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private InlineKeyboardMarkup toInlineKeyboardMarkup(BotKeyboard keyboard) {
        if (keyboard == null || keyboard.getRows().isEmpty()) return null;
        List<InlineKeyboardButton[]> tgRows = new ArrayList<>();
        for (List<BotButton> row : keyboard.getRows()) {
            InlineKeyboardButton[] tgRow = row.stream()
                    .map(b -> new InlineKeyboardButton(b.label()).callbackData(b.callbackData()))
                    .toArray(InlineKeyboardButton[]::new);
            tgRows.add(tgRow);
        }
        return new InlineKeyboardMarkup(tgRows.toArray(new InlineKeyboardButton[0][]));
    }

    private List<InputMedia> buildMediaList(List<?> images) {
        List<InputMedia> result = new ArrayList<>();
        for (Object img : images) {
            String filePath;
            String description;
            if (img instanceof BlockImage bi) {
                filePath = bi.getFilePath();
                description = bi.getDescription();
            } else if (img instanceof QuestionImage qi) {
                filePath = qi.getFilePath();
                description = qi.getDescription();
            } else continue;
            if (filePath == null || filePath.isEmpty()) continue;
            InputMediaPhoto photo = new InputMediaPhoto(new File(filePath));
            if (description != null && !description.isEmpty()) photo.caption(description);
            result.add(photo);
        }
        return result;
    }
}