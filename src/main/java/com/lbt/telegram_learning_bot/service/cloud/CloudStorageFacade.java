package com.lbt.telegram_learning_bot.service.cloud;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Фасад для работы с облачными хранилищами.
 * Пробует последовательно все доступные сервисы, возвращает результат первого успешного.
 * Для операций, не требующих уникальности (listBackups, deleteFile), выполняет операцию на всех сервисах.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudStorageFacade {

    private final List<CloudStorageService> storageServices;

    /**
     * Загружает файл в облачное хранилище, пробуя сервисы по очереди.
     * @param data содержимое файла
     * @param fileName имя файла
     * @return публичная ссылка на файл от первого успешного сервиса
     * @throws RuntimeException если все сервисы недоступны
     */
    public String upload(byte[] data, String fileName) throws Exception {
        for (CloudStorageService service : storageServices) {
            try {
                log.info("Trying to upload {} to {}...", fileName, service.getName());
                String link = service.upload(data, fileName);
                log.info("Successfully uploaded to {}: {}", service.getName(), link);
                return link;
            } catch (Exception e) {
                log.warn("Failed to upload to {}: {}", service.getName(), e.getMessage());
            }
        }
        throw new RuntimeException("All cloud storage services failed to upload file: " + fileName);
    }

    /**
     * Загружает файл с fallback-механизмом (для обратной совместимости).
     * @param data содержимое файла
     * @param fileName имя файла
     * @return публичная ссылка на файл
     */
    public String uploadWithFallback(byte[] data, String fileName) throws Exception {
        return upload(data, fileName);
    }

    /**
     * Возвращает объединённый список имён файлов из всех облачных хранилищ,
     * начинающихся с указанного префикса. Сортирует по убыванию даты (по имени файла).
     * @param prefix префикс (например, "backup_")
     * @return отсортированный список уникальных имён файлов
     */
    public List<String> listBackups(String prefix) throws Exception {
        List<String> allFiles = new ArrayList<>();
        for (CloudStorageService service : storageServices) {
            try {
                log.debug("Listing backups from {} with prefix '{}'", service.getName(), prefix);
                List<String> files = service.listBackups(prefix);
                allFiles.addAll(files);
            } catch (Exception e) {
                log.warn("Failed to list backups from {}: {}", service.getName(), e.getMessage());
            }
        }
        // Удаляем дубликаты и сортируем по убыванию (новые первыми)
        return allFiles.stream()
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    /**
     * Удаляет файл из всех облачных хранилищ (по возможности).
     * @param fileName имя файла
     */
    public void deleteFile(String fileName) throws Exception {
        Exception lastException = null;
        boolean anySuccess = false;
        for (CloudStorageService service : storageServices) {
            try {
                log.info("Deleting file {} from {}", fileName, service.getName());
                service.deleteFile(fileName);
                anySuccess = true;
                log.info("Deleted from {}", service.getName());
            } catch (Exception e) {
                log.warn("Failed to delete from {}: {}", service.getName(), e.getMessage());
                lastException = e;
            }
        }
        if (!anySuccess && lastException != null) {
            throw new RuntimeException("Failed to delete file from all services", lastException);
        }
    }

    /**
     * Скачивает файл из любого доступного облачного хранилища.
     * @param fileName имя файла
     * @return содержимое файла
     * @throws RuntimeException если файл не найден ни в одном сервисе
     */
    public byte[] downloadFile(String fileName) throws Exception {
        for (CloudStorageService service : storageServices) {
            try {
                log.info("Downloading file {} from {}", fileName, service.getName());
                byte[] data = service.downloadFile(fileName);
                log.info("Downloaded from {}", service.getName());
                return data;
            } catch (Exception e) {
                log.warn("Failed to download from {}: {}", service.getName(), e.getMessage());
            }
        }
        throw new RuntimeException("File not found in any cloud storage: " + fileName);
    }

    /**
     * Загружает файл во все доступные облачные хранилища.
     * @return карта: имя сервиса -> успех (true/false)
     */
    public Map<String, Boolean> uploadToAll(byte[] data, String fileName) {
        Map<String, Boolean> results = new LinkedHashMap<>();
        for (CloudStorageService service : storageServices) {
            boolean success = false;
            int attempts = 0;
            while (!success && attempts < 3) {
                try {
                    log.info("Uploading {} to {} (attempt {})...", fileName, service.getName(), attempts + 1);
                    service.upload(data, fileName);
                    log.info("Successfully uploaded to {}", service.getName());
                    success = true;
                    results.put(service.getName(), true);
                } catch (Exception e) {
                    attempts++;
                    log.warn("Failed to upload to {} (attempt {}): {}", service.getName(), attempts, e.getMessage());
                    if (attempts >= 3) {
                        results.put(service.getName(), false);
                    } else {
                        try {
                            Thread.sleep(5000L * attempts); // экспоненциальная задержка
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }
        return results;
    }

    public List<CloudStorageService.BackupFileInfo> listBackupFiles(String prefix) throws Exception {
        Map<String, Long> resultMap = new LinkedHashMap<>();
        for (CloudStorageService service : storageServices) {
            try {
                List<CloudStorageService.BackupFileInfo> files = service.listBackupFiles(prefix);
                for (var info : files) {
                    resultMap.putIfAbsent(info.fileName(), info.sizeBytes());
                }
            } catch (Exception e) {
                log.warn("Failed to list backup files from {}: {}", service.getName(), e.getMessage());
            }
        }
        // Преобразуем в список и сортируем по убыванию имени (даты)
        return resultMap.entrySet().stream()
                .map(e -> new CloudStorageService.BackupFileInfo(e.getKey(), e.getValue()))
                .sorted((a, b) -> b.fileName().compareTo(a.fileName()))
                .collect(Collectors.toList());
    }
}