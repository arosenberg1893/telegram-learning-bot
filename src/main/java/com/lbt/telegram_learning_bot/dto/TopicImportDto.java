package com.lbt.telegram_learning_bot.dto;

import lombok.Data;

import java.util.List;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

// TopicImportDto.java
@Data
public class TopicImportDto {
    private String title;
    private String description;
    private List<BlockImportDto> blocks;
}