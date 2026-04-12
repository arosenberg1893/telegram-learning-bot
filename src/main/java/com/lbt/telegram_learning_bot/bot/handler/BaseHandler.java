package com.lbt.telegram_learning_bot.bot.handler;

import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.repository.AdminUserRepository;
import com.lbt.telegram_learning_bot.service.MaintenanceModeService;
import com.lbt.telegram_learning_bot.service.NavigationService;
import com.lbt.telegram_learning_bot.service.UserSessionService;
import com.lbt.telegram_learning_bot.service.UserSettingsService;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.lbt.telegram_learning_bot.util.Constants.*;

@Slf4j
public abstract class BaseHandler {

    protected final MessageSender messageSender;
    protected final UserSessionService sessionService;
    protected final NavigationService navigationService;
    protected final AdminUserRepository adminUserRepository;
    protected final UserSettingsService userSettingsService;
    protected final MaintenanceModeService maintenanceModeService;

    public BaseHandler(MessageSender messageSender,
                       UserSessionService sessionService,
                       NavigationService navigationService,
                       AdminUserRepository adminUserRepository,
                       UserSettingsService userSettingsService,
                       MaintenanceModeService maintenanceModeService) {
        this.messageSender = messageSender;
        this.sessionService = sessionService;
        this.navigationService = navigationService;
        this.adminUserRepository = adminUserRepository;
        this.userSettingsService = userSettingsService;
        this.maintenanceModeService = maintenanceModeService;
    }

    protected long getEffectiveUserId(long userId) {
        UserContext ctx = sessionService.getCurrentContext(userId);
        Long platformId = ctx.getCurrentPlatformUserId();
        long effective = platformId != null ? platformId : userId;
        log.debug("getEffectiveUserId: userId={}, platformId={}, effective={}", userId, platformId, effective);
        return effective;
    }

    /**
     * Проверяет, заблокирован ли пользователь из-за режима обслуживания.
     * Администраторы не блокируются.
     */
    protected boolean isMaintenanceBlocked(Long userId) {
        return maintenanceModeService.isMaintenance() && !isAdmin(userId);
    }

    /**
     * Отправляет сообщение о техническом обслуживании.
     */
    protected void sendMaintenanceMessage(long effectiveUserId) {
        String maintenanceText = "🔧 Бот временно недоступен. Ведутся технические работы. Пожалуйста, зайдите позже.";
        messageSender.sendText(effectiveUserId, maintenanceText);
    }

    // ================== Методы отправки сообщений с проверкой режима обслуживания ==================

    protected void sendMessage(Long userId, String text) {
        if (isMaintenanceBlocked(userId)) {
            sendMaintenanceMessage(getEffectiveUserId(userId));
            return;
        }
        messageSender.sendText(getEffectiveUserId(userId), text);
    }

    protected void sendMessage(Long userId, String text, BotKeyboard keyboard) {
        if (isMaintenanceBlocked(userId)) {
            sendMaintenanceMessage(getEffectiveUserId(userId));
            return;
        }
        messageSender.sendMenu(getEffectiveUserId(userId), text, keyboard);
    }

    protected void editMessage(Long userId, Integer messageId, String text) {
        if (isMaintenanceBlocked(userId)) return;
        messageSender.editMenu(getEffectiveUserId(userId), messageId, text, null);
    }

    protected void editMessage(Long userId, Integer messageId, String text, BotKeyboard keyboard) {
        if (isMaintenanceBlocked(userId)) return;
        messageSender.editMenu(getEffectiveUserId(userId), messageId, text, keyboard);
    }

    protected void deleteMessage(Long userId, Integer messageId) {
        if (isMaintenanceBlocked(userId)) return;
        if (messageId != null) {
            messageSender.deleteMessage(getEffectiveUserId(userId), messageId);
        }
    }

    protected void sendMediaGroup(Long userId, List<?> images) {
        if (isMaintenanceBlocked(userId)) return;
        messageSender.sendImageGroup(getEffectiveUserId(userId), images);
    }

    protected void clearMediaMessages(Long userId) {
        // Очистка медиа-сообщений теперь управляется на стороне MessageSender,
        // но для совместимости оставим пустой метод.
        // В текущей реализации MessageSender не хранит списки, поэтому этот метод можно оставить пустым,
        // а очистку делать через deleteMessage.
    }

    protected Integer sendProgressMessage(Long userId) {
        if (isMaintenanceBlocked(userId)) return null;
        return messageSender.sendProgress(getEffectiveUserId(userId));
    }

    // ================== Вспомогательные методы, не связанные с отправкой ==================

    /**
     * Отправляет/редактирует главное меню пользователя.
     * Конкретные платформы (Telegram, VK) переопределяют этот метод,
     * добавляя кнопки администратора в нужном формате.
     */
    protected void sendMainMenu(Long userId, Integer messageId) {
        if (isMaintenanceBlocked(userId)) {
            sendMaintenanceMessage(getEffectiveUserId(userId));
            return;
        }
        BotKeyboard keyboard = buildBaseMainMenuKeyboard(userId);
        if (messageId != null) {
            editMessage(userId, messageId, MSG_MAIN_MENU, keyboard);
        } else {
            sendMessage(userId, MSG_MAIN_MENU, keyboard);
        }
    }

    /** Строит клавиатуру главного меню без кнопок администратора. */
    protected BotKeyboard buildBaseMainMenuKeyboard(Long userId) {
        return new BotKeyboard()
                .addRow(BotButton.callback(BUTTON_MY_COURSES, CALLBACK_MY_COURSES),
                        BotButton.callback(BUTTON_ALL_COURSES, CALLBACK_ALL_COURSES))
                .addRow(BotButton.callback(BUTTON_SEARCH, CALLBACK_SEARCH_COURSES),
                        BotButton.callback(BUTTON_STATISTICS, CALLBACK_STATISTICS))
                .addRow(BotButton.callback(BUTTON_MISTAKES, CALLBACK_MY_MISTAKES))
                .addRow(BotButton.callback("⚙️ Настройки", CALLBACK_SETTINGS));
    }

    protected boolean isAdmin(Long userId) {
        return adminUserRepository.existsByUserId(userId);
    }

    // ================== Клавиатуры ==================

    protected BotKeyboard createRetryOrCancelKeyboard() {
        return BotKeyboard.rows(
                new BotButton[]{BotButton.callback(BUTTON_RETRY, CALLBACK_RETRY)},
                new BotButton[]{BotButton.callback(BUTTON_CANCEL, CALLBACK_MAIN_MENU)}
        );
    }

    protected BotKeyboard createCancelKeyboard() {
        return BotKeyboard.of(BotButton.callback(BUTTON_CANCEL, CALLBACK_MAIN_MENU));
    }

    protected BotKeyboard createBackToMainKeyboard() {
        return BotKeyboard.backToMain();
    }

    protected BotKeyboard createSearchNotFoundKeyboard() {
        return BotKeyboard.rows(
                new BotButton[]{BotButton.callback(BUTTON_RETRY_SEARCH, CALLBACK_SEARCH_COURSES)},
                new BotButton[]{BotButton.callback(BUTTON_MAIN_MENU, CALLBACK_MAIN_MENU)}
        );
    }

    protected BotKeyboard createCancelKeyboardWithBackToTopics() {
        return BotKeyboard.of(BotButton.callback(BUTTON_CANCEL, CALLBACK_BACK_TO_TOPICS));
    }

    protected BotKeyboard createStatisticsKeyboard() {
        return BotKeyboard.rows(
                new BotButton[]{BotButton.callback(BUTTON_EXPORT_PDF, CALLBACK_EXPORT_PDF)},
                new BotButton[]{BotButton.callback(BUTTON_MAIN_MENU, CALLBACK_MAIN_MENU)}
        );
    }

    protected BotKeyboard createAdminCancelKeyboardWithBackToTopics() {
        return BotKeyboard.of(BotButton.callback(BUTTON_CANCEL, CALLBACK_ADMIN_BACK_TO_TOPICS));
    }

    // ================== Форматирование ==================

    protected String formatStudyTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return String.format(FORMAT_STUDY_TIME_HOURS, hours, minutes);
        } else {
            return String.format(FORMAT_STUDY_TIME_MINUTES, minutes);
        }
    }

    protected String formatLastAccessed(Instant instant) {
        if (instant == null) return "никогда";

        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        LocalDateTime now = LocalDateTime.now();

        long days = ChronoUnit.DAYS.between(dateTime.toLocalDate(), now.toLocalDate());

        if (days == 0) {
            return "сегодня";
        } else if (days == 1) {
            return "вчера";
        } else if (days < 7) {
            return days + " дня назад";
        } else if (days < 30) {
            long weeks = days / 7;
            return weeks + " " + (weeks == 1 ? "неделю" : "недели") + " назад";
        } else if (days < 365) {
            long months = days / 30;
            return months + " " + (months == 1 ? "месяц" : "месяца") + " назад";
        } else {
            long years = days / 365;
            return years + " " + (years == 1 ? "год" : "года") + " назад";
        }
    }
}