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
 *
 * <p>Расширение файла определяется автоматически по magic bytes содержимого,
 * что корректно обрабатывает PNG, GIF, WebP и JPEG вне зависимости от имени
 * источника.</p>
 */
@Slf4j
@Service
public class ImageStorageService {

    @Value("${course.images.storage.path:./course_images}")
    private String basePath;

    /**
     * Сохраняет изображение для указанной сущности.
     * Расширение файла определяется по magic bytes, а не фиксируется как {@code .jpg}.
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
        String extension = detectExtension(imageData);
        Path targetPath = dir.resolve(entityId + "." + extension);
        Files.write(targetPath, imageData);
        log.debug("Saved image for entity {} id={} to {} (detected: {})",
                entityType, entityId, targetPath, extension);
        return targetPath;
    }

    /**
     * Сохраняет изображение по заданному составному пути внутри базовой директории.
     *
     * @param subPath   подпуть относительно базовой директории (например, {@code "courseId/topicId"})
     * @param fileName  имя файла (расширение берётся из этого параметра)
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

    /**
     * Определяет расширение файла изображения по его magic bytes.
     *
     * <p>Поддерживаемые форматы:</p>
     * <ul>
     *   <li>PNG  — {@code 89 50 4E 47}</li>
     *   <li>GIF  — {@code 47 49 46 38}</li>
     *   <li>WebP — {@code 52 49 46 46 .. .. .. .. 57 45 42 50}</li>
     *   <li>JPEG — {@code FF D8 FF} (по умолчанию)</li>
     * </ul>
     *
     * @param data байты изображения
     * @return расширение без точки: {@code "png"}, {@code "gif"}, {@code "webp"} или {@code "jpg"}
     */
    static String detectExtension(byte[] data) {
        if (data == null || data.length < 4) return "jpg";

        // PNG: 89 50 4E 47
        if (data[0] == (byte) 0x89 && data[1] == 0x50
                && data[2] == 0x4E && data[3] == 0x47) {
            return "png";
        }
        // GIF: 47 49 46 38
        if (data[0] == 0x47 && data[1] == 0x49
                && data[2] == 0x46 && data[3] == 0x38) {
            return "gif";
        }
        // WebP: RIFF....WEBP (bytes 0-3 = RIFF, bytes 8-11 = WEBP)
        if (data.length >= 12
                && data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46
                && data[8] == 0x57 && data[9] == 0x45 && data[10] == 0x42 && data[11] == 0x50) {
            return "webp";
        }
        // Default: JPEG (FF D8 FF)
        return "jpg";
    }
}
