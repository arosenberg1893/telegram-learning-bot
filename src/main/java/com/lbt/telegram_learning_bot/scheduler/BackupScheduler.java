package com.lbt.telegram_learning_bot.scheduler;

import com.lbt.telegram_learning_bot.service.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackupScheduler {

    private final BackupService backupService;

    @Scheduled(cron = "0 0 0 * * ?", zone = "Europe/Moscow")
    public void scheduledBackup() {
        log.info("Starting scheduled database backup");
        try {
            BackupService.BackupResult result = backupService.createAndUploadBackup();
            log.info("Scheduled backup completed successfully: {}", result.fileName());
            for (Map.Entry<String, Boolean> entry : result.uploadResults().entrySet()) {
                log.info("  {} upload: {}", entry.getKey(), entry.getValue() ? "SUCCESS" : "FAILED");
            }
        } catch (Exception e) {
            log.error("Scheduled backup failed", e);
        }
    }
}