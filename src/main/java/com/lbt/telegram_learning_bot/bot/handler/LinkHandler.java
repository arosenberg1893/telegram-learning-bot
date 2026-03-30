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

    // ─── Разрешение конфликта ─────────────────────────────────────────────────

    /**
     * Пользователь выбрал сохранить прогресс ТЕКУЩЕГО аккаунта (receiver).
     */
    public void resolveConflictKeepThis(long internalUserId, String code,
                                         Platform platform, long externalUserId,
                                         MessageSender sender) {
        Long issuerInternal = getIssuerInternalId(code);
        if (issuerInternal == null) {
            sender.sendMenu(internalUserId, MSG_LINK_INVALID_CODE, BotKeyboard.backToMain());
            return;
        }
        // keepInternal = receiver (текущий), discardInternal = issuer
        accountLinkService.resolveConflict(internalUserId, issuerInternal, code, platform, externalUserId);
        sender.sendMenu(internalUserId, MSG_LINK_SUCCESS, BotKeyboard.backToMain());
        log.info("Conflict resolved: kept receiver={}, discarded issuer={}", internalUserId, issuerInternal);
    }

    /**
     * Пользователь выбрал сохранить прогресс ДРУГОГО аккаунта (issuer).
     */
    public void resolveConflictKeepOther(long internalUserId, String code,
                                          Platform platform, long externalUserId,
                                          MessageSender sender) {
        Long issuerInternal = getIssuerInternalId(code);
        if (issuerInternal == null) {
            sender.sendMenu(internalUserId, MSG_LINK_INVALID_CODE, BotKeyboard.backToMain());
            return;
        }
        // keepInternal = issuer, discardInternal = receiver (текущий)
        accountLinkService.resolveConflict(issuerInternal, internalUserId, code, platform, externalUserId);
        sender.sendMenu(internalUserId, MSG_LINK_SUCCESS, BotKeyboard.backToMain());
        log.info("Conflict resolved: kept issuer={}, discarded receiver={}", issuerInternal, internalUserId);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void showConflictChoice(long internalUserId, String code, MessageSender sender) {
        BotKeyboard keyboard = new BotKeyboard()
                .addRow(BotButton.callback(BUTTON_KEEP_THIS_PROGRESS,
                        CALLBACK_LINK_RESOLVE_KEEP_THIS + ":" + code))
                .addRow(BotButton.callback(BUTTON_KEEP_OTHER_PROGRESS,
                        CALLBACK_LINK_RESOLVE_KEEP_OTHER + ":" + code))
                .addRow(BotButton.callback(BUTTON_CANCEL, CALLBACK_MAIN_MENU));
        sender.sendMenu(internalUserId, MSG_LINK_CONFLICT, keyboard);
    }

    /**
     * Извлекает issuerInternalUserId из активного кода.
     */
    private Long getIssuerInternalId(String code) {
        return linkCodeRepository.findByCodeAndUsedFalse(code.toUpperCase().trim())
                .map(LinkCode::getIssuerInternalUserId)
                .orElse(null);
    }
}
