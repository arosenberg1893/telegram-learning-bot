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
        LINKED_AUTO_MERGE,
        CONFLICT_NEEDS_CHOICE,
        ALREADY_LINKED,
        INVALID_CODE,
        SAME_PLATFORM,
        ALREADY_LINKED_ELSEWHERE
    }

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
        Long masterInternal = issuerHasProgress ? issuerInternal : receiverInternal;
        Long slaveInternal = masterInternal.equals(issuerInternal) ? receiverInternal : issuerInternal;
        mergeAccounts(masterInternal, slaveInternal, linkCode, receiverPlatform, receiverExternal);
        return LinkResult.LINKED_AUTO_MERGE;
    }

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

    private void mergeAccounts(Long masterInternal, Long slaveInternal,
                               LinkCode linkCode, Platform receiverPlatform, Long receiverExternal) {
        // Используем единые блокировки для пользователей
        Object masterLock = userLockService.getLock(masterInternal);
        Object slaveLock = userLockService.getLock(slaveInternal);
        synchronized (masterLock) {
            synchronized (slaveLock) {
                log.info("Merging accounts: master={}, slave={}", masterInternal, slaveInternal);

                // Удаляем прогресс slave
                userProgressRepository.deleteByUserId(slaveInternal);
                userMistakeRepository.deleteByUserId(slaveInternal);
                userStudyTimeRepository.deleteByUserId(slaveInternal);
                userTestResultRepository.deleteByUserId(slaveInternal);
                userSettingsRepository.deleteById(slaveInternal);

                // Удаляем сессии master и slave, чтобы при следующем обращении создать чистые
                userSessionRepository.deleteById(masterInternal);
                userSessionRepository.deleteById(slaveInternal);
                log.info("Deleted sessions for master {} and slave {}", masterInternal, slaveInternal);

                // Перепривязываем все LinkedAccount с slaveInternal на masterInternal
                List<LinkedAccount> slaveAccounts = linkedAccountRepository.findByInternalUserId(slaveInternal);
                for (LinkedAccount acc : slaveAccounts) {
                    acc.setInternalUserId(masterInternal);
                    linkedAccountRepository.save(acc);
                }

                // Убеждаемся, что запись receiverPlatform существует
                boolean receiverAlreadyLinked = linkedAccountRepository
                        .existsByPlatformAndExternalUserId(receiverPlatform, receiverExternal);
                if (!receiverAlreadyLinked) {
                    linkedAccountRepository.save(new LinkedAccount(masterInternal, receiverPlatform, receiverExternal));
                }

                linkCode.setUsed(true);
                linkCodeRepository.save(linkCode);

                log.info("Merge complete: all accounts now use internalUserId={}", masterInternal);
            }
        }
        // Опционально: удаляем блокировки из карты, чтобы не накапливались
        userLockService.removeLock(masterInternal);
        userLockService.removeLock(slaveInternal);
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