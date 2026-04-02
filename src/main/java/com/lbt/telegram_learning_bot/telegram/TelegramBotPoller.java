package com.lbt.telegram_learning_bot.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotPoller {

    private final TelegramBot bot;
    private final TelegramBotHandler handler;

    @PostConstruct
    public void init() {
        bot.setUpdatesListener(updates -> {
            for (Update update : updates) {
                try {
                    handler.handle(update);
                } catch (Exception e) {
                    log.error("Unhandled exception processing update {}", update.updateId(), e);
                }
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        }, e -> log.error("Error in Telegram updates listener", e));
        log.info("Telegram bot polling started");
    }

    @PreDestroy
    public void destroy() {
        bot.removeGetUpdatesListener();
        log.info("Telegram bot polling stopped");
    }
}
