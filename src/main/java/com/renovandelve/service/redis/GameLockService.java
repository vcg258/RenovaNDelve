package com.renovandelve.service.redis;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 회원 단위 게임 행동 직렬화 (Redis 분산 락).
 *
 * 게임 세션은 "Redis에서 읽기 -> 계산 -> 다시 쓰기" 구조라서, 같은 회원의 요청 두 개가
 * 동시에 들어오면 둘 다 같은 상태를 읽고 각자 덮어쓴다(lost update).
 * 예) 보상 선택 요청을 동시에 두 번 보내면 둘 다 REWARD 단계로 판정되어 보상을 두 개 받는다.
 * 서버 권위 구조의 마지막 구멍이므로, 상태를 바꾸는 요청은 회원당 한 번에 하나만 처리한다.
 *
 * - SET NX + TTL: 서버가 처리 도중 죽어도 TTL 뒤 자동 해제
 * - 해제는 Lua로 "내가 건 락일 때만" 삭제: TTL이 지나 다른 요청이 새로 건 락을 지우지 않기 위함
 */
@Log4j2
@Service
public class GameLockService {
    private static final String KEY_PREFIX = "game:lock:";
    // 노드 전진은 LLM 응답을 기다리므로 프론트 타임아웃(90초)보다 길게 잡는다.
    // 락 안에서 가장 오래 걸리는 AI 노드 생성은 GameEngineServiceImpl이 75초에서 끊고 폴백하므로
    // 한 요청이 이 TTL을 넘겨 락이 먼저 풀리는 일은 없다
    private static final Duration LOCK_TTL = Duration.ofSeconds(120);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public GameLockService(@Qualifier("securityRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 락을 잡고 action을 실행한다. 이미 처리 중인 요청이 있으면 IllegalStateException(409).
     * 반드시 트랜잭션 바깥(컨트롤러)에서 호출해, 커밋이 끝난 뒤 락이 풀리게 한다.
     */
    public <T> T withLock(Long memberId, Supplier<T> action) {
        String key = KEY_PREFIX + memberId;
        String token = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new IllegalStateException("이전 행동을 처리하는 중입니다. 잠시 후 다시 시도해주세요.");
        }
        try {
            return action.get();
        } finally {
            try {
                redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
            } catch (Exception e) {
                log.warn("게임 락 해제 실패(TTL로 자동 해제됨) memberId={} 원인={}", memberId, e.getMessage());
            }
        }
    }
}
