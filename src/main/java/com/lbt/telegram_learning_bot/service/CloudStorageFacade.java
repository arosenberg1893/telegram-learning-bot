package com.lbt.telegram_learning_bot.service;

import com.lbt.telegram_learning_bot.service.cloud.CloudStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CloudStorageFacade {

    private final List<CloudStorageService> storageServices;

    public String uploadWithFallback(byte[] data, String fileName) throws Exception {
        for (CloudStorageService service : storageServices) {
            try {
                log.info("Trying to upload to {}...", service.getName());
                String link = service.upload(data, fileName);
                log.info("Successfully uploaded to {}: {}", service.getName(), link);
                return link;
            } catch (Exception e) {
                log.warn("Failed to upload to {}: {}", service.getName(), e.getMessage());
            }
        }
        throw new RuntimeException("All cloud storage services failed");
    }
}