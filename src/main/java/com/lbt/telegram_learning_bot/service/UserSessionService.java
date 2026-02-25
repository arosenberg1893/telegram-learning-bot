package com.lbt.telegram_learning_bot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.entity.UserSession;
import com.lbt.telegram_learning_bot.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public UserSession getOrCreateSession(Long userId) {
        return sessionRepository.findById(userId)
                .orElseGet(() -> {
                    UserSession newSession = new UserSession();
                    newSession.setUserId(userId);
                    newSession.setState(BotState.MAIN_MENU.name());
                    newSession.setContext("{}");
                    return sessionRepository.save(newSession);
                });
    }

    @Transactional
    public void updateSessionState(Long userId, BotState state) {
        UserSession session = getOrCreateSession(userId);
        session.setState(state.name());
        sessionRepository.save(session);
    }

    @Transactional
    public void updateSessionContext(Long userId, UserContext context) {
        try {
            String json = objectMapper.writeValueAsString(context);
            UserSession session = getOrCreateSession(userId);
            session.setContext(json);
            sessionRepository.save(session);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize user context for user {}", userId, e);
        }
    }

    @Transactional
    public void updateSession(Long userId, BotState state, UserContext context) {
        try {
            String json = objectMapper.writeValueAsString(context);
            UserSession session = getOrCreateSession(userId);
            session.setState(state.name());
            session.setContext(json);
            sessionRepository.save(session);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize user context for user {}", userId, e);
        }
    }

    public BotState getCurrentState(Long userId) {
        return sessionRepository.findById(userId)
                .map(session -> {
                    try {
                        return BotState.valueOf(session.getState());
                    } catch (IllegalArgumentException e) {
                        return BotState.MAIN_MENU;
                    }
                })
                .orElse(BotState.MAIN_MENU);
    }

    public UserContext getCurrentContext(Long userId) {
        return sessionRepository.findById(userId)
                .map(session -> {
                    try {
                        UserContext ctx = objectMapper.readValue(session.getContext(), UserContext.class);
                        // Инициализация списков для предотвращения NPE
                        if (ctx.getPendingImages() == null) ctx.setPendingImages(new ArrayList<>());
                        if (ctx.getTestQuestionIds() == null) ctx.setTestQuestionIds(new ArrayList<>());
                        if (ctx.getCurrentTopicBlockIds() == null) ctx.setCurrentTopicBlockIds(new ArrayList<>());
                        if (ctx.getCurrentBlockQuestionIds() == null) ctx.setCurrentBlockQuestionIds(new ArrayList<>());
                        return ctx;
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize user context for user {}", userId, e);
                        return new UserContext();
                    }
                })
                .orElse(new UserContext());
    }

    @Transactional
    public void clearSession(Long userId) {
        sessionRepository.deleteById(userId);
    }
}