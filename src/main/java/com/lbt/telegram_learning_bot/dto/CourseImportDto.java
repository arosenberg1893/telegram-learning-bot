// CourseImportDto.java
package com.lbt.telegram_learning_bot.dto;

import lombok.Data;

import java.util.List;

@Data
public class CourseImportDto {
    private String title;
    private String description;
    private List<SectionImportDto> sections;
}