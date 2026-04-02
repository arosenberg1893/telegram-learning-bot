package com.lbt.telegram_learning_bot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.TelegramBot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelegramBotConfig {

    @Value("${telegram.bot.token}")
    private String token;

    @Bean
    public TelegramBot telegramBot() {
        return new TelegramBot(token);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}