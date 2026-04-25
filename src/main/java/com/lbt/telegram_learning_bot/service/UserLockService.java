package com.lbt.telegram_learning_bot.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Обеспечивает per-user блокировки для предотвращения гонок состояний.
 *
 * <p>Каждому пользователю выдаётся отдельный монитор-объект ({@code Object}).
 * Мониторы автоматически вытесняются из кэша через {@value #TTL_MINUTES} минут
 * после последнего обращения, что предотвращает бесконечный рост памяти
 * при большом числе уникальных userId.</p>
 *
 * <p>Важно: Caffeine вытесняет запись только при отсутствии внешних ссылок,
 * поэтому монитор, удерживаемый потоком в {@code synchronized}-блоке,
 * не будет удалён в середине критической секции.</p>
 */
@Service
public class UserLockService {

    private static final long TTL_MINUTES = 60;

    private final Cache<Long, Object> locks = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(TTL_MINUTES))
            .build();

    /**
     * Возвращает монитор-объект для данного пользователя.
     * Объект создаётся при первом обращении и живёт в кэше пока активен.
     */
    public Object getLock(Long userId) {
        return locks.get(userId, k -> new Object());
    }

    /**
     * Принудительно удаляет монитор для пользователя из кэша.
     * Вызывать только после того, как все потоки, ожидающие на мониторе,
     * гарантированно завершили работу.
     */
    public void removeLock(Long userId) {
        locks.invalidate(userId);
    }
}
