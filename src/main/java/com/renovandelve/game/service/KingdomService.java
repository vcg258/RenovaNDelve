package com.renovandelve.game.service;

import com.renovandelve.game.dto.response.KingdomDTO;
import com.renovandelve.game.engine.KingdomState;
import com.renovandelve.game.repository.GameStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 왕국의 저울: 지난 7일 동안 모든 플레이어가 택한 방식을 모아 왕국의 정세를 정한다.
 *
 * - 한 사람이 한 표: 회원마다 성향 합계를 비율로 바꿔 더한다. 많이 플레이한 한 사람이 저울을 혼자 기울이지 못하게 하려는 것이다.
 * - 계산은 집계 쿼리 한 번이고, 결과는 10분 동안 메모리에 둔다(정세는 천천히 바뀌는 값이다).
 * - 정세는 원정을 시작할 때 그 원정에 고정한다. 원정 도중에 효과가 바뀌지 않는다.
 *
 * 한계: 플레이어가 적으면 몇 사람의 선택이 곧 정세다. 혼자 시연하면 자기 선택이 그대로 왕국이 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KingdomService {

    /** 정세를 계산하는 기간 */
    static final Duration WINDOW = Duration.ofDays(7);
    /** 계산 결과를 다시 쓰는 시간 */
    static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final GameStateRepository gameStateRepository;

    /** 테스트에서 바꿀 수 있도록 필드로 둔다 */
    Clock clock = Clock.systemDefaultZone();

    private volatile KingdomDTO cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    /**
     * 지금의 왕국 정세.
     *
     * @return 정세
     */
    @Transactional(readOnly = true)
    public KingdomDTO current() {
        Instant now = clock.instant();
        KingdomDTO snapshot = cached;
        if (snapshot != null && Duration.between(cachedAt, now).compareTo(CACHE_TTL) < 0) return snapshot;
        KingdomDTO fresh = compute(gameStateRepository.sumAffinityByMemberSince(LocalDateTime.now(clock).minus(WINDOW)));
        cached = fresh;
        cachedAt = now;
        return fresh;
    }

    /**
     * 지금 정세 (원정 시작 시 고정용). 계산에 실패하면 균형으로 본다.
     *
     * @return 정세
     */
    public KingdomState currentState() {
        try {
            return KingdomState.valueOf(current().state());
        } catch (Exception e) {
            log.warn("왕국 정세 계산 실패, 균형으로 둔다: {}", e.getMessage());
            return KingdomState.BALANCE;
        }
    }

    /**
     * 회원별 성향 합계로 정세를 계산한다. 선택을 하나도 남기지 않은 회원은 세지 않는다.
     *
     * @param rows [회원 ID, 천상 합, 마계 합, 인간 합]
     * @return 정세
     */
    static KingdomDTO compute(List<Object[]> rows) {
        double heaven = 0, abyss = 0, human = 0;
        int players = 0;
        for (Object[] row : rows) {
            double h = number(row[1]), a = number(row[2]), u = number(row[3]);
            double total = h + a + u;
            if (total <= 0) continue;
            heaven += h / total;
            abyss += a / total;
            human += u / total;
            players++;
        }
        if (players == 0) {
            return of(KingdomState.BALANCE, 0, 0, 0, 0);
        }
        double hs = heaven / players, as = abyss / players, us = human / players;
        return of(KingdomState.decide(hs, as, us), hs, as, us, players);
    }

    private static KingdomDTO of(KingdomState state, double heaven, double abyss, double human, int players) {
        return new KingdomDTO(state.name(), state.title(), state.description(), state.effects(),
                (int) Math.round(heaven * 100), (int) Math.round(abyss * 100), (int) Math.round(human * 100), players);
    }

    private static double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0;
    }
}
