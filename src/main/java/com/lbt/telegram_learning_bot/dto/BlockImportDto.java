package com.lbt.telegram_learning_bot.dto;

import lombok.Data;

import java.util.List;

// BlockImportDto.java
@Data
public class BlockImportDto {
    private String text;
    private List<String> images; // описания изображений
    private List<QuestionImportDto> questions;
}
