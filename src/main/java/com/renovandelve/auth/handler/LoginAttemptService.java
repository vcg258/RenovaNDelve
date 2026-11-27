package com.renovandelve.auth.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 로그인 시도 횟수 제한 (무차별 대입 공격 방어).
 * 실패 횟수를 두 단위로 센다.
 * <ul>
 *   <li>계정 + 접속 IP: 5번 틀리면 그 IP에서 그 계정으로 10분 동안 로그인할 수 없다.
 *       남의 아이디를 일부러 틀려 주인까지 잠그는 공격을 막기 위해 IP를 함께 본다.</li>
 *   <li>계정 전체: 여러 IP에서 합쳐 1시간에 30번 틀리면 모든 IP에서 잠근다.
 *       IP를 바꿔 가며 한 계정의 비밀번호를 맞히는 공격에 상한을 둔다.
 *       한도를 넉넉히 잡아, 한 사람이 일부러 주인을 잠그려면 IP를 여러 개 동원해야 한다.</li>
 * </ul>
 */
@Slf4j
@Service
public class LoginAttemptService {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "login:fail:";

    /** 계정 전체(모든 IP 합산) 실패 한도와 집계 기간 */
    private static final int MAX_ACCOUNT_ATTEMPTS = 30;
    private static final Duration ACCOUNT_WINDOW = Duration.ofHours(1);
    private static final String ACCOUNT_KEY_PREFIX = "login:fail-account:";

    /** 로그인 필터가 계산한 잠금 키를 실패/성공 핸들러로 넘기는 요청 속성 이름 */
    public static final String ATTEMPT_KEY_ATTRIBUTE = "loginAttemptKey";

    /**
     * 잠금 키 = 계정 + 접속 IP. 다른 IP의 정상 사용자는 영향받지 않는다.
     * IP를 바꿔 가며 시도하는 공격은 계정 전체 한도(MAX_ACCOUNT_ATTEMPTS)가 막는다.
     */
    public static String attemptKey(String loginId, String remoteAddr) {
        return loginId + "|" + remoteAddr;
    }

    private final RedisTemplate<String, String> redisTemplate;

    public LoginAttemptService(@Qualifier("securityRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 로그인 실패를 한 번 센다 (계정 + IP, 계정 전체 둘 다). 처음 실패할 때 집계 기간만큼 만료를 건다.
     *
     * @param attemptKey 시도 키 (계정 + IP)
     */
    public void loginFailed(String attemptKey) {
        if (attemptKey == null || attemptKey.isBlank()) return;
        Long count = increment(KEY_PREFIX + attemptKey, BLOCK_DURATION);
        Long accountCount = increment(ACCOUNT_KEY_PREFIX + accountOf(attemptKey), ACCOUNT_WINDOW);
        log.info("로그인 실패 누적 key={} count={} accountCount={}", attemptKey, count, accountCount);
    }

    /**
     * 로그인에 성공하면 실패 횟수를 지운다. 비밀번호를 맞힌 사람은 주인이므로 계정 전체 집계도 지운다.
     *
     * @param attemptKey 시도 키 (계정 + IP)
     */
    public void loginSucceeded(String attemptKey) {
        if (attemptKey == null || attemptKey.isBlank()) return;
        redisTemplate.delete(List.of(KEY_PREFIX + attemptKey, ACCOUNT_KEY_PREFIX + accountOf(attemptKey)));
    }

    /**
     * 계정의 로그인 실패 기록을 모든 접속지에서 지운다 (비밀번호 재설정 성공 시).
     * 잠금 키는 "아이디|IP"라서 아이디로 시작하는 키를 찾아 지운다. 키 수가 적어 SCAN으로 충분하다.
     *
     * @param loginId 로그인 아이디
     */
    public void clearAccount(String loginId) {
        if (loginId == null || loginId.isBlank()) return;
        var options = org.springframework.data.redis.core.ScanOptions.scanOptions()
                .match(KEY_PREFIX + loginId + "|*").count(100).build();
        List<String> keys = new ArrayList<>();
        try (var cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        keys.add(ACCOUNT_KEY_PREFIX + loginId);
        redisTemplate.delete(keys);
    }

    /**
     * 실패가 한도에 닿아 잠겨 있는지 확인한다. 계정 + IP 한도나 계정 전체 한도 중 하나라도 닿으면 잠긴 것이다.
     *
     * @param attemptKey 시도 키 (계정 + IP)
     * @return 잠겨 있으면 true
     */
    public boolean isBlocked(String attemptKey) {
        if (attemptKey == null || attemptKey.isBlank()) return false;
        return count(KEY_PREFIX + attemptKey) >= MAX_ATTEMPTS
                || count(ACCOUNT_KEY_PREFIX + accountOf(attemptKey)) >= MAX_ACCOUNT_ATTEMPTS;
    }

    /**
     * 잠기기 전까지 남은 시도 횟수.
     *
     * @param loginId 시도 키 (계정 + IP)
     * @return 남은 횟수
     */
    public int remainingAttempts(String attemptKey) {
        return (int) Math.max(0, MAX_ATTEMPTS - count(KEY_PREFIX + attemptKey));
    }

    /**
     * 잠금이 풀리기까지 남은 시간 (분, 올림). 계정 전체 잠금이면 그 남은 시간을 쓴다.
     *
     * @param attemptKey 시도 키 (계정 + IP)
     * @return 분
     */
    public long blockMinutes(String attemptKey) {
        String key = count(ACCOUNT_KEY_PREFIX + accountOf(attemptKey)) >= MAX_ACCOUNT_ATTEMPTS
                ? ACCOUNT_KEY_PREFIX + accountOf(attemptKey)
                : KEY_PREFIX + attemptKey;
        Long seconds = redisTemplate.getExpire(key);
        if (seconds == null || seconds <= 0) return BLOCK_DURATION.toMinutes();
        return (seconds + 59) / 60;
    }

    /**
     * 시도 키에서 계정 부분을 꺼낸다. IP에는 '|'가 없으므로 마지막 '|' 앞이 계정이다.
     *
     * @param attemptKey 시도 키 (계정 + IP)
     * @return 로그인 아이디
     */
    static String accountOf(String attemptKey) {
        int bar = attemptKey.lastIndexOf('|');
        return bar < 0 ? attemptKey : attemptKey.substring(0, bar);
    }

    /**
     * 카운터를 1 올리고, 처음 올린 것이면 만료를 건다.
     *
     * @param key    Redis 키
     * @param window 만료
     * @return 올린 뒤의 값
     */
    private Long increment(String key, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
        return count;
    }

    /**
     * @param key Redis 키
     * @return 카운터 값 (없으면 0)
     */
    private long count(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0 : Long.parseLong(value);
    }
}
