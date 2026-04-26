package com.lbt.telegram_learning_bot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lbt.telegram_learning_bot.bot.BotState;
import com.lbt.telegram_learning_bot.bot.UserContext;
import com.lbt.telegram_learning_bot.entity.UserSession;
import com.lbt.telegram_learning_bot.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    private final Map<Long, CachedSession> sessionCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;

    private record CachedSession(UserSession session, long lastAccessMs) {}

    @Transactional
    public UserSession getOrCreateSession(Long userId) {
        CachedSession cached = sessionCache.get(userId);
        if (cached != null) {
            sessionCache.put(userId, new CachedSession(cached.session(), System.currentTimeMillis()));
            return cached.session();
        }
        UserSession session = sessionRepository.findById(userId)
                .orElseGet(() -> {
                    UserSession s = new UserSession();
                    s.setUserId(userId);
                    s.setState(BotState.MAIN_MENU.name());
                    s.setContext("{}");
                    s.setUpdatedAt(Instant.now());
                    return sessionRepository.save(s);
                });
        sessionCache.put(userId, new CachedSession(session, System.currentTimeMillis()));
        return session;
    }

    @Transactional
    public void updateSessionState(Long userId, BotState state) {
        UserSession session = getOrCreateSession(userId);
        session.setState(state.name());
        session.setUpdatedAt(Instant.now());
        sessionRepository.save(session);
        sessionCache.put(userId, new CachedSession(session, System.currentTimeMillis()));
    }

    @Transactional
    public void updateSessionContext(Long userId, UserContext context) {
        try {
            String json = objectMapper.writeValueAsString(context);
            UserSession session = getOrCreateSession(userId);
            session.setContext(json);
            session.setUpdatedAt(Instant.now());
            sessionRepository.save(session);
            sessionCache.put(userId, new CachedSession(session, System.currentTimeMillis()));
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
            session.setUpdatedAt(Instant.now());
            sessionRepository.save(session);
            sessionCache.put(userId, new CachedSession(session, System.currentTimeMillis()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize user context for user {}", userId, e);
        }
    }

    public BotState getCurrentState(Long userId) {
        CachedSession cached = sessionCache.get(userId);
        String rawState = cached != null
                ? cached.session().getState()
                : sessionRepository.findById(userId).map(UserSession::getState).orElse(null);
        if (rawState == null) return BotState.MAIN_MENU;
        return parseBotState(rawState, userId);
    }

    public UserContext getCurrentContext(Long userId) {
        CachedSession cached = sessionCache.get(userId);
        String json = cached != null
                ? cached.session().getContext()
                : sessionRepository.findById(userId).map(UserSession::getContext).orElse(null);
        if (json == null) {
            UserContext ctx = new UserContext();
            initializeCollections(ctx);
            return ctx;
        }
        return deserializeContext(userId, json);
    }

    public SessionSnapshot getSnapshot(Long userId) {
        CachedSession cached = sessionCache.get(userId);
        if (cached != null) {
            return new SessionSnapshot(
                    parseBotState(cached.session().getState(), userId),
                    deserializeContext(userId, cached.session().getContext()));
        }
        return sessionRepository.findById(userId)
                .map(s -> new SessionSnapshot(
                        parseBotState(s.getState(), userId),
                        deserializeContext(userId, s.getContext())))
                .orElseGet(() -> {
                    UserContext ctx = new UserContext();
                    initializeCollections(ctx);
                    return new SessionSnapshot(BotState.MAIN_MENU, ctx);
                });
    }

    public record SessionSnapshot(BotState state, UserContext context) {}

    @Transactional
    public void clearSession(Long userId) {
        sessionCache.remove(userId);
        sessionRepository.deleteById(userId);
    }

    @Scheduled(fixedDelay = CACHE_TTL_MS)
    public void evictStaleCache() {
        long cutoff = System.currentTimeMillis() - CACHE_TTL_MS;
        int before = sessionCache.size();
        sessionCache.entrySet().removeIf(e -> e.getValue().lastAccessMs() < cutoff);
        int removed = before - sessionCache.size();
        if (removed > 0) {
            log.debug("Session cache eviction: removed {} stale entries, remaining {}",
                    removed, sessionCache.size());
        }
    }

    private BotState parseBotState(String rawState, Long userId) {
        try {
            return BotState.valueOf(rawState);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown BotState '{}' for user {}, resetting to MAIN_MENU", rawState, userId);
            return BotState.MAIN_MENU;
        }
    }

    private UserContext deserializeContext(Long userId, String json) {
        try {
            UserContext ctx = objectMapper.readValue(json, UserContext.class);
            initializeCollections(ctx);
            return ctx;
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize user context for user {}, returning empty context", userId, e);
            UserContext ctx = new UserContext();
            initializeCollections(ctx);
            return ctx;
        }
    }

    private void initializeCollections(UserContext ctx) {
        if (ctx.getPendingImages() == null)          ctx.setPendingImages(new ArrayList<>());
        if (ctx.getTestQuestionIds() == null)         ctx.setTestQuestionIds(new ArrayList<>());
        if (ctx.getCurrentTopicBlockIds() == null)    ctx.setCurrentTopicBlockIds(new ArrayList<>());
        if (ctx.getCurrentBlockQuestionIds() == null) ctx.setCurrentBlockQuestionIds(new ArrayList<>());
    }

    /**
     * Принудительно удаляет сессию пользователя из кэша.
     * Следующий вызов getOrCreateSession() обратится в БД.
     */
    public void evictFromCache(Long userId) {
        sessionCache.remove(userId);
    }
}
