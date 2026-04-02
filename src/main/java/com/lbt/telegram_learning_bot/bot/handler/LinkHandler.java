package com.lbt.telegram_learning_bot.bot.handler;

import com.lbt.telegram_learning_bot.entity.LinkCode;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;
import com.lbt.telegram_learning_bot.platform.MessageSender;
import com.lbt.telegram_learning_bot.platform.Platform;
import com.lbt.telegram_learning_bot.repository.LinkCodeRepository;
import com.lbt.telegram_learning_bot.service.AccountLinkService;
import com.lbt.telegram_learning_bot.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static com.lbt.telegram_learning_bot.util.Constants.*;

/**
 * Платформо-независимый обработчик команды /link и связанных callback-ов.
 *
 * Принимает {@link MessageSender} конкретной платформы, поэтому не зависит
 * ни от Telegram API, ни от VK API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkHandler {

    private final AccountLinkService accountLinkService;
    private final LinkCodeRepository linkCodeRepository;
    private final UserSessionService sessionService;

    // ─── Генерация кода ───────────────────────────────────────────────────────

    /**
     * Генерирует код привязки и отправляет его пользователю.
     * Вызывается командой {@code /link} без аргументов.
     */
    public void generateCode(long internalUserId, Platform platform, MessageSender sender) {
        List<Platform> linked = accountLinkService.getLinkedPlatforms(internalUserId);
        if (linked.size() > 1) {
            String platformsList = linked.stream()
                    .map(p -> p == Platform.TELEGRAM ? "Telegram" : "ВКонтакте")
                    .collect(Collectors.joining(", "));
            sender.sendMenu(internalUserId,
                    String.format(MSG_LINK_STATUS_LINKED, platformsList),
                    BotKeyboard.backToMain());
            return;
        }

        String code = accountLinkService.generateLinkCode(internalUserId, platform);
        String msg = String.format(MSG_LINK_CODE_GENERATED, code, code, LinkCode.EXPIRY_MINUTES);
        sender.sendMenu(internalUserId, msg, BotKeyboard.backToMain());
    }

    // ─── Применение кода ─────────────────────────────────────────────────────

    /**
     * Применяет код привязки. При конфликте прогресса показывает выбор.
     */
    public void applyCode(long internalUserId, String code, Platform platform,
                          long externalUserId, MessageSender sender) {
        AccountLinkService.LinkResult result =
                accountLinkService.applyLinkCode(code, platform, externalUserId);

        switch (result) {
            case LINKED_AUTO_MERGE ->
                    sender.sendMenu(internalUserId, MSG_LINK_SUCCESS, BotKeyboard.backToMain());
            case CONFLICT_NEEDS_CHOICE ->
                    showConflictChoice(internalUserId, code, sender);
            case ALREADY_LINKED ->
                    sender.sendMenu(internalUserId, MSG_LINK_ALREADY_LINKED, BotKeyboard.backToMain());
            case INVALID_CODE ->
                    sender.sendMenu(internalUserId, MSG_LINK_INVALID_CODE, BotKeyboard.backToMain());
            case SAME_PLATFORM ->
                    sender.sendMenu(internalUserId, MSG_LINK_SAME_PLATFORM, BotKeyboard.backToMain());
            case ALREADY_LINKED_ELSEWHERE ->
                    sender.sendMenu(internalUserId, MSG_LINK_ALREADY_ELSEWHERE, BotKeyboard.backToMain());
        }
    }

    // ─── Разрешение конфликта – три варианта ──────────────────────────────────

    /**
     * Пользователь выбрал сохранить прогресс Telegram (текущий аккаунт).
     */
    public void resolveConflictKeepTelegram(long internalUserId, String code,
                                            Platform platform, long externalUserId,
                                            MessageSender sender) {
        Long issuerInternal = getIssuerInternalId(code);
        if (issuerInternal == null) {
            sender.sendMenu(internalUserId, MSG_LINK_INVALID_CODE, BotKeyboard.backToMain());
            return;
        }
        // internalUserId – это Telegram (receiver), issuerInternal – VK
        accountLinkService.resolveConflictKeepTelegram(internalUserId, code, platform, externalUserId);
        sender.sendMenu(internalUserId, MSG_LINK_SUCCESS, BotKeyboard.backToMain());
        log.info("Conflict resolved: kept Telegram user {}, discarded VK user {}", internalUserId, issuerInternal);
    }

    /**
     * Пользователь выбрал сохранить прогресс ВКонтакте (другой аккаунт).
     */
    public void resolveConflictKeepVk(long internalUserId, String code,
                                      Platform platform, long externalUserId,
                                      MessageSender sender) {
        Long issuerInternal = getIssuerInternalId(code);
        if (issuerInternal == null) {
            sender.sendMenu(internalUserId, MSG_LINK_INVALID_CODE, BotKeyboard.backToMain());
            return;
        }
        // internalUserId – это Telegram (receiver), issuerInternal – VK
        accountLinkService.resolveConflictKeepVk(internalUserId, code, platform, externalUserId);
        sender.sendMenu(internalUserId, MSG_LINK_SUCCESS, BotKeyboard.backToMain());
        log.info("Conflict resolved: kept VK user {}, discarded Telegram user {}", issuerInternal, internalUserId);
    }

    /**
     * Пользователь выбрал объединить прогресс. Показываем выбор настроек.
     */
    public void resolveConflictMerge(long internalUserId, String code,
                                     Platform platform, long externalUserId,
                                     MessageSender sender) {
        Long issuerInternal = getIssuerInternalId(code);
        if (issuerInternal == null) {
            sender.sendMenu(internalUserId, MSG_LINK_INVALID_CODE, BotKeyboard.backToMain());
            return;
        }
        // Показываем выбор: чьи настройки использовать
        showMergeSettingsChoice(internalUserId, issuerInternal, code, platform, externalUserId, sender);
    }

    // ─── Выбор настроек при слиянии ──────────────────────────────────────────

    private void showMergeSettingsChoice(long receiverInternal, long issuerInternal,
                                         String code, Platform platform, long externalUserId,
                                         MessageSender sender) {
        String text = "🔧 При объединении прогресса нужно выбрать, *чьи настройки использовать*:\n" +
                "• Настройки Telegram (размер страницы, перемешивание и т.д.)\n" +
                "• Настройки ВКонтакте";
        BotKeyboard keyboard = new BotKeyboard()
                .addRow(BotButton.callback("Настройки Telegram",
                        CALLBACK_LINK_MERGE_SETTINGS_TG + ":" + code))
                .addRow(BotButton.callback("Настройки ВКонтакте",
                        CALLBACK_LINK_MERGE_SETTINGS_VK + ":" + code))
                .addRow(BotButton.callback(BUTTON_CANCEL, CALLBACK_MAIN_MENU));
        sender.sendMenu(receiverInternal, text, keyboard);
    }

    /**
     * Завершаем слияние с выбором настроек от Telegram.
     */
    public void finalizeMergeWithTelegramSettings(long internalUserId, String code,
                                                  Platform platform, long externalUserId,
                                                  MessageSender sender) {
        Long issuerInternal = getIssuerInternalId(code);
        if (issuerInternal == null) {
            sender.sendMenu(internalUserId, MSG_LINK_INVALID_CODE, BotKeyboard.backToMain());
            return;
        }
        // internalUserId – Telegram, issuerInternal – VK
        // Настройки берём с Telegram (internalUserId)
        accountLinkService.resolveConflictMerge(internalUserId, issuerInternal, internalUserId,
                code, platform, externalUserId);
        sender.sendMenu(internalUserId, MSG_LINK_SUCCESS, BotKeyboard.backToMain());
        log.info("Merge completed: kept both progresses, settings from Telegram user {}", internalUserId);
    }

    /**
     * Завершаем слияние с выбором настроек от VK.
     */
    public void finalizeMergeWithVkSettings(long internalUserId, String code,
                                            Platform platform, long externalUserId,
                                            MessageSender sender) {
        Long issuerInternal = getIssuerInternalId(code);
        if (issuerInternal == null) {
            sender.sendMenu(internalUserId, MSG_LINK_INVALID_CODE, BotKeyboard.backToMain());
            return;
        }
        // Настройки берём с VK (issuerInternal)
        accountLinkService.resolveConflictMerge(internalUserId, issuerInternal, issuerInternal,
                code, platform, externalUserId);
        sender.sendMenu(internalUserId, MSG_LINK_SUCCESS, BotKeyboard.backToMain());
        log.info("Merge completed: kept both progresses, settings from VK user {}", issuerInternal);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void showConflictChoice(long internalUserId, String code, MessageSender sender) {
        BotKeyboard keyboard = new BotKeyboard()
                .addRow(BotButton.callback(BUTTON_KEEP_TELEGRAM_PROGRESS,
                        CALLBACK_LINK_KEEP_TELEGRAM + ":" + code))
                .addRow(BotButton.callback(BUTTON_KEEP_VK_PROGRESS,
                        CALLBACK_LINK_KEEP_VK + ":" + code))
                .addRow(BotButton.callback(BUTTON_MERGE_PROGRESS,
                        CALLBACK_LINK_MERGE + ":" + code))
                .addRow(BotButton.callback(BUTTON_CANCEL, CALLBACK_MAIN_MENU));
        sender.sendMenu(internalUserId, MSG_LINK_CONFLICT, keyboard);
    }

    private Long getIssuerInternalId(String code) {
        return linkCodeRepository.findByCodeAndUsedFalse(code.toUpperCase().trim())
                .map(LinkCode::getIssuerInternalUserId)
                .orElse(null);
    }
}