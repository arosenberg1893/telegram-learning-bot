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

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLinkService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final LinkedAccountRepository linkedAccountRepository;
    private final LinkCodeRepository linkCodeRepository;
    private final UserProgressRepository userProgressRepository;
    private final UserMistakeRepository userMistakeRepository;
    private final UserStudyTimeRepository userStudyTimeRepository;
    private final UserTestResultRepository userTestResultRepository;
    private final UserSessionRepository userSessionRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserLockService userLockService;

    // ================== Основные публичные методы ==================

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

    public boolean isLinkedToBothPlatforms(Long internalUserId) {
        List<LinkedAccount> accounts = linkedAccountRepository.findByInternalUserId(internalUserId);
        boolean hasTg = accounts.stream().anyMatch(a -> a.getPlatform() == Platform.TELEGRAM);
        boolean hasVk = accounts.stream().anyMatch(a -> a.getPlatform() == Platform.VK);
        return hasTg && hasVk;
    }

    public List<Platform> getLinkedPlatforms(Long internalUserId) {
        return linkedAccountRepository.findByInternalUserId(internalUserId)
                .stream().map(LinkedAccount::getPlatform).toList();
    }

    @Transactional
    public String generateLinkCode(Long internalUserId, Platform issuerPlatform) {
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

    public enum LinkResult {
        LINKED_AUTO_MERGE,      // автоматическое слияние (прогресс только на одной стороне)
        CONFLICT_NEEDS_CHOICE,  // прогресс на обеих сторонах, требуется выбор пользователя
        ALREADY_LINKED,
        INVALID_CODE,
        SAME_PLATFORM,
        ALREADY_LINKED_ELSEWHERE
    }

    /**
     * Применяет код привязки.
     * Если прогресс есть только у одного пользователя – автоматическое слияние.
     * Если прогресс есть у обоих – возвращает CONFLICT_NEEDS_CHOICE.
     */
    @Transactional
    public LinkResult applyLinkCode(String code, Platform receiverPlatform, Long receiverExternal) {
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
        List<LinkedAccount> receiverAccounts = linkedAccountRepository.findByInternalUserId(receiverInternal);
        if (receiverAccounts.size() > 1) {
            return LinkResult.ALREADY_LINKED_ELSEWHERE;
        }
        boolean issuerHasProgress = userProgressRepository.existsByUserId(issuerInternal);
        boolean receiverHasProgress = userProgressRepository.existsByUserId(receiverInternal);
        if (issuerHasProgress && receiverHasProgress) {
            return LinkResult.CONFLICT_NEEDS_CHOICE;
        }
        // Автоматическое слияние: сохраняем пользователя с прогрессом, удаляем пустого
        Long masterInternal = issuerHasProgress ? issuerInternal : receiverInternal;
        Long slaveInternal = masterInternal.equals(issuerInternal) ? receiverInternal : issuerInternal;
        mergeAccounts(masterInternal, slaveInternal, linkCode, receiverPlatform, receiverExternal);
        return LinkResult.LINKED_AUTO_MERGE;
    }

    // ================== Методы разрешения конфликта (три варианта) ==================

    /**
     * Сохранить прогресс Telegram (receiver), удалить прогресс VK (issuer).
     * @param receiverInternal внутренний ID получателя (Telegram)
     * @param code код привязки
     * @param platform платформа получателя
     * @param externalUserId внешний ID получателя
     */
    @Transactional
    public void resolveConflictKeepTelegram(Long receiverInternal, String code,
                                            Platform platform, Long externalUserId) {
        Optional<LinkCode> codeOpt = linkCodeRepository.findByCodeAndUsedFalse(code.toUpperCase().trim());
        if (codeOpt.isEmpty()) {
            log.warn("resolveConflictKeepTelegram: code not found or already used: {}", code);
            return;
        }
        LinkCode linkCode = codeOpt.get();
        Long issuerInternal = linkCode.getIssuerInternalUserId();
        // receiverInternal - Telegram, issuerInternal - VK
        // Сохраняем receiver (Telegram), удаляем issuer (VK)
        mergeAccounts(receiverInternal, issuerInternal, linkCode, platform, externalUserId);
        log.info("Conflict resolved: kept Telegram user {}, discarded VK user {}", receiverInternal, issuerInternal);
    }

    /**
     * Сохранить прогресс VK (issuer), удалить прогресс Telegram (receiver).
     * @param receiverInternal внутренний ID получателя (Telegram) – будет удалён
     * @param code код привязки
     * @param platform платформа получателя
     * @param externalUserId внешний ID получателя
     */
    @Transactional
    public void resolveConflictKeepVk(Long receiverInternal, String code,
                                      Platform platform, Long externalUserId) {
        Optional<LinkCode> codeOpt = linkCodeRepository.findByCodeAndUsedFalse(code.toUpperCase().trim());
        if (codeOpt.isEmpty()) {
            log.warn("resolveConflictKeepVk: code not found or already used: {}", code);
            return;
        }
        LinkCode linkCode = codeOpt.get();
        Long issuerInternal = linkCode.getIssuerInternalUserId();
        // Сохраняем issuer (VK), удаляем receiver (Telegram)
        mergeAccounts(issuerInternal, receiverInternal, linkCode, platform, externalUserId);
        log.info("Conflict resolved: kept VK user {}, discarded Telegram user {}", issuerInternal, receiverInternal);
    }

    /**
     * Объединить прогресс обоих аккаунтов.
     * @param userA один из внутренних ID (Telegram или VK)
     * @param userB другой внутренний ID
     * @param settingsFromUserId ID пользователя, чьи настройки будут использованы (userA или userB)
     * @param code код привязки
     * @param platform платформа получателя
     * @param externalUserId внешний ID получателя
     */
    @Transactional
    public void resolveConflictMerge(Long userA, Long userB, Long settingsFromUserId,
                                     String code, Platform platform, Long externalUserId) {
        Optional<LinkCode> codeOpt = linkCodeRepository.findByCodeAndUsedFalse(code.toUpperCase().trim());
        if (codeOpt.isEmpty()) {
            log.warn("resolveConflictMerge: code not found or already used: {}", code);
            return;
        }
        LinkCode linkCode = codeOpt.get();
        // Определяем master (целевой) и slave (донор)
        // master будет userA, slave - userB, но после слияния все записи привяжутся к master.
        // Настройки копируются с settingsFromUserId.
        Long master = userA;
        Long slave = userB;
        if (!master.equals(settingsFromUserId)) {
            // Если настройки нужно взять с slave, сначала скопируем их в master, затем сольём.
            copySettings(slave, master);
        }
        mergeAccounts(master, slave, linkCode, platform, externalUserId);
        log.info("Conflict resolved: merged {} and {}, settings from {}, result master={}",
                userA, userB, settingsFromUserId, master);
    }

    // ================== Внутренние методы слияния ==================

    private void mergeAccounts(Long masterInternal, Long slaveInternal,
                               LinkCode linkCode, Platform receiverPlatform, Long receiverExternal) {
        Object masterLock = userLockService.getLock(masterInternal);
        Object slaveLock = userLockService.getLock(slaveInternal);
        synchronized (masterLock) {
            synchronized (slaveLock) {
                log.info("Merging accounts: master={}, slave={}", masterInternal, slaveInternal);

                // 1. Переносим прогресс (user_progress)
                userProgressRepository.copyUserProgress(slaveInternal, masterInternal);

                // 2. Переносим ошибки (user_mistake) с выбором более поздней записи
                userMistakeRepository.mergeUserMistakes(slaveInternal, masterInternal);

                // 3. Переносим время изучения (user_study_time) – суммируем total_seconds, берём max last_action_at
                userStudyTimeRepository.mergeUserStudyTime(slaveInternal, masterInternal);

                // 4. Переносим результаты тестов (user_test_result) – суммируем correct/wrong, берём max updated_at
                userTestResultRepository.mergeUserTestResults(slaveInternal, masterInternal);

                // 5. Удаляем сессии обоих пользователей (чтобы при следующем обращении создать чистые)
                userSessionRepository.deleteById(masterInternal);
                userSessionRepository.deleteById(slaveInternal);
                log.info("Deleted sessions for master {} and slave {}", masterInternal, slaveInternal);

                // 6. Перепривязываем все LinkedAccount с slaveInternal на masterInternal
                List<LinkedAccount> slaveAccounts = linkedAccountRepository.findByInternalUserId(slaveInternal);
                for (LinkedAccount acc : slaveAccounts) {
                    acc.setInternalUserId(masterInternal);
                    linkedAccountRepository.save(acc);
                }

                // 7. Убеждаемся, что запись для receiverPlatform существует
                boolean receiverAlreadyLinked = linkedAccountRepository
                        .existsByPlatformAndExternalUserId(receiverPlatform, receiverExternal);
                if (!receiverAlreadyLinked) {
                    linkedAccountRepository.save(new LinkedAccount(masterInternal, receiverPlatform, receiverExternal));
                }

                // 8. Удаляем данные slave (прогресс, ошибки, время, тесты, настройки) – они уже скопированы
                userProgressRepository.deleteByUserId(slaveInternal);
                userMistakeRepository.deleteByUserId(slaveInternal);
                userStudyTimeRepository.deleteByUserId(slaveInternal);
                userTestResultRepository.deleteByUserId(slaveInternal);
                userSettingsRepository.deleteById(slaveInternal);

                // 9. Помечаем код как использованный
                linkCode.setUsed(true);
                linkCodeRepository.save(linkCode);

                log.info("Merge complete: all accounts now use internalUserId={}", masterInternal);
            }
        }
        userLockService.removeLock(masterInternal);
        userLockService.removeLock(slaveInternal);
    }

    private void copySettings(Long fromUserId, Long toUserId) {
        userSettingsRepository.findById(fromUserId).ifPresent(settings -> {
            settings.setUserId(toUserId);
            userSettingsRepository.save(settings);
        });
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

    @Scheduled(fixedDelay = 600_000)
    public void cleanExpiredCodes() {
        linkCodeRepository.deleteExpiredBefore(Instant.now());
    }
}