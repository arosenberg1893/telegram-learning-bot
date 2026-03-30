package com.lbt.telegram_learning_bot.service;

import com.lbt.telegram_learning_bot.entity.UserSettings;
import com.lbt.telegram_learning_bot.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.function.Consumer;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository settingsRepository;

    @Transactional
    public UserSettings getSettings(Long userId) {
        return settingsRepository.findById(userId)
                .orElseGet(() -> {
                    UserSettings settings = new UserSettings();
                    settings.setUserId(userId);
                    return settingsRepository.save(settings);
                });
    }

    @Transactional
    public void updateSettings(Long userId, Consumer<UserSettings> updater) {
        UserSettings settings = getSettings(userId);
        updater.accept(settings);
        settingsRepository.save(settings);
    }
}