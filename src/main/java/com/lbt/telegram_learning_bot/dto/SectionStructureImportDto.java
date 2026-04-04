package com.lbt.telegram_learning_bot.dto;

import lombok.Data;
import java.util.List;

@Data
public class SectionStructureImportDto {
    private String title;
    private String description;
    private List<TopicStructureImportDto> topics;
}