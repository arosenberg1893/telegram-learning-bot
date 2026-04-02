package com.lbt.telegram_learning_bot.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class UserLockService {
    private final ConcurrentMap<Long, Object> locks = new ConcurrentHashMap<>();

    public Object getLock(Long userId) {
        return locks.computeIfAbsent(userId, k -> new Object());
    }

    public void removeLock(Long userId) {
        locks.remove(userId);
    }
}