package com.lbt.telegram_learning_bot.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Запускает Long Polling для Telegram Bot API.
 *
 * <p>Каждый апдейт обрабатывается в отдельной задаче виртуального потока
 * (или обычного пула, если виртуальные потоки недоступны),
 * что позволяет одновременно обслуживать нескольких пользователей без блокировки.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotPoller {

    private final TelegramBot bot;
    private final TelegramBotHandler handler;

    /**
     * Пул для параллельной обработки апдейтов.
     * Размер ограничен, чтобы не перегружать БД при внезапных пиках.
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors() * 2));

    @PostConstruct
    public void init() {
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                executor.submit(() -> {
                    try {
                        handler.handle(update);
                    } catch (Exception e) {
                        log.error("Unhandled exception processing update {}", update.updateId(), e);
                    }
                });
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        }, e -> log.error("Error in Telegram updates listener", e));
        log.info("Telegram bot polling started (executor threads: {})",
                Runtime.getRuntime().availableProcessors() * 2);
    }

    @PreDestroy
    public void destroy() {
        bot.removeGetUpdatesListener();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                log.warn("Executor did not terminate gracefully, forcing shutdown");
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Telegram bot polling stopped");
    }
}
