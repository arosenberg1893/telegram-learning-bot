package com.lbt.telegram_learning_bot.platform;

/**
 * Платформонезависимый загрузчик файлов.
 * Для Telegram – принимает Message, для VK – Map с ownerId и docId.
 */
public interface FileDownloader {
    byte[] downloadFile(Object fileReference) throws Exception;
}
