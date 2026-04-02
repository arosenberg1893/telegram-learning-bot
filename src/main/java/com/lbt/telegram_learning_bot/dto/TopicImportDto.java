package com.lbt.telegram_learning_bot.dto;

import lombok.Data;

import java.util.List;

// TopicImportDto.java
@Data
public class TopicImportDto {
    private String title;
    private String description;
    private List<BlockImportDto> blocks;
}