package com.renovandelve.member.service.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 비밀번호 재설정 토큰: 1회용, 회원당 하나, 원문은 저장하지 않는다.
 * Redis는 맵으로 흉내 낸다 (TTL 만료 자체는 Redis의 동작이라 여기서는 TTL을 걸었는지만 본다).
 */
class PasswordResetTokenStoreTest {

    private final Map<String, String> redis = new HashMap<>();
    private RedisTemplate<String, String> redisTemplate;
    private ValueOperations<String, String> values;
    private PasswordResetTokenStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> redis.get(call.<String>getArgument(0)));
        when(values.getAndDelete(anyString())).thenAnswer(call -> redis.remove(call.<String>getArgument(0)));
        when(redisTemplate.delete(anyString())).thenAnswer(call -> redis.remove(call.<String>getArgument(0)) != null);
        when(redisTemplate.hasKey(anyString())).thenAnswer(call -> redis.containsKey(call.<String>getArgument(0)));
        org.mockito.Mockito.doAnswer(call -> redis.put(call.getArgument(0), call.getArgument(1)))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        store = new PasswordResetTokenStore(redisTemplate);
    }

    @Test
    @DisplayName("발급한 토큰은 한 번만 쓸 수 있다")
    void tokenIsSingleUse() {
        String token = store.issue(42L);

        assertThat(store.isValid(token)).isTrue();
        assertThat(store.consume(token)).isEqualTo(42L);
        assertThat(store.consume(token)).isNull();   // 두 번째는 실패
        assertThat(store.isValid(token)).isFalse();
        assertThat(redis).isEmpty();                 // 흔적이 남지 않는다
    }

    @Test
    @DisplayName("유효 여부 확인은 토큰을 소비하지 않는다")
    void checkingDoesNotConsume() {
        String token = store.issue(42L);

        assertThat(store.isValid(token)).isTrue();
        assertThat(store.isValid(token)).isTrue();
        assertThat(store.consume(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("다시 발급하면 이전 토큰은 무효가 된다 (회원당 하나)")
    void reissueInvalidatesPrevious() {
        String first = store.issue(42L);
        String second = store.issue(42L);

        assertThat(first).isNotEqualTo(second);
        assertThat(store.consume(first)).isNull();
        assertThat(store.consume(second)).isEqualTo(42L);
    }

    @Test
    @DisplayName("다른 회원의 토큰끼리는 서로 영향을 주지 않는다")
    void tokensAreIndependentAcrossMembers() {
        String a = store.issue(1L);
        String b = store.issue(2L);

        assertThat(store.consume(a)).isEqualTo(1L);
        assertThat(store.consume(b)).isEqualTo(2L);
    }

    @Test
    @DisplayName("Redis에는 토큰 원문이 아니라 해시만 저장되고, 30분 TTL이 걸린다")
    void storesOnlyHashWithTtl() {
        String token = store.issue(42L);

        assertThat(token).hasSizeGreaterThanOrEqualTo(43); // 256비트 Base64URL
        assertThat(redis.keySet()).noneMatch(key -> key.contains(token));
        assertThat(redis.values()).noneMatch(value -> value.contains(token));
        verify(values).set(anyString(), org.mockito.ArgumentMatchers.eq("42"), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("없는 토큰, 빈 값, 지나치게 긴 값은 모두 거부한다")
    void rejectsUnknownOrMalformed() {
        store.issue(42L);

        assertThat(store.consume("not-a-real-token")).isNull();
        assertThat(store.consume(null)).isNull();
        assertThat(store.consume("  ")).isNull();
        assertThat(store.consume("x".repeat(500))).isNull();
        assertThat(store.isValid(null)).isFalse();
        assertThat(redis).hasSize(2); // 원래 토큰은 그대로 살아 있다
    }

    @Test
    @DisplayName("토큰은 매번 다르다")
    void tokensAreRandom() {
        assertThat(store.issue(1L)).isNotEqualTo(store.issue(2L));
    }
}
