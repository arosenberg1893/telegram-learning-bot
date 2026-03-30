package com.lbt.telegram_learning_bot.vk;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@ConditionalOnProperty(name = "vk.bot.enabled", havingValue = "true")
public class VkBotPoller {

    private final VkHttpClient vkHttpClient;
    private final VkUpdateHandler updateHandler;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vk-poller");
        t.setDaemon(true);
        return t;
    });

    public VkBotPoller(VkHttpClient vkHttpClient, VkUpdateHandler updateHandler) {
        this.vkHttpClient = vkHttpClient;
        this.updateHandler = updateHandler;
    }

    @PostConstruct
    public void start() {
        running.set(true);
        executor.submit(this::pollLoop);
        log.info("VK bot polling started");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdownNow();
        log.info("VK bot polling stopped");
    }

    private void pollLoop() {
        try {
            Map<String, String> server = vkHttpClient.getLongPollServer();
            String key = server.get("key");
            String serverUrl = server.get("server");
            String ts = server.get("ts");

            while (running.get()) {
                try {
                    JsonNode response = vkHttpClient.getLongPollEvents(serverUrl, key, ts);
                    if (response == null) {
                        Thread.sleep(3000);
                        continue;
                    }
                    if (response.has("failed")) {
                        int failed = response.get("failed").asInt();
                        if (failed == 1) {
                            ts = response.get("ts").asText();
                        } else {
                            server = vkHttpClient.getLongPollServer();
                            key = server.get("key");
                            serverUrl = server.get("server");
                            ts = server.get("ts");
                        }
                        continue;
                    }
                    ts = response.get("ts").asText();
                    if (response.has("updates")) {
                        for (JsonNode update : response.get("updates")) {
                            updateHandler.handle(update);
                        }
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        log.error("VK poll error: {}", e.getMessage(), e);
                        Thread.sleep(3000);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Fatal error in VK poller", e);
        }
    }
}