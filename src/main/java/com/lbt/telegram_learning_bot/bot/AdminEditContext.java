package com.lbt.telegram_learning_bot.bot;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Контекст сессии редактирования учебного контента администратором.
 *
 * <p>Хранит идентификаторы редактируемых сущностей, очередь изображений для загрузки
 * и состояние административных страниц. Является вложенным объектом {@link UserContext}
 * и прозрачно сериализуется Jackson.</p>
 */
@Data
public class AdminEditContext {

    // --- Редактируемые сущности ---
    private Long editingCourseId;
    private Long editingSectionId;
    private Long editingTopicId;

    // --- Загрузка изображений ---
    private List<PendingImage> pendingImages = new ArrayList<>();
    private List<String> pendingImageDescriptions = new ArrayList<>();
    private int currentImageIndex;
    private Long targetEntityId;
    private String targetEntityType;

    // --- Административные страницы ---
    private Integer adminSectionsPage = 0;
    private Integer adminTopicsPage = 0;

    // --- Прочее ---
    private String selectedBackupFileName;
}
