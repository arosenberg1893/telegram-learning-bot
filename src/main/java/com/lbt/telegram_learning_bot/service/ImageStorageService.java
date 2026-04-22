package com.lbt.telegram_learning_bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Единый сервис для сохранения изображений на диск.
 *
 * <p>Централизует логику хранения файлов, которая ранее была продублирована
 * в {@code AdminHandler} и {@code CourseImportService}. Путь базовой директории
 * настраивается через свойство {@code course.images.storage.path}.</p>
 */
@Slf4j
@Service
public class ImageStorageService {

    @Value("${course.images.storage.path:./course_images}")
    private String basePath;

    /**
     * Сохраняет изображение для указанной сущности.
     *
     * @param entityType тип сущности (например, {@code "block"} или {@code "question"})
     * @param entityId   идентификатор сущности
     * @param imageData  байты изображения
     * @return путь к сохранённому файлу
     * @throws IOException если не удалось создать директорию или записать файл
     */
    public Path saveImage(String entityType, Long entityId, byte[] imageData) throws IOException {
        Path dir = Paths.get(basePath, entityType);
        Files.createDirectories(dir);
        Path targetPath = dir.resolve(entityId + ".jpg");
        Files.write(targetPath, imageData);
        log.debug("Saved image for entity {} id={} to {}", entityType, entityId, targetPath);
        return targetPath;
    }

    /**
     * Сохраняет изображение по заданному составному пути внутри базовой директории.
     *
     * @param subPath   подпуть относительно базовой директории (например, {@code "courseId/topicId"})
     * @param fileName  имя файла
     * @param imageData байты изображения
     * @return путь к сохранённому файлу
     * @throws IOException если не удалось создать директорию или записать файл
     */
    public Path saveImage(String subPath, String fileName, byte[] imageData) throws IOException {
        Path dir = Paths.get(basePath, subPath);
        Files.createDirectories(dir);
        Path targetPath = dir.resolve(fileName);
        Files.write(targetPath, imageData);
        log.debug("Saved image to {}", targetPath);
        return targetPath;
    }
}
