package com.renovandelve.service.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renovandelve.engine.GameSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 게임 노드 세션(전투 상태 등)의 Redis 저장소.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class GameSessionService {
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final long TTL_HOURS = 24L;

    private String key(Long gameStateId) {
        return "game:session:" + gameStateId;
    }

    public void save(GameSession session) {
        redisTemplate.opsForValue().set(key(session.getGameStateId()), session, TTL_HOURS, TimeUnit.HOURS);
    }

    public GameSession get(Long gameStateId) {
        Object value = redisTemplate.opsForValue().get(key(gameStateId));
        if (value == null) return null;
        return objectMapper.convertValue(value, GameSession.class);
    }

    /**
     * 세션이 없으면 EXPLORE 상태의 새 세션을 만들어 반환한다.
     * (Redis 유실 시에도 게임 진행이 막히지 않도록 하는 안전장치)
     */
    public GameSession getOrCreate(Long gameStateId) {
        GameSession session = get(gameStateId);
        if (session == null) {
            session = new GameSession();
            session.setGameStateId(gameStateId);
            session.setPhase(GameSession.PHASE_EXPLORE);
            save(session);
            log.info("게임 세션 신규 생성 gameStateId={}", gameStateId);
        }
        return session;
    }

    public void delete(Long gameStateId) {
        redisTemplate.delete(key(gameStateId));
    }
}
