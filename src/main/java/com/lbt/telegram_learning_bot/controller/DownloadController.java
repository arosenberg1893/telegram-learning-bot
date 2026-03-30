package com.lbt.telegram_learning_bot.controller;

import com.lbt.telegram_learning_bot.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@RestController
@RequestMapping("/api/download")
@RequiredArgsConstructor
public class DownloadController {

    private final FileStorageService fileStorageService;

    @GetMapping("/{fileId}")
    public ResponseEntity<?> downloadFile(@PathVariable String fileId) {
        Path file = fileStorageService.getFile(fileId);
        if (file == null || !file.toFile().exists()) {
            return ResponseEntity.notFound().build();
        }
        try {
            InputStreamResource resource = new InputStreamResource(new FileInputStream(file.toFile()));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName().toString().substring(fileId.length() + 1) + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (IOException e) {
            log.error("Error serving file {}", fileId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
