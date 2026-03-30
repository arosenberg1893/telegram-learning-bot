package com.lbt.telegram_learning_bot.bot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingImage {
    private String entityType;
    private Long entityId;
    private String description;
}