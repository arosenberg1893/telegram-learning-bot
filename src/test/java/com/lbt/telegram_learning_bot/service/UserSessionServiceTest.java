package com.lbt.telegram_learning_bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.entity.UserSession;
import com.lbt.telegram_learning_bot.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSessionService")
class UserSessionServiceTest {

    @Mock
    private UserSessionRepository sessionRepository;

    private UserSessionService sessionService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        sessionService = new UserSessionService(sessionRepository, objectMapper);
    }

    @Test
    @DisplayName("создаёт новую сессию, если её не существует")
    void createsNewSessionIfNotFound() {
        Long userId = 100L;
        when(sessionRepository.findById(userId)).thenReturn(Optional.empty());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserSession session = sessionService.getOrCreateSession(userId);

        assertThat(session.getUserId()).isEqualTo(userId);
        assertThat(session.getState()).isEqualTo(BotState.MAIN_MENU.name());
        verify(sessionRepository).save(any(UserSession.class));
    }

    @Test
    @DisplayName("возвращает существующую сессию из БД")
    void returnsExistingSessionFromDb() {
        Long userId = 200L;
        UserSession existing = buildSession(userId, BotState.AWAITING_SEARCH_QUERY, "{}");
        when(sessionRepository.findById(userId)).thenReturn(Optional.of(existing));

        UserSession result = sessionService.getOrCreateSession(userId);

        assertThat(result.getState()).isEqualTo(BotState.AWAITING_SEARCH_QUERY.name());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("второй вызов getOrCreateSession возвращает кэшированную сессию без запроса в БД")
    void secondCallUsesCache() {
        Long userId = 300L;
        UserSession existing = buildSession(userId, BotState.MAIN_MENU, "{}");
        when(sessionRepository.findById(userId)).thenReturn(Optional.of(existing));

        sessionService.getOrCreateSession(userId); // первый вызов — идёт в БД
        sessionService.getOrCreateSession(userId); // второй — должен брать из кэша

        // БД вызвана только один раз
        verify(sessionRepository, times(1)).findById(userId);
    }

    @Test
    @DisplayName("getCurrentState отдаёт кэшированное состояние без запроса в БД")
    void getCurrentStateUsesCache() {
        Long userId = 400L;
        UserSession existing = buildSession(userId, BotState.AWAITING_SEARCH_QUERY, "{}");
        when(sessionRepository.findById(userId)).thenReturn(Optional.of(existing));

        // Прогреваем кэш
        sessionService.getOrCreateSession(userId);

        // Теперь getCurrentState не должна идти в БД
        BotState state = sessionService.getCurrentState(userId);

        assertThat(state).isEqualTo(BotState.AWAITING_SEARCH_QUERY);
        // findById вызвана всего один раз (при прогреве кэша)
        verify(sessionRepository, times(1)).findById(userId);
    }

    @Test
    @DisplayName("сериализует и десериализует UserContext без потерь")
    void serializesAndDeserializesContextCorrectly() {
        Long userId = 500L;
        UserSession session = buildSession(userId, BotState.MAIN_MENU, "{}");
        when(sessionRepository.findById(userId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserContext context = new UserContext();
        context.setCurrentCourseId(5L);
        context.setCurrentSectionId(10L);
        context.setCurrentTopicId(15L);
        context.setCurrentPage(2);

        sessionService.updateSessionContext(userId, context);

        verify(sessionRepository).save(argThat(s -> {
            try {
                UserContext restored = objectMapper.readValue(s.getContext(), UserContext.class);
                return Long.valueOf(5L).equals(restored.getCurrentCourseId())
                        && Long.valueOf(10L).equals(restored.getCurrentSectionId())
                        && Long.valueOf(15L).equals(restored.getCurrentTopicId())
                        && restored.getCurrentPage() == 2;
            } catch (Exception e) {
                return false;
            }
        }));
    }

    @Test
    @DisplayName("при неизвестном состоянии сбрасывает на MAIN_MENU")
    void resetsUnknownStateToMainMenu() {
        Long userId = 600L;
        UserSession session = buildSession(userId, null, "{}");
        session.setState("UNKNOWN_LEGACY_STATE");
        when(sessionRepository.findById(userId)).thenReturn(Optional.of(session));

        BotState state = sessionService.getCurrentState(userId);

        assertThat(state).isEqualTo(BotState.MAIN_MENU);
    }

    @Test
    @DisplayName("evictFromCache инвалидирует кэш — следующий вызов идёт в БД")
    void evictFromCacheForcesDbRead() {
        Long userId = 700L;
        UserSession existing = buildSession(userId, BotState.MAIN_MENU, "{}");
        when(sessionRepository.findById(userId)).thenReturn(Optional.of(existing));

        sessionService.getOrCreateSession(userId); // кэшируем
        sessionService.evictFromCache(userId);     // инвалидируем
        sessionService.getCurrentState(userId);    // должно идти в БД снова

        verify(sessionRepository, times(2)).findById(userId);
    }

    // ────────────────────────────────────────────────────────────────────────
    private UserSession buildSession(Long userId, BotState state, String context) {
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setState(state != null ? state.name() : BotState.MAIN_MENU.name());
        session.setContext(context);
        session.setUpdatedAt(Instant.now());
        return session;
    }
}