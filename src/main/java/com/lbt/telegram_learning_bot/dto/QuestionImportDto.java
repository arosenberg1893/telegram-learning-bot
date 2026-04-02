package com.lbt.telegram_learning_bot.dto;

import lombok.Data;

import java.util.List;

// QuestionImportDto.java
@Data
public class QuestionImportDto {
    private String text;
    private List<String> images;
    private List<String> options; // варианты ответа
    private int correctIndex; // индекс правильного ответа (0-based)
    private String explanation; // пояснение
}