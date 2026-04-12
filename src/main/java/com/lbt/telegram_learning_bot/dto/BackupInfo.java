package com.lbt.telegram_learning_bot.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DTO для информации о резервной копии базы данных.
 */
public class BackupInfo {

    private final String fileName;
    private final LocalDateTime timestamp;
    private final long sizeBytes;
    private boolean yandexDiskUploaded;
    private boolean googleDriveUploaded;

    public BackupInfo(String fileName, LocalDateTime timestamp, long sizeBytes) {
        this.fileName = fileName;
        this.timestamp = timestamp;
        this.sizeBytes = sizeBytes;
        this.yandexDiskUploaded = false;
        this.googleDriveUploaded = false;
    }

    public String getFileName() {
        return fileName;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public boolean isYandexDiskUploaded() {
        return yandexDiskUploaded;
    }

    public void setYandexDiskUploaded(boolean yandexDiskUploaded) {
        this.yandexDiskUploaded = yandexDiskUploaded;
    }

    public boolean isGoogleDriveUploaded() {
        return googleDriveUploaded;
    }

    public void setGoogleDriveUploaded(boolean googleDriveUploaded) {
        this.googleDriveUploaded = googleDriveUploaded;
    }

    public String getFormattedTimestamp() {
        if (timestamp == null) return "неизвестно";
        return timestamp.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
    }

    public String getFormattedSize() {
        if (sizeBytes <= 0) return "неизвестно";
        if (sizeBytes < 1024) return sizeBytes + " Б";
        if (sizeBytes < 1024 * 1024) return String.format("%.2f КБ", sizeBytes / 1024.0);
        return String.format("%.2f МБ", sizeBytes / (1024.0 * 1024.0));
    }

    @Override
    public String toString() {
        return "BackupInfo{" +
                "fileName='" + fileName + '\'' +
                ", timestamp=" + timestamp +
                ", sizeBytes=" + sizeBytes +
                ", yandexDiskUploaded=" + yandexDiskUploaded +
                ", googleDriveUploaded=" + googleDriveUploaded +
                '}';
    }
}