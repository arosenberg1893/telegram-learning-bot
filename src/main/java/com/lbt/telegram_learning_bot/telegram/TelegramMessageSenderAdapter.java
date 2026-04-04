package com.lbt.telegram_learning_bot.telegram;

import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.platform.Platform;
import com.lbt.telegram_learning_bot.service.UserSessionService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class TelegramMessageSenderAdapter implements MessageSender {

    private final TelegramBot bot;
    private final UserSessionService sessionService;
    private final long userId;

    public TelegramMessageSenderAdapter(TelegramBot bot,
                                        UserSessionService sessionService,
                                        long userId) {
        this.bot = bot;
        this.sessionService = sessionService;
        this.userId = userId;
    }

    @Override
    public Platform getPlatform() {
        return Platform.TELEGRAM;
    }

    @Override
    public void sendText(long userId, String text) {
        var req = new SendMessage(userId, text).parseMode(ParseMode.Markdown);
        var resp = bot.execute(req);
        if (!resp.isOk()) log.error("[TG-adapter] sendText failed: {}", resp.description());
    }

    @Override
    public void sendMenu(long userId, String text, BotKeyboard keyboard) {
        UserContext ctx = sessionService.getCurrentContext(userId);
        if (ctx.getLastInteractiveMessageId() != null) {
            bot.execute(new DeleteMessage(userId, ctx.getLastInteractiveMessageId()));
        }
        var req = new SendMessage(userId, text)
                .replyMarkup(toInlineKeyboardMarkup(keyboard))
                .parseMode(ParseMode.Markdown);
        SendResponse resp = bot.execute(req);
        if (resp.isOk()) {
            ctx.setLastInteractiveMessageId(resp.message().messageId());
            sessionService.updateSessionContext(userId, ctx);
        } else {
            log.error("[TG-adapter] sendMenu failed: {}", resp.description());
        }
    }

    @Override
    public void editMenu(long userId, int messageId, String text, BotKeyboard keyboard) {
        var req = new EditMessageText(userId, messageId, text).parseMode(ParseMode.Markdown);
        if (keyboard != null) req.replyMarkup(toInlineKeyboardMarkup(keyboard));
        var resp = bot.execute(req);
        if (!resp.isOk()) log.error("[TG-adapter] editMenu failed: {}", resp.description());
    }

    @Override
    public void deleteMessage(long userId, int messageId) {
        bot.execute(new DeleteMessage(userId, messageId));
    }

    @Override
    public List<Integer> sendImageGroup(long userId, List<?> images) {
        return List.of();
    }

    @Override
    public Integer sendDocument(long userId, byte[] data, String fileName, String caption, BotKeyboard keyboard) {
        var req = new SendDocument(userId, data).fileName(fileName);
        if (caption != null && !caption.isEmpty()) {
            req.caption(caption);
        }
        if (keyboard != null) req.replyMarkup(toInlineKeyboardMarkup(keyboard));
        var resp = bot.execute(req);
        return resp.isOk() ? resp.message().messageId() : null;
    }

    @Override
    public Integer sendProgress(long userId) {
        SendResponse resp = bot.execute(new SendMessage(userId, "⏳ Обрабатываю файл..."));
        return resp.isOk() ? resp.message().messageId() : null;
    }

    private InlineKeyboardMarkup toInlineKeyboardMarkup(BotKeyboard keyboard) {
        if (keyboard == null || keyboard.getRows().isEmpty()) return null;
        List<InlineKeyboardButton[]> rows = new ArrayList<>();
        for (List<BotButton> row : keyboard.getRows()) {
            InlineKeyboardButton[] tgRow = row.stream()
                    .map(b -> new InlineKeyboardButton(b.label()).callbackData(b.callbackData()))
                    .toArray(InlineKeyboardButton[]::new);
            rows.add(tgRow);
        }
        return new InlineKeyboardMarkup(rows.toArray(new InlineKeyboardButton[0][]));
    }
}