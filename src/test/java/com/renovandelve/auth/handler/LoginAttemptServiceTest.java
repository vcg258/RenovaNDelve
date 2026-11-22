package com.renovandelve.auth.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 로그인 잠금: 계정 + IP 한도(5회)와 계정 전체 한도(30회)를 Redis 대신 메모리 맵으로 확인한다.
 */
class LoginAttemptServiceTest {

    private final Map<String, String> store = new HashMap<>();
    private LoginAttemptService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenAnswer(call -> {
            String key = call.getArgument(0);
            long next = Long.parseLong(store.getOrDefault(key, "0")) + 1;
            store.put(key, String.valueOf(next));
            return next;
        });
        when(ops.get(anyString())).thenAnswer(call -> store.get((String) call.getArgument(0)));
        when(redis.delete(any(Collection.class))).thenAnswer(call -> {
            Collection<String> keys = call.getArgument(0);
            keys.forEach(store::remove);
            return (long) keys.size();
        });
        service = new LoginAttemptService(redis);
    }

    @Test
    @DisplayName("같은 IP에서 5번 틀리면 그 IP만 잠기고, 다른 IP의 주인은 로그인할 수 있다")
    void lockIsPerAccountAndIp() {
        String attacker = LoginAttemptService.attemptKey("hong", "1.1.1.1");
        for (int i = 0; i < 5; i++) service.loginFailed(attacker);

        assertThat(service.isBlocked(attacker)).isTrue();
        assertThat(service.isBlocked(LoginAttemptService.attemptKey("hong", "2.2.2.2"))).isFalse();
    }

    @Test
    @DisplayName("IP를 바꿔 가며 틀려도 계정 전체로 30번이면 모든 IP에서 잠긴다")
    void accountWideCapStopsIpRotation() {
        for (int ip = 0; ip < 10; ip++) {
            String key = LoginAttemptService.attemptKey("hong", "10.0.0." + ip);
            for (int i = 0; i < 3; i++) service.loginFailed(key); // IP마다 한도(5) 아래로만 틀린다
        }

        assertThat(service.isBlocked(LoginAttemptService.attemptKey("hong", "10.0.0.99"))).isTrue();
        assertThat(service.isBlocked(LoginAttemptService.attemptKey("kim", "10.0.0.99"))).isFalse();
    }

    @Test
    @DisplayName("로그인에 성공하면 그 IP와 계정 전체 실패 기록이 지워진다")
    void successClearsCounters() {
        String key = LoginAttemptService.attemptKey("hong", "1.1.1.1");
        for (int i = 0; i < 4; i++) service.loginFailed(key);

        service.loginSucceeded(key);

        assertThat(service.remainingAttempts(key)).isEqualTo(5);
        assertThat(store).isEmpty();
    }

    @Test
    @DisplayName("시도 키에서 계정을 꺼낼 때 마지막 '|' 앞까지를 쓴다")
    void accountOfUsesLastBar() {
        assertThat(LoginAttemptService.accountOf("hong|1.1.1.1")).isEqualTo("hong");
        assertThat(LoginAttemptService.accountOf("odd|id|::1")).isEqualTo("odd|id");
    }
}
