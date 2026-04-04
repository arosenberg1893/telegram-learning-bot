package com.lbt.telegram_learning_bot.bot.handler;

import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.entity.UserSettings;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.platform.Platform;
import com.lbt.telegram_learning_bot.repository.AdminUserRepository;
import com.lbt.telegram_learning_bot.service.NavigationService;
import com.lbt.telegram_learning_bot.service.UserProgressCleanupService;
import com.lbt.telegram_learning_bot.service.UserSessionService;
import com.lbt.telegram_learning_bot.service.UserSettingsService;
import lombok.extern.slf4j.Slf4j;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
public class SettingsHandler extends BaseHandler {

    private final UserProgressCleanupService progressCleanupService;

    public SettingsHandler(MessageSender messageSender,
                           UserSessionService sessionService,
                           NavigationService navigationService,
                           AdminUserRepository adminUserRepository,
                           UserSettingsService userSettingsService,
                           UserProgressCleanupService progressCleanupService) {
        super(messageSender, sessionService, navigationService, adminUserRepository, userSettingsService);
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
                        📄 Вопросы в PDF: %s
                        """,
                settings.getShuffleOptions() ? "✅" : "❌",
                settings.getPageSize(),
                settings.getTestQuestionsPerBlock(),
                settings.getShowExplanations() ? "✅" : "❌",
                settings.getNotificationsEnabled() ? "✅" : "❌",
                settings.getIncludeQuestionsInPdf() ? "✅" : "❌"
        );

        BotKeyboard keyboard = new BotKeyboard()
                .addRow(BotButton.callback("🔀 Перемешивание", CALLBACK_SETTINGS_SHUFFLE));

        // Для VK скрываем опцию изменения размера страницы
        if (messageSender.getPlatform() != Platform.VK) {
            keyboard.addRow(BotButton.callback("📄 Размер страницы", CALLBACK_SETTINGS_PAGESIZE));
        }

        keyboard.addRow(BotButton.callback("❓ Кол-во вопросов", CALLBACK_SETTINGS_QUESTIONS),
                        BotButton.callback("💬 Пояснения", CALLBACK_SETTINGS_EXPLANATIONS))
                .addRow(BotButton.callback("🔔 Уведомления", CALLBACK_SETTINGS_NOTIFICATIONS),
                        BotButton.callback("🗑️ Сброс прогресса", CALLBACK_SETTINGS_RESET))
                .addRow(BotButton.callback("📄 Вопросы в PDF", CALLBACK_SETTINGS_PDF_QUESTIONS))
                .addRow(BotButton.callback("🔗 Привязать аккаунт", CALLBACK_LINK_GENERATE))
                .addRow(BotButton.callback(BUTTON_MAIN_MENU, CALLBACK_MAIN_MENU));

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

    public void togglePdfQuestions(Long userId, Integer messageId) {
        userSettingsService.updateSettings(userId, settings ->
                settings.setIncludeQuestionsInPdf(!settings.getIncludeQuestionsInPdf()));
        showSettingsMenu(userId, messageId);
    }

    public void showPageSizeOptions(Long userId, Integer messageId) {
        if (messageSender.getPlatform() == Platform.VK) {
            sendMessage(userId, "❌ Изменение размера страницы недоступно в VK.", createBackToMainKeyboard());
            return;
        }
        String text = "Выберите количество элементов на странице:";
        BotKeyboard keyboard = new BotKeyboard()
                .addRow(BotButton.callback("5", CALLBACK_SETTINGS_PAGESIZE_SET + ":5"),
                        BotButton.callback("10", CALLBACK_SETTINGS_PAGESIZE_SET + ":10"),
                        BotButton.callback("15", CALLBACK_SETTINGS_PAGESIZE_SET + ":15"))
                .addRow(BotButton.callback("✏️ Другое", CALLBACK_SETTINGS_PAGESIZE_OTHER))
                .addRow(BotButton.callback("🔙 Назад", CALLBACK_SETTINGS));
        editMessage(userId, messageId, text, keyboard);
    }

    public void promptPageSizeInput(Long userId, Integer messageId) {
        String text = "Введите число от 1 до 50 (количество элементов на странице):";
        BotKeyboard keyboard = createCancelKeyboard();
        if (messageId != null) {
            editMessage(userId, messageId, text, keyboard);
        } else {
            sendMessage(userId, text, keyboard);
        }
        sessionService.updateSessionState(userId, BotState.AWAITING_PAGE_SIZE_INPUT);
    }

    public void handlePageSizeInput(Long userId, String input, Integer messageId) {
        try {
            int size = Integer.parseInt(input.trim());
            if (size < 1 || size > 50) {
                sendMessage(userId, "❌ Число должно быть от 1 до 50. Попробуйте ещё раз.", createCancelKeyboard());
                return;
            }
            userSettingsService.updateSettings(userId, settings -> settings.setPageSize(size));
            sessionService.updateSessionState(userId, BotState.MAIN_MENU);
            showSettingsMenu(userId, null);
            if (messageId != null) {
                deleteMessage(userId, messageId);
            }
        } catch (NumberFormatException e) {
            sendMessage(userId, "❌ Пожалуйста, введите целое число.", createCancelKeyboard());
        }
    }

    public void setPageSize(Long userId, Integer messageId, int size) {
        if (messageSender.getPlatform() == Platform.VK) {
            sendMessage(userId, "❌ Изменение размера страницы недоступно в VK.", createBackToMainKeyboard());
            return;
        }
        userSettingsService.updateSettings(userId, settings -> settings.setPageSize(size));
        showSettingsMenu(userId, messageId);
    }

    public void showQuestionsPerBlockOptions(Long userId, Integer messageId) {
        String text = "Выберите количество вопросов на блок в тестах раздела/курса:";
        BotKeyboard keyboard = new BotKeyboard()
                .addRow(BotButton.callback("1", CALLBACK_SETTINGS_QUESTIONS_SET + ":1"),
                        BotButton.callback("2", CALLBACK_SETTINGS_QUESTIONS_SET + ":2"),
                        BotButton.callback("3", CALLBACK_SETTINGS_QUESTIONS_SET + ":3"),
                        BotButton.callback("5", CALLBACK_SETTINGS_QUESTIONS_SET + ":5"))
                .addRow(BotButton.callback("🔙 Назад", CALLBACK_SETTINGS));
        editMessage(userId, messageId, text, keyboard);
    }

    public void setQuestionsPerBlock(Long userId, Integer messageId, int count) {
        userSettingsService.updateSettings(userId, settings -> settings.setTestQuestionsPerBlock(count));
        showSettingsMenu(userId, messageId);
    }

    public void confirmResetProgress(Long userId, Integer messageId) {
        String text = "⚠️ Вы уверены, что хотите **полностью сбросить весь прогресс обучения**? Все ваши ответы, ошибки и время изучения будут удалены. Это действие необратимо.";
        BotKeyboard keyboard = new BotKeyboard()
                .addRow(BotButton.callback("✅ Да, сбросить", CALLBACK_SETTINGS_RESET_CONFIRM),
                        BotButton.callback("❌ Отмена", CALLBACK_SETTINGS));
        editMessage(userId, messageId, text, keyboard);
    }

    public void resetProgress(Long userId, Integer messageId) {
        progressCleanupService.deleteAllUserData(userId);
        sessionService.updateSessionState(userId, BotState.MAIN_MENU);
        sendMessage(userId, "✅ Весь прогресс успешно сброшен.", createBackToMainKeyboard());
        sendMainMenu(userId, null);
    }
}