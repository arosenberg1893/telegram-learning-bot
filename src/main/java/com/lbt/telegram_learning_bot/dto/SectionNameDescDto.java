// SectionNameDescDto.java
package com.lbt.telegram_learning_bot.dto;

import lombok.Data;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

@Data
public class SectionNameDescDto {
    private String title;
    private String description;
}