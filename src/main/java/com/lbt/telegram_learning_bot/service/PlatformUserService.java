package com.lbt.telegram_learning_bot.service;

import com.lbt.telegram_learning_bot.platform.Platform;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

/**
 * Тонкий сервис-фасад для получения internalUserId из платформенного ID.
 * Используется в точках входа каждого бота (TelegramBotHandler, VkBotHandler).
 */
@Service
@RequiredArgsConstructor
public class PlatformUserService {

    private final AccountLinkService accountLinkService;

    /**
     * Преобразует платформенный ID в единый внутренний ID.
     * При первом обращении создаёт запись автоматически.
     */
    public Long resolveUserId(Platform platform, Long externalUserId) {
        return accountLinkService.resolveInternalUserId(platform, externalUserId);
    }
}
