package com.lbt.telegram_learning_bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.lbt.telegram_learning_bot.platform.BotButton;
import com.lbt.telegram_learning_bot.platform.BotKeyboard;

@Slf4j
@Service
public class RateLimiterService {

    private static final long MILLIS_PER_MINUTE = 60_000L;

    @Value("${rate.limit.max-requests-per-minute:30}")
    private int maxRequestsPerMinute;

    private final Map<Long, UserRequestInfo> requestCounts = new ConcurrentHashMap<>();

    /**
     * Проверяет, разрешён ли запрос для данного пользователя.
     * Использует скользящее окно в 1 минуту.
     */
    public boolean isAllowed(Long userId) {
        long currentMinute = System.currentTimeMillis() / MILLIS_PER_MINUTE;
        UserRequestInfo info = requestCounts.compute(userId, (key, existing) -> {
            if (existing == null || existing.minute() != currentMinute) {
                return new UserRequestInfo(currentMinute, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return info.count().get() <= maxRequestsPerMinute;
    }

    /**
     * Очищает устаревшие записи раз в минуту.
     */
    @Scheduled(fixedDelay = MILLIS_PER_MINUTE)
    public void cleanOldEntries() {
        long currentMinute = System.currentTimeMillis() / MILLIS_PER_MINUTE;
        int removed = 0;
        var iterator = requestCounts.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().minute() != currentMinute) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Rate limiter cleanup: removed {} stale entries", removed);
        }
    }

    private record UserRequestInfo(long minute, AtomicInteger count) {}
}
