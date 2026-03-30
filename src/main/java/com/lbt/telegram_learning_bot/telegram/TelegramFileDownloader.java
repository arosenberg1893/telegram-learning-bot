package com.lbt.telegram_learning_bot.telegram;

import com.lbt.telegram_learning_bot.platform.FileDownloader;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.request.GetFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class TelegramFileDownloader implements FileDownloader {

    private final TelegramBot telegramBot;

    @Override
    public byte[] downloadFile(Object fileReference) throws Exception {
        if (!(fileReference instanceof Message message)) {
            throw new IllegalArgumentException("Expected Message for Telegram");
        }
        var document = message.document();
        if (document == null) {
            throw new IllegalArgumentException("Message does not contain a document");
        }
        String fileId = document.fileId();
        var file = telegramBot.execute(new GetFile(fileId)).file();
        return telegramBot.getFileContent(file);
    }
}
