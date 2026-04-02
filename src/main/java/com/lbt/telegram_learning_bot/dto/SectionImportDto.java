package com.lbt.telegram_learning_bot.dto;

import lombok.Data;

import java.util.List;

// SectionImportDto.java
@Data
public class SectionImportDto {
    private String title;
    private String description;
    private List<TopicImportDto> topics;
}