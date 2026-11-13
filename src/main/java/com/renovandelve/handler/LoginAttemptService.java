package com.renovandelve.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 로그인 시도 횟수 제한 (무차별 대입 공격 방어).
 * Redis에 계정별 실패 횟수를 기록하고, 임계치를 넘으면 일정 시간 로그인을 차단한다.
 */
@Log4j2
@Service
public class LoginAttemptService {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "login:fail:";

    /** 로그인 필터가 계산한 잠금 키를 실패/성공 핸들러로 넘기는 요청 속성 이름 */
    public static final String ATTEMPT_KEY_ATTRIBUTE = "loginAttemptKey";

    /**
     * 잠금 키 = 계정 + 접속 IP.
     * 다른 IP의 정상 사용자는 영향받지 않고, IP를 바꿔가며 시도하는 공격은 IP 단위 요청 제한(RateLimit)이 막는다.
     */
    public static String attemptKey(String loginId, String remoteAddr) {
        return loginId + "|" + remoteAddr;
    }

    private final RedisTemplate<String, String> redisTemplate;

    public LoginAttemptService(@Qualifier("securityRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void loginFailed(String loginId) {
        if (loginId == null || loginId.isBlank()) return;
        String key = KEY_PREFIX + loginId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, BLOCK_DURATION);
        }
        log.info("로그인 실패 누적 loginId={} count={}", loginId, count);
    }

    public void loginSucceeded(String loginId) {
        if (loginId == null || loginId.isBlank()) return;
        redisTemplate.delete(KEY_PREFIX + loginId);
    }

    public boolean isBlocked(String loginId) {
        if (loginId == null || loginId.isBlank()) return false;
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + loginId);
        return value != null && Integer.parseInt(value) >= MAX_ATTEMPTS;
    }

    public int remainingAttempts(String loginId) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + loginId);
        int used = value == null ? 0 : Integer.parseInt(value);
        return Math.max(0, MAX_ATTEMPTS - used);
    }

    public long blockMinutes() {
        return BLOCK_DURATION.toMinutes();
    }
}
