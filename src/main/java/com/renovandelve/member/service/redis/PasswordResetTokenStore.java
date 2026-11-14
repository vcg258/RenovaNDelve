package com.renovandelve.member.service.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 비밀번호 재설정 토큰 (1회용, Redis TTL).
 *
 * - 토큰은 256비트 난수다. 메일로만 전달되고 서버에는 SHA-256 해시만 둔다.
 *   Redis 내용이 유출돼도 재설정 링크를 만들어 낼 수 없다.
 * - 한 번 쓰면 사라진다: 조회와 삭제를 한 명령(GETDEL)으로 처리해, 같은 토큰으로 동시에 두 번 요청해도 하나만 성공한다.
 * - 회원당 유효한 토큰은 하나다. 다시 요청하면 이전 링크는 죽는다.
 */
@Service
public class PasswordResetTokenStore {
    /** 재설정 링크의 유효 시간 */
    public static final Duration TTL = Duration.ofMinutes(30);

    private static final String TOKEN_PREFIX = "pwreset:token:";
    private static final String MEMBER_PREFIX = "pwreset:member:";
    private static final int TOKEN_BYTES = 32;
    /** Base64URL 43자. 이보다 턱없이 긴 입력은 해시도 하지 않고 버린다 */
    private static final int MAX_TOKEN_LENGTH = 128;

    private final RedisTemplate<String, String> redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetTokenStore(@Qualifier("securityRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 새 토큰을 발급한다. 이 회원의 이전 토큰은 무효가 된다 */
    public String issue(Long memberId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(token);

        String memberKey = MEMBER_PREFIX + memberId;
        String previous = redisTemplate.opsForValue().get(memberKey);
        if (previous != null) {
            redisTemplate.delete(TOKEN_PREFIX + previous);
        }
        redisTemplate.opsForValue().set(TOKEN_PREFIX + hash, String.valueOf(memberId), TTL);
        redisTemplate.opsForValue().set(memberKey, hash, TTL);
        return token;
    }

    /**
     * 토큰을 소비한다 (1회용).
     * @return 토큰의 주인(회원 ID). 없거나 만료됐거나 이미 쓴 토큰이면 null
     */
    public Long consume(String token) {
        if (!wellFormed(token)) return null;
        String memberId = redisTemplate.opsForValue().getAndDelete(TOKEN_PREFIX + hash(token));
        if (memberId == null) return null;
        redisTemplate.delete(MEMBER_PREFIX + memberId);
        return Long.valueOf(memberId);
    }

    /** 토큰이 아직 살아 있는지 (소비하지 않는다). 재설정 화면이 만료 여부를 미리 알려주는 데 쓴다 */
    public boolean isValid(String token) {
        if (!wellFormed(token)) return false;
        return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_PREFIX + hash(token)));
    }

    /**
     * 토큰 형식이 맞는지 확인한다 (비어 있지 않고 길이 제한 안).
     *
     * @param token 토큰
     * @return 형식이 맞으면 true
     */
    private boolean wellFormed(String token) {
        return token != null && !token.isBlank() && token.length() <= MAX_TOKEN_LENGTH;
    }

    /** 개인정보(이메일 등)를 Redis 키에 그대로 쓰지 않기 위한 짧은 해시 */
    public static String hashForKey(String value) {
        return hash(value).substring(0, 32);
    }

    /**
     * SHA-256 해시 (16진수). Redis에는 토큰 원문 대신 이 값만 둔다.
     *
     * @param token 원문
     * @return 해시
     */
    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }
}
