package com.lbt.telegram_learning_bot.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Обеспечивает per-user блокировки для предотвращения гонки состояний.
 *
 * <p>Использует отдельный {@code Object} в качестве монитора для каждого пользователя.
 * Объекты хранятся в {@link ConcurrentHashMap} и существуют всё время жизни приложения —
 * это приемлемо, так как число уникальных userId ограничено.</p>
 *
 * <p>Для более сложных сценариев (tryLock, таймауты) можно заменить Object на
 * {@link java.util.concurrent.locks.ReentrantLock} без изменения внешнего API.</p>
 */
@Service
public class UserLockService {

    private final ConcurrentMap<Long, Object> locks = new ConcurrentHashMap<>();

    /**
     * Возвращает монитор-объект для данного пользователя.
     * Объект создаётся при первом обращении и остаётся в памяти на всё время
     * жизни приложения — это приемлемо, так как число уникальных userId ограничено.
     */
    public Object getLock(Long userId) {
        return locks.computeIfAbsent(userId, k -> new Object());
    }

    /**
     * Удаляет блокировку для пользователя.
     * Следует вызывать только после того, как все нити, ожидающие на этом мониторе,
     * гарантированно завершили работу (например, после слияния аккаунтов).
     */
    public void removeLock(Long userId) {
        locks.remove(userId);
    }
}
