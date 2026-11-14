package com.renovandelve.game.service.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.renovandelve.game.engine.GameSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 게임 노드 세션(전투 상태 등)의 Redis 저장소.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameSessionService {
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final long TTL_HOURS = 24L;

    /**
     * 게임 세션의 Redis 키.
     *
     * @param gameStateId 게임 ID
     * @return game:session:{게임 ID}
     */
    private String key(Long gameStateId) {
        return "game:session:" + gameStateId;
    }

    /**
     * 게임 세션을 저장한다 (TTL 24시간).
     *
     * @param session 게임 세션
     */
    public void save(GameSession session) {
        redisTemplate.opsForValue().set(key(session.getGameStateId()), session, TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 게임 세션을 읽는다.
     *
     * @param gameStateId 게임 ID
     * @return 게임 세션, 없으면 null
     */
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

    /**
     * 게임 세션을 지운다.
     *
     * @param gameStateId 게임 ID
     */
    public void delete(Long gameStateId) {
        redisTemplate.delete(key(gameStateId));
    }
}
