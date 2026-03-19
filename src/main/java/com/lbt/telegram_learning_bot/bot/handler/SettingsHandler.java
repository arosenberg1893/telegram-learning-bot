package com.lbt.telegram_learning_bot.bot.handler;

import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.entity.UserSettings;
import com.lbt.telegram_learning_bot.repository.AdminUserRepository;
import com.lbt.telegram_learning_bot.service.NavigationService;
import com.lbt.telegram_learning_bot.service.UserProgressCleanupService;
import com.lbt.telegram_learning_bot.service.UserSessionService;
import com.lbt.telegram_learning_bot.service.UserSettingsService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
@Component
public class SettingsHandler extends BaseHandler {

    private final UserProgressCleanupService progressCleanupService;

    public SettingsHandler(TelegramBot telegramBot,
                           UserSessionService sessionService,
                           NavigationService navigationService,
                           AdminUserRepository adminUserRepository,
                           UserSettingsService userSettingsService,
                           UserProgressCleanupService progressCleanupService) {
        super(telegramBot, sessionService, navigationService, adminUserRepository, userSettingsService);
        this.progressCleanupService = progressCleanupService;
    }

    public void showSettingsMenu(Long userId, Integer messageId) {
        UserSettings settings = userSettingsService.getSettings(userId);
        String text = String.format("""
                ⚙️ **Настройки**

                🔀 Перемешивать варианты: %s
                📄 Размер страницы: %d
                ❓ Вопросов в тесте (на блок): %d
                💬 Показывать пояснения: %s
                🔔 Уведомления о новых курсах: %s
                """,
                settings.getShuffleOptions() ? "✅" : "❌",
                settings.getPageSize(),
                settings.getTestQuestionsPerBlock(),
                settings.getShowExplanations() ? "✅" : "❌",
                settings.getNotificationsEnabled() ? "✅" : "❌"
        );

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("🔀 Перемешивание").callbackData(CALLBACK_SETTINGS_SHUFFLE),
                new InlineKeyboardButton("📄 Размер страницы").callbackData(CALLBACK_SETTINGS_PAGESIZE)
        ).addRow(
                new InlineKeyboardButton("❓ Кол-во вопросов").callbackData(CALLBACK_SETTINGS_QUESTIONS),
                new InlineKeyboardButton("💬 Пояснения").callbackData(CALLBACK_SETTINGS_EXPLANATIONS)
        ).addRow(
                new InlineKeyboardButton("🔔 Уведомления").callbackData(CALLBACK_SETTINGS_NOTIFICATIONS),
                new InlineKeyboardButton("🗑️ Сброс прогресса").callbackData(CALLBACK_SETTINGS_RESET)
        ).addRow(
                new InlineKeyboardButton(BUTTON_MAIN_MENU).callbackData(CALLBACK_MAIN_MENU)
        );

        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
    }

    public void toggleShuffle(Long userId, Integer messageId) {
        userSettingsService.updateSettings(userId, settings ->
                settings.setShuffleOptions(!settings.getShuffleOptions()));
        showSettingsMenu(userId, messageId);
    }

    public void toggleExplanations(Long userId, Integer messageId) {
        userSettingsService.updateSettings(userId, settings ->
                settings.setShowExplanations(!settings.getShowExplanations()));
        showSettingsMenu(userId, messageId);
    }

    public void toggleNotifications(Long userId, Integer messageId) {
        userSettingsService.updateSettings(userId, settings ->
                settings.setNotificationsEnabled(!settings.getNotificationsEnabled()));
        showSettingsMenu(userId, messageId);
    }

    public void showPageSizeOptions(Long userId, Integer messageId) {
        String text = "Выберите количество элементов на странице:";
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("5").callbackData(CALLBACK_SETTINGS_PAGESIZE_SET + ":5"),
                new InlineKeyboardButton("10").callbackData(CALLBACK_SETTINGS_PAGESIZE_SET + ":10"),
                new InlineKeyboardButton("15").callbackData(CALLBACK_SETTINGS_PAGESIZE_SET + ":15")
        ).addRow(
                new InlineKeyboardButton("🔙 Назад").callbackData(CALLBACK_SETTINGS)
        );
        editMessage(userId, messageId, text, keyboard);
    }

    public void setPageSize(Long userId, Integer messageId, int size) {
        userSettingsService.updateSettings(userId, settings -> settings.setPageSize(size));
        showSettingsMenu(userId, messageId);
    }

    public void showQuestionsPerBlockOptions(Long userId, Integer messageId) {
        String text = "Выберите количество вопросов на блок в тестах раздела/курса:";
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("1").callbackData(CALLBACK_SETTINGS_QUESTIONS_SET + ":1"),
                new InlineKeyboardButton("2").callbackData(CALLBACK_SETTINGS_QUESTIONS_SET + ":2"),
                new InlineKeyboardButton("3").callbackData(CALLBACK_SETTINGS_QUESTIONS_SET + ":3"),
                new InlineKeyboardButton("5").callbackData(CALLBACK_SETTINGS_QUESTIONS_SET + ":5")
        ).addRow(
                new InlineKeyboardButton("🔙 Назад").callbackData(CALLBACK_SETTINGS)
        );
        editMessage(userId, messageId, text, keyboard);
    }

    public void setQuestionsPerBlock(Long userId, Integer messageId, int count) {
        userSettingsService.updateSettings(userId, settings -> settings.setTestQuestionsPerBlock(count));
        showSettingsMenu(userId, messageId);
    }

    public void confirmResetProgress(Long userId, Integer messageId) {
        String text = "⚠️ Вы уверены, что хотите **полностью сбросить весь прогресс обучения**? Все ваши ответы, ошибки и время изучения будут удалены. Это действие необратимо.";
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup(
                new InlineKeyboardButton("✅ Да, сбросить").callbackData(CALLBACK_SETTINGS_RESET_CONFIRM),
                new InlineKeyboardButton("❌ Отмена").callbackData(CALLBACK_SETTINGS)
        );
        editMessage(userId, messageId, text, keyboard);
    }

    public void resetProgress(Long userId, Integer messageId) {
        progressCleanupService.deleteAllUserData(userId);
        sessionService.updateSessionState(userId, BotState.MAIN_MENU);
        sendMessage(userId, "✅ Весь прогресс успешно сброшен.", createBackToMainKeyboard());
        // Возвращаем в главное меню, так как настройки могли быть изменены
        sendMainMenu(userId, null);
    }
}