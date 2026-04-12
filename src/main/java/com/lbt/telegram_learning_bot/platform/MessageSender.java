package com.lbt.telegram_learning_bot.platform;

import java.util.List;

/**
 * Абстракция отправки сообщений конкретной платформы.
 * Каждый бот реализует этот интерфейс, что позволяет handlers
 * не зависеть от деталей Telegram API или VK API.
 */
public interface MessageSender {

    Platform getPlatform();

    /** Отправить текстовое сообщение без клавиатуры. */
    void sendText(long userId, String text);

    /** Отправить сообщение с интерактивной клавиатурой. */
    void sendMenu(long userId, String text, BotKeyboard keyboard);

    /**
     * Отредактировать уже отправленное сообщение.
     * Если платформа не поддерживает редактирование — отправляет новое.
     */
    void editMenu(long userId, int messageId, String text, BotKeyboard keyboard);

    /** Удалить сообщение (если платформа поддерживает). */
    void deleteMessage(long userId, int messageId);

    /** Отправить группу медиа-файлов (изображений). */
    List<Integer> sendImageGroup(long userId, List<?> images);

    /**
     * Отправить файл-документ (PDF и др.).
     * @return messageId отправленного сообщения, или null при ошибке
     */
    Integer sendDocument(long userId, byte[] data, String fileName, String caption, BotKeyboard keyboard);

    /** Отправить прогресс-сообщение («обрабатываю...»). */
    Integer sendProgress(long userId);
}