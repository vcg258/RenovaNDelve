package com.renovandelve.service.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 고정 윈도우(Fixed Window) 요청 제한.
 *
 * 키마다 윈도우 시간 동안의 요청 수를 INCR로 세고, 한도를 넘으면 거부한다.
 * - 인증 API: IP 단위 (무차별 대입, 계정 대량 생성, 아이디 수집 방어)
 * - LLM 호출 API: 회원 단위 (외부 API 비용 폭주 방어)
 *
 * Redis 장애 시의 동작은 호출하는 쪽이 고른다.
 * - fail-open(기본): 제한을 포기하고 통과. 요청 제한은 보조 방어선이므로, 이것 때문에 로그인까지 막히는 쪽이 더 큰 손해다.
 * - fail-closed: 거부. 통과시키면 곧바로 외부 API 비용이 나가는 경로(가이드 챗봇)에 쓴다.
 */
@Slf4j
@Service
public class RateLimitService {
    private static final String KEY_PREFIX = "ratelimit:";

    private final RedisTemplate<String, String> redisTemplate;

    public RateLimitService(@Qualifier("securityRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * @param bucket 제한 종류 (login, signup, guide ...)
     * @param subject 제한 대상 (IP 또는 회원 식별자)
     * @return 허용이면 0, 초과면 다시 시도할 수 있을 때까지 남은 초(최소 1)
     */
    public long retryAfterSeconds(String bucket, String subject, int limit, Duration window) {
        return retryAfterSeconds(bucket, subject, limit, window, false);
    }

    /** Redis 장애로 횟수를 셀 수 없을 때 failClosed면 이 시간만큼 기다리라고 답한다 */
    private static final long FAIL_CLOSED_RETRY_SECONDS = 30;

    /**
     * @param failClosed true면 Redis 장애 시 거부한다 (기본은 통과)
     */
    public long retryAfterSeconds(String bucket, String subject, int limit, Duration window, boolean failClosed) {
        String key = KEY_PREFIX + bucket + ":" + subject;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) return 0;
            if (count == 1L) {
                redisTemplate.expire(key, window);
            }
            if (count <= limit) return 0;

            Long ttl = redisTemplate.getExpire(key);
            if (ttl == null || ttl < 0) {
                // INCR 직후 EXPIRE가 실패해 만료 없는 키가 남은 경우 -> 영구 차단 방지
                redisTemplate.expire(key, window);
                return window.toSeconds();
            }
            return Math.max(1, ttl);
        } catch (Exception e) {
            log.warn("요청 제한 확인 실패({}) bucket={} 원인={}", failClosed ? "거부 처리" : "통과 처리", bucket, e.getMessage());
            return failClosed ? FAIL_CLOSED_RETRY_SECONDS : 0;
        }
    }
}
