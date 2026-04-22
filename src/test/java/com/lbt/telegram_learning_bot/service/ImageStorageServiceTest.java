package com.lbt.telegram_learning_bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ImageStorageService")
class ImageStorageServiceTest {

    private ImageStorageService imageStorageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        imageStorageService = new ImageStorageService();
        ReflectionTestUtils.setField(imageStorageService, "basePath", tempDir.toString());
    }

    @Test
    @DisplayName("сохраняет изображение по типу сущности и ID")
    void savesImageByEntityTypeAndId() throws IOException {
        byte[] imageData = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}; // JPEG magic bytes
        Long entityId = 42L;
        String entityType = "block";

        Path savedPath = imageStorageService.saveImage(entityType, entityId, imageData);

        assertThat(savedPath).exists();
        assertThat(Files.readAllBytes(savedPath)).isEqualTo(imageData);
        assertThat(savedPath.getFileName().toString()).isEqualTo("42.jpg");
        assertThat(savedPath.getParent().getFileName().toString()).isEqualTo("block");
    }

    @Test
    @DisplayName("создаёт директории автоматически")
    void createsDirectoriesAutomatically() throws IOException {
        byte[] imageData = new byte[]{1, 2, 3};
        Path savedPath = imageStorageService.saveImage("question", 99L, imageData);

        assertThat(savedPath.getParent()).isDirectory();
    }

    @Test
    @DisplayName("сохраняет изображение по составному пути")
    void savesImageBySubPathAndFilename() throws IOException {
        byte[] imageData = new byte[]{10, 20, 30};
        String subPath = "courses/1/topics/5";
        String fileName = "image_001.jpg";

        Path savedPath = imageStorageService.saveImage(subPath, fileName, imageData);

        assertThat(savedPath).exists();
        assertThat(Files.readAllBytes(savedPath)).isEqualTo(imageData);
        assertThat(savedPath.getFileName().toString()).isEqualTo(fileName);
    }

    @Test
    @DisplayName("перезаписывает существующий файл")
    void overwritesExistingFile() throws IOException {
        byte[] originalData = {1, 2, 3};
        byte[] newData = {4, 5, 6, 7};

        imageStorageService.saveImage("block", 1L, originalData);
        imageStorageService.saveImage("block", 1L, newData);

        Path savedPath = tempDir.resolve("block/1.jpg");
        assertThat(Files.readAllBytes(savedPath)).isEqualTo(newData);
    }
}
