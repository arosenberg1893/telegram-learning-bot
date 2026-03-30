package com.lbt.telegram_learning_bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
public class FileStorageService {

    private final Path tempDir;
    private final ConcurrentMap<String, Instant> expiryMap = new ConcurrentHashMap<>();
    private static final long EXPIRY_MINUTES = 15;

    public FileStorageService(@Value("${file.temp.dir:./temp_files}") String tempDirPath) throws IOException {
        this.tempDir = Paths.get(tempDirPath);
        Files.createDirectories(tempDir);
        log.info("Temp file directory: {}", tempDir.toAbsolutePath());
    }

    public String saveFile(byte[] data, String originalFileName) throws IOException {
        String fileId = UUID.randomUUID().toString();
        Path target = tempDir.resolve(fileId + "_" + originalFileName);
        Files.write(target, data);
        expiryMap.put(fileId, Instant.now().plusSeconds(EXPIRY_MINUTES * 60));
        log.debug("Saved file {} with id {}", target, fileId);
        return fileId;
    }

    public Path getFile(String fileId) {
        Instant expiry = expiryMap.get(fileId);
        if (expiry == null || Instant.now().isAfter(expiry)) {
            return null;
        }
        try {
            return Files.list(tempDir)
                    .filter(p -> p.getFileName().toString().startsWith(fileId + "_"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.error("Error finding file for id {}", fileId, e);
            return null;
        }
    }

    @Scheduled(fixedDelay = 3600000)
    public void cleanExpiredFiles() {
        Instant now = Instant.now();
        expiryMap.entrySet().removeIf(entry -> {
            if (now.isAfter(entry.getValue())) {
                try {
                    Path file = getFile(entry.getKey());
                    if (file != null) Files.deleteIfExists(file);
                } catch (IOException e) {
                    log.warn("Failed to delete expired file {}", entry.getKey(), e);
                }
                return true;
            }
            return false;
        });
        log.debug("Cleaned expired files");
    }
}
