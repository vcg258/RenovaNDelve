package com.renovandelve.service.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis에 기대는 두 보조 방어선(요청 제한, AccessToken 폐기)의 판정 규칙과 Redis 장애 시 동작.
 */
class RedisGuardTest {

    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> values;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
    }

    @Test
    @DisplayName("요청 제한: 한도 안이면 통과, 넘으면 남은 시간을 돌려준다")
    void rateLimitCounts() {
        RateLimitService service = new RateLimitService(redisTemplate);
        when(values.increment("ratelimit:guide:min:user1")).thenReturn(8L, 9L);
        when(redisTemplate.getExpire("ratelimit:guide:min:user1")).thenReturn(42L);

        assertThat(service.retryAfterSeconds("guide:min", "user1", 8, Duration.ofMinutes(1))).isZero();
        assertThat(service.retryAfterSeconds("guide:min", "user1", 8, Duration.ofMinutes(1))).isEqualTo(42);
    }

    @Test
    @DisplayName("요청 제한: Redis 장애 시 기본은 통과, failClosed면 거부")
    void rateLimitOnRedisFailure() {
        RateLimitService service = new RateLimitService(redisTemplate);
        when(values.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(service.retryAfterSeconds("login", "1.2.3.4", 10, Duration.ofMinutes(1))).isZero();
        assertThat(service.retryAfterSeconds("guide:min", "user1", 8, Duration.ofMinutes(1), true)).isPositive();
    }

    @Test
    @DisplayName("토큰 폐기: 폐기 시각 이전(같은 초 포함)에 발급된 토큰만 무효다")
    void revocationComparesIssuedAt() {
        TokenRevocationService service = new TokenRevocationService(redisTemplate);
        long revokedAt = Instant.now().getEpochSecond();
        when(values.get("auth:revoked-before:user1")).thenReturn(String.valueOf(revokedAt));

        assertThat(service.isRevoked("user1", revokedAt - 60)).isTrue();  // 정지 전에 받은 토큰
        assertThat(service.isRevoked("user1", revokedAt)).isTrue();       // 로그인 직후 같은 초에 정지된 토큰
        assertThat(service.isRevoked("user1", revokedAt + 5)).isFalse();  // 그 뒤 다시 로그인해 받은 토큰
        assertThat(service.isRevoked("user2", revokedAt - 60)).isFalse(); // 폐기 기록이 없는 회원
    }

    @Test
    @DisplayName("토큰 폐기: Redis 장애 시에는 통과시킨다")
    void revocationOnRedisFailure() {
        TokenRevocationService service = new TokenRevocationService(redisTemplate);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(service.isRevoked("user1", 0)).isFalse();
    }
}
