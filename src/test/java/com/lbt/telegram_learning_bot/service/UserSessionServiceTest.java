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
    @DisplayName("возвращает существующую сессию")
    void returnsExistingSession() {
        Long userId = 200L;
        UserSession existing = new UserSession();
        existing.setUserId(userId);
        existing.setState(BotState.AWAITING_SEARCH_QUERY.name());
        existing.setContext("{}");
        existing.setUpdatedAt(Instant.now());

        when(sessionRepository.findById(userId)).thenReturn(Optional.of(existing));

        UserSession result = sessionService.getOrCreateSession(userId);

        assertThat(result.getState()).isEqualTo(BotState.AWAITING_SEARCH_QUERY.name());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("сериализует и десериализует UserContext без потерь")
    void serializesAndDeserializesContextCorrectly() {
        Long userId = 300L;
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setState(BotState.MAIN_MENU.name());
        session.setContext("{}");
        session.setUpdatedAt(Instant.now());

        when(sessionRepository.findById(userId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserContext context = new UserContext();
        context.setCurrentCourseId(5L);
        context.setCurrentSectionId(10L);
        context.setCurrentTopicId(15L);
        context.setCurrentPage(2);

        sessionService.updateSessionContext(userId, context);

        // После обновления сессия должна содержать сериализованный контекст
        verify(sessionRepository).save(argThat(s -> {
            try {
                UserContext restored = objectMapper.readValue(s.getContext(), UserContext.class);
                return restored.getCurrentCourseId().equals(5L)
                        && restored.getCurrentSectionId().equals(10L)
                        && restored.getCurrentTopicId().equals(15L)
                        && restored.getCurrentPage() == 2;
            } catch (Exception e) {
                return false;
            }
        }));
    }

    @Test
    @DisplayName("при неизвестном состоянии сбрасывает на MAIN_MENU")
    void resetsUnknownStateToMainMenu() {
        Long userId = 400L;
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setState("UNKNOWN_LEGACY_STATE");
        session.setContext("{}");
        session.setUpdatedAt(Instant.now());

        when(sessionRepository.findById(userId)).thenReturn(Optional.of(session));

        BotState state = sessionService.getCurrentState(userId);

        assertThat(state).isEqualTo(BotState.MAIN_MENU);
    }
}
