package com.renovandelve.service.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class SecurityTokenService {
    private final RedisTemplate<String, String> redisTemplate;

    public SecurityTokenService(@Qualifier("securityRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * RefreshToken Redis에 저장
     * @param loginId 로그인 아이디
     * @param refreshToken RefreshToken
     * @param expireMinutes 만료시간
     */
    public void saveRefreshToken(String loginId, String refreshToken, long expireMinutes) {
        redisTemplate.opsForValue()
                .set("refresh:" + loginId, refreshToken, Duration.ofMinutes(expireMinutes));
        log.info("RefreshToken Redis Save");
    }

    /**
     * RefreshToken 조회
     * @param loginId 로그인 아이디
     * @return Redis에 저장된 RefreshToken
     */
    public String getRefreshToken(String loginId) {
        return redisTemplate.opsForValue().get("refresh:" + loginId);
    }

    /**
     * 로그아웃 (RefreshToken 제거)
     * @param loginId 사용자 아이디
     */
    public void logout(String loginId) {
        redisTemplate.delete("refresh:" + loginId);
    }
}
