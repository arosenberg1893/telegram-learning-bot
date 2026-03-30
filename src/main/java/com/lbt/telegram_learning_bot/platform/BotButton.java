package com.lbt.telegram_learning_bot.platform;

/**
 * Платформо-независимая кнопка.
 * Поддерживает только callback-кнопки (inline), т.к. VK и TG оба поддерживают этот тип.
 */
public record BotButton(String label, String callbackData) {

    public static BotButton callback(String label, String callbackData) {
        return new BotButton(label, callbackData);
    }
}
