package com.lbt.telegram_learning_bot.service.cloud;

import java.util.List;

/**
 * Сервис для работы с облачными хранилищами.
 * Поддерживает загрузку файлов, получение списка резервных копий,
 * скачивание и удаление файлов.
 */
public interface CloudStorageService {

    /**
     * Загружает файл в облачное хранилище.
     * @param data содержимое файла
     * @param fileName имя файла
     * @return публичная ссылка на файл (временная)
     * @throws Exception если загрузка не удалась
     */
    String upload(byte[] data, String fileName) throws Exception;

    /**
     * Возвращает имя сервиса (для логирования).
     */
    String getName();

    /**
     * Возвращает список имён файлов, начинающихся с указанного префикса.
     * @param prefix префикс (например, "backup_")
     * @return список имён файлов, отсортированный по дате (новые первыми)
     * @throws Exception если не удалось получить список
     */
    List<String> listBackups(String prefix) throws Exception;

    /**
     * Удаляет файл из облачного хранилища.
     * @param fileName имя файла
     * @throws Exception если удаление не удалось
     */
    void deleteFile(String fileName) throws Exception;

    /**
     * Скачивает файл из облачного хранилища.
     * @param fileName имя файла
     * @return содержимое файла в виде байтового массива
     * @throws Exception если скачивание не удалось
     */
    byte[] downloadFile(String fileName) throws Exception;

    /**
     * Возвращает список файлов резервных копий с их размерами.
     * @param prefix префикс имени файла (обычно "backup_")
     * @return список объектов BackupFileInfo (имя, размер в байтах)
     */
    List<BackupFileInfo> listBackupFiles(String prefix) throws Exception;

    record BackupFileInfo(String fileName, long sizeBytes) {}
}