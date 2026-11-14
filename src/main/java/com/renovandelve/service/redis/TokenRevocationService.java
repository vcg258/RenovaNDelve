package com.renovandelve.service.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * AccessToken 즉시 폐기.
 *
 * AccessToken은 서명만 검증하는 무상태 토큰이라, 계정을 정지하거나 권한을 회수해도
 * 만료(30분)까지는 그대로 통과한다. RefreshToken을 지우는 것만으로는 이 창이 닫히지 않는다.
 *
 * 토큰마다 차단 목록을 두는 대신 "이 회원의 토큰은 이 시각 이후 발급분만 유효"라는
 * 기준 시각 하나만 Redis에 둔다. 키 수가 회원 수를 넘지 않고, AccessToken 수명만큼만 보관하면 된다.
 * (그 뒤에는 이전 토큰이 어차피 만료된다)
 */
@Slf4j
@Service
public class TokenRevocationService {
    private static final String KEY_PREFIX = "auth:revoked-before:";
    /** AccessToken 수명(30분)보다 조금 길게 보관 */
    private static final Duration RETENTION = Duration.ofMinutes(35);

    private final RedisTemplate<String, String> redisTemplate;

    public TokenRevocationService(@Qualifier("securityRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 이 회원에게 지금까지 발급된 AccessToken을 모두 무효로 만든다 */
    public void revoke(String loginId) {
        if (loginId == null) return;
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + loginId,
                    String.valueOf(Instant.now().getEpochSecond()), RETENTION);
        } catch (Exception e) {
            log.warn("AccessToken 폐기 기록 실패 loginId={} 원인={}", loginId, e.getMessage());
        }
    }

    /**
     * @param issuedAtEpochSecond 토큰의 iat 클레임
     * @return 폐기 시각 이전(같은 초 포함)에 발급된 토큰이면 true.
     *         iat는 초 단위라 같은 초를 통과시키면 로그인 직후 정지된 토큰이 살아남는다.
     *         대신 폐기와 같은 초에 다시 로그인해 받은 토큰도 한 번 거부되지만, 재발급으로 곧 복구된다
     */
    public boolean isRevoked(String loginId, long issuedAtEpochSecond) {
        if (loginId == null) return false;
        try {
            String revokedBefore = redisTemplate.opsForValue().get(KEY_PREFIX + loginId);
            return revokedBefore != null && issuedAtEpochSecond <= Long.parseLong(revokedBefore);
        } catch (Exception e) {
            // Redis 장애 시에는 통과시킨다. 게임 세션도 Redis에 있어 대부분의 API는 어차피 실패한다.
            log.warn("AccessToken 폐기 확인 실패(통과 처리) 원인={}", e.getMessage());
            return false;
        }
    }
}
