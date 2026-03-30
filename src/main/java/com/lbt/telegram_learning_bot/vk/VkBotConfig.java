package com.lbt.telegram_learning_bot.vk;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "vk.bot.enabled", havingValue = "true")
public class VkBotConfig {
    // Конфигурация теперь не нужна, все настройки в VkHttpClient
}