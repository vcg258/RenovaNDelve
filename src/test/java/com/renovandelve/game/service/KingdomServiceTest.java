package com.renovandelve.game.service;

import com.renovandelve.game.dto.response.KingdomDTO;
import com.renovandelve.game.engine.KingdomState;
import com.renovandelve.game.repository.GameStateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 왕국의 저울 규칙 테스트. 저장소는 목이라 DB 없이 돈다.
 */
class KingdomServiceTest {

    private static Object[] row(long memberId, long heaven, long abyss, long human) {
        return new Object[]{memberId, heaven, abyss, human};
    }

    @Test
    @DisplayName("한 세력이 40% 이상이고 2위보다 10%p 앞서야 정세를 쥔다")
    void decideNeedsShareAndLead() {
        assertThat(KingdomState.decide(0.6, 0.2, 0.2)).isEqualTo(KingdomState.HEAVEN_RISING);
        assertThat(KingdomState.decide(0.2, 0.5, 0.3)).isEqualTo(KingdomState.ABYSS_WIDENING);
        assertThat(KingdomState.decide(0.1, 0.4, 0.5)).isEqualTo(KingdomState.HUMAN_RESOLVE);
        assertThat(KingdomState.decide(0.45, 0.4, 0.15)).isEqualTo(KingdomState.BALANCE); // 차이 부족
        assertThat(KingdomState.decide(0.35, 0.33, 0.32)).isEqualTo(KingdomState.BALANCE); // 비율 부족
    }

    @Test
    @DisplayName("한 사람이 한 표: 많이 플레이한 한 사람이 저울을 혼자 기울이지 못한다")
    void onePlayerOneVote() {
        // 마계만 30번 고른 사람 1명 vs 천상만 3번 고른 사람 2명 -> 천상 2/3
        KingdomDTO result = KingdomService.compute(List.of(row(1, 0, 30, 0), row(2, 3, 0, 0), row(3, 3, 0, 0)));
        assertThat(result.state()).isEqualTo("HEAVEN_RISING");
        assertThat(result.heaven()).isEqualTo(67);
        assertThat(result.abyss()).isEqualTo(33);
        assertThat(result.players()).isEqualTo(3);
    }

    @Test
    @DisplayName("선택이 하나도 없으면 균형이고, 선택이 없는 회원은 세지 않는다")
    void emptyIsBalance() {
        assertThat(KingdomService.compute(List.of()).state()).isEqualTo("BALANCE");
        KingdomDTO result = KingdomService.compute(List.of(row(1, 0, 0, 0), row(2, 0, 0, 4)));
        assertThat(result.players()).isEqualTo(1);
        assertThat(result.state()).isEqualTo("HUMAN_RESOLVE");
        assertThat(result.effects()).containsExactly("경험치 +15%");
    }

    @Test
    @DisplayName("결과는 10분 동안 다시 계산하지 않는다")
    void resultIsCachedForTenMinutes() {
        GameStateRepository repository = mock(GameStateRepository.class);
        List<Object[]> rows = new ArrayList<>();
        rows.add(row(1, 5, 0, 0));
        when(repository.sumAffinityByMemberSince(any())).thenReturn(rows);
        KingdomService service = new KingdomService(repository);
        Instant start = Instant.parse("2026-11-15T00:00:00Z");
        service.clock = Clock.fixed(start, ZoneId.of("UTC"));

        service.current();
        service.clock = Clock.fixed(start.plus(Duration.ofMinutes(9)), ZoneId.of("UTC"));
        service.current();
        verify(repository, times(1)).sumAffinityByMemberSince(any());

        service.clock = Clock.fixed(start.plus(Duration.ofMinutes(11)), ZoneId.of("UTC"));
        service.current();
        verify(repository, times(2)).sumAffinityByMemberSince(any());
    }

    @Test
    @DisplayName("계산이 실패하면 균형으로 본다 (원정 시작을 막지 않는다)")
    void failureMeansBalance() {
        GameStateRepository repository = mock(GameStateRepository.class);
        when(repository.sumAffinityByMemberSince(any())).thenThrow(new RuntimeException("DB down"));
        assertThat(new KingdomService(repository).currentState()).isEqualTo(KingdomState.BALANCE);
    }

    @Test
    @DisplayName("저장값이 없거나 모르는 값이면 효과 없음(null)")
    void parseUnknown() {
        assertThat(KingdomState.parse(null)).isNull();
        assertThat(KingdomState.parse("NOPE")).isNull();
        assertThat(KingdomState.parse("ABYSS_WIDENING")).isEqualTo(KingdomState.ABYSS_WIDENING);
    }
}
