package com.lbt.telegram_learning_bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RateLimiterService")
class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
        ReflectionTestUtils.setField(rateLimiterService, "maxRequestsPerMinute", 5);
    }

    @Test
    @DisplayName("разрешает запросы в пределах лимита")
    void allowsRequestsWithinLimit() {
        Long userId = 1L;
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiterService.isAllowed(userId))
                    .as("запрос #%d должен быть разрешён", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("блокирует запросы сверх лимита")
    void blocksRequestsAboveLimit() {
        Long userId = 2L;
        // Исчерпываем лимит
        for (int i = 0; i < 5; i++) {
            rateLimiterService.isAllowed(userId);
        }
        // Следующий запрос должен быть отклонён
        assertThat(rateLimiterService.isAllowed(userId))
                .as("6-й запрос должен быть заблокирован")
                .isFalse();
    }

    @Test
    @DisplayName("независимые счётчики для разных пользователей")
    void independentCountersPerUser() {
        Long user1 = 10L;
        Long user2 = 20L;

        // Исчерпываем лимит для user1
        for (int i = 0; i < 5; i++) {
            rateLimiterService.isAllowed(user1);
        }
        assertThat(rateLimiterService.isAllowed(user1)).isFalse();

        // user2 ещё не превысил лимит
        assertThat(rateLimiterService.isAllowed(user2)).isTrue();
    }

    @Test
    @DisplayName("очистка устаревших записей не ломает новые")
    void cleanupDoesNotBreakFreshRequests() {
        Long userId = 3L;
        rateLimiterService.cleanOldEntries(); // не должно бросать исключений
        assertThat(rateLimiterService.isAllowed(userId)).isTrue();
    }
}
