package com.lbt.telegram_learning_bot.service;

import com.lbt.telegram_learning_bot.entity.LinkCode;
import com.lbt.telegram_learning_bot.entity.LinkedAccount;
import com.lbt.telegram_learning_bot.platform.Platform;
import com.lbt.telegram_learning_bot.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

/**
 * Сервис управления привязкой аккаунтов между платформами.
 *
 * <p>Схема работы:
 * <ol>
 *   <li>Пользователь вводит /link в боте A → {@link #generateLinkCode} возвращает 6-значный код.</li>
 *   <li>Пользователь вводит /link CODE в боте B → {@link #applyLinkCode} объединяет аккаунты.</li>
 *   <li>При объединении пользователь выбирает, чей прогресс сохранить
 *       (если конфликт), либо прогресс сливается автоматически если у одного из аккаунтов его нет.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLinkService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // без I,O,0,1

    private final LinkedAccountRepository linkedAccountRepository;
    private final LinkCodeRepository linkCodeRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserMistakeRepository userMistakeRepository;
    private final UserStudyTimeRepository userStudyTimeRepository;
    private final UserTestResultRepository userTestResultRepository;
    private final UserSessionRepository userSessionRepository;
    private final UserSettingsRepository userSettingsRepository;

    // ─── Регистрация/поиск аккаунтов ─────────────────────────────────────────

    /**
     * Возвращает internalUserId для данного платформенного пользователя.
     * Если аккаунт встречается впервые — создаёт новую запись,
     * используя externalUserId как internalUserId (первая платформа = «мастер»).
     */
    @Transactional
    public Long resolveInternalUserId(Platform platform, Long externalUserId) {
        return linkedAccountRepository
                .findByPlatformAndExternalUserId(platform, externalUserId)
                .map(LinkedAccount::getInternalUserId)
                .orElseGet(() -> {
                    LinkedAccount account = new LinkedAccount(externalUserId, platform, externalUserId);
                    linkedAccountRepository.save(account);
                    log.info("New user registered: platform={}, externalId={}, internalId={}",
                            platform, externalUserId, externalUserId);
                    return externalUserId;
                });
    }

    /**
     * Проверяет, привязаны ли у пользователя обе платформы.
     */
    public boolean isLinkedToBothPlatforms(Long internalUserId) {
        List<LinkedAccount> accounts = linkedAccountRepository.findByInternalUserId(internalUserId);
        boolean hasTg = accounts.stream().anyMatch(a -> a.getPlatform() == Platform.TELEGRAM);
        boolean hasVk = accounts.stream().anyMatch(a -> a.getPlatform() == Platform.VK);
        return hasTg && hasVk;
    }

    /**
     * Возвращает список платформ, привязанных к аккаунту.
     */
    public List<Platform> getLinkedPlatforms(Long internalUserId) {
        return linkedAccountRepository.findByInternalUserId(internalUserId)
                .stream().map(LinkedAccount::getPlatform).toList();
    }

    // ─── Генерация кода ───────────────────────────────────────────────────────

    /**
     * Генерирует временный код для привязки аккаунта.
     * Старые не использованные коды этого пользователя аннулируются.
     */
    @Transactional
    public String generateLinkCode(Long internalUserId, Platform issuerPlatform) {
        // Аннулируем старые коды пользователя
        linkCodeRepository.deleteExpiredBefore(Instant.now());

        String code = generateUniqueCode();

        LinkCode linkCode = new LinkCode();
        linkCode.setCode(code);
        linkCode.setIssuerInternalUserId(internalUserId);
        linkCode.setIssuerPlatform(issuerPlatform);
        linkCode.setCreatedAt(Instant.now());
        linkCode.setExpiresAt(Instant.now().plus(LinkCode.EXPIRY_MINUTES, ChronoUnit.MINUTES));
        linkCodeRepository.save(linkCode);

        log.info("Link code generated: user={}, platform={}, code={}", internalUserId, issuerPlatform, code);
        return code;
    }

    // ─── Применение кода ─────────────────────────────────────────────────────

    public enum LinkResult {
        /** Аккаунты успешно объединены, прогресс слит (один из них был пустым). */
        LINKED_AUTO_MERGE,
        /** Обнаружен конфликт прогресса — требуется выбор пользователя. */
        CONFLICT_NEEDS_CHOICE,
        /** Эти аккаунты уже связаны. */
        ALREADY_LINKED,
        /** Код не найден или истёк. */
        INVALID_CODE,
        /** Попытка привязать аккаунт той же платформы. */
        SAME_PLATFORM,
        /** Второй аккаунт уже привязан к другому мастер-аккаунту. */
        ALREADY_LINKED_ELSEWHERE
    }

    /**
     * Применяет код связывания.
     *
     * @param code             код, введённый пользователем
     * @param receiverPlatform платформа, где пользователь вводит код
     * @param receiverExternal externalUserId пользователя, который вводит код
     * @return результат операции
     */
    @Transactional
    public LinkResult applyLinkCode(String code,
                                    Platform receiverPlatform,
                                    Long receiverExternal) {
        Optional<LinkCode> codeOpt = linkCodeRepository.findByCodeAndUsedFalse(code.toUpperCase().trim());
        if (codeOpt.isEmpty()) return LinkResult.INVALID_CODE;

        LinkCode linkCode = codeOpt.get();
        if (linkCode.isExpired()) {
            linkCodeRepository.delete(linkCode);
            return LinkResult.INVALID_CODE;
        }

        if (linkCode.getIssuerPlatform() == receiverPlatform) {
            return LinkResult.SAME_PLATFORM;
        }

        Long issuerInternal = linkCode.getIssuerInternalUserId();
        Long receiverInternal = resolveInternalUserId(receiverPlatform, receiverExternal);

        if (issuerInternal.equals(receiverInternal)) {
            return LinkResult.ALREADY_LINKED;
        }

        // Проверяем, не привязан ли receiver к кому-то другому
        List<LinkedAccount> receiverAccounts = linkedAccountRepository.findByInternalUserId(receiverInternal);
        if (receiverAccounts.size() > 1) {
            return LinkResult.ALREADY_LINKED_ELSEWHERE;
        }

        // Проверяем конфликт прогресса

        // Приоритет: Telegram всегда мастер
        boolean isReceiverTelegram = receiverPlatform == Platform.TELEGRAM;
        boolean isIssuerTelegram = linkCode.getIssuerPlatform() == Platform.TELEGRAM;
        Long masterInternal;
        Long slaveInternal;
        if (isReceiverTelegram) {
            masterInternal = receiverInternal;
            slaveInternal = issuerInternal;
        } else if (isIssuerTelegram) {
            masterInternal = issuerInternal;
            slaveInternal = receiverInternal;
        } else {
            boolean issuerHasProgress = userProgressRepository.existsByUserId(issuerInternal);
            boolean receiverHasProgress = userProgressRepository.existsByUserId(receiverInternal);
            if (issuerHasProgress && receiverHasProgress) {
                return LinkResult.CONFLICT_NEEDS_CHOICE;
            }
            masterInternal = issuerHasProgress ? issuerInternal : receiverInternal;
            slaveInternal = masterInternal.equals(issuerInternal) ? receiverInternal : issuerInternal;
        }
        mergeAccounts(masterInternal, slaveInternal, linkCode, receiverPlatform, receiverExternal);
        return LinkResult.LINKED_AUTO_MERGE;

    }

    /**
     * Завершает объединение при конфликте прогресса, когда пользователь выбрал мастер-аккаунт.
     *
     * @param keepInternal   internalUserId, чей прогресс сохранить
     * @param discardInternal internalUserId, чей прогресс удалить
     * @param code            исходный код связывания
     * @param receiverPlatform платформа получателя кода
     * @param receiverExternal externalUserId получателя
     */
    @Transactional
    public void resolveConflict(Long keepInternal, Long discardInternal,
                                String code, Platform receiverPlatform, Long receiverExternal) {
        Optional<LinkCode> codeOpt = linkCodeRepository.findByCodeAndUsedFalse(code.toUpperCase().trim());
        if (codeOpt.isEmpty()) {
            log.warn("resolveConflict: code not found or already used: {}", code);
            return;
        }
        LinkCode linkCode = codeOpt.get();
        mergeAccounts(keepInternal, discardInternal, linkCode, receiverPlatform, receiverExternal);
    }

    // ─── Внутренние методы ────────────────────────────────────────────────────

    /**
     * Объединяет два аккаунта: всё данные slave переносятся на master,
     * затем slave-записи удаляются.
     */
    private void mergeAccounts(Long masterInternal, Long slaveInternal,
                               LinkCode linkCode, Platform receiverPlatform, Long receiverExternal) {

        log.info("Merging accounts: master={}, slave={}", masterInternal, slaveInternal);

        // 1. Удаляем прогресс slave (мастер уже имеет нужный прогресс или оба пустые)
        userProgressRepository.deleteByUserId(slaveInternal);
        userMistakeRepository.deleteByUserId(slaveInternal);
        userStudyTimeRepository.deleteByUserId(slaveInternal);
        userTestResultRepository.deleteByUserId(slaveInternal);
        userSettingsRepository.deleteById(slaveInternal);

        // 2. Сессию slave удаляем, master не трогаем (оставляем его текущую сессию)
        userSessionRepository.deleteById(slaveInternal);
        log.info("Deleted session for slave {}", slaveInternal);

        // 3. Перепривязываем все LinkedAccount с slaveInternal на masterInternal
        List<LinkedAccount> slaveAccounts = linkedAccountRepository.findByInternalUserId(slaveInternal);
        for (LinkedAccount acc : slaveAccounts) {
            acc.setInternalUserId(masterInternal);
            linkedAccountRepository.save(acc);
        }

        // 4. Убеждаемся, что запись receiverPlatform существует
        boolean receiverAlreadyLinked = linkedAccountRepository
                .existsByPlatformAndExternalUserId(receiverPlatform, receiverExternal);
        if (!receiverAlreadyLinked) {
            linkedAccountRepository.save(new LinkedAccount(masterInternal, receiverPlatform, receiverExternal));
        }

        // 5. Помечаем код использованным
        linkCode.setUsed(true);
        linkCodeRepository.save(linkCode);

        log.info("Merge complete: all accounts now use internalUserId={}", masterInternal);
    }

    private String generateUniqueCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (linkCodeRepository.findByCodeAndUsedFalse(code).isPresent());
        return code;
    }

    // ─── Плановая очистка ────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 600_000) // каждые 10 минут
    public void cleanExpiredCodes() {
        linkCodeRepository.deleteExpiredBefore(Instant.now());
    }
}