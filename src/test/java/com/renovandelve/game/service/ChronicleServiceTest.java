package com.renovandelve.game.service;

import com.renovandelve.ai.dto.response.RunReviewDTO;
import com.renovandelve.ai.service.RunReviewService;
import com.renovandelve.game.domain.ChronicleChapterEntity;
import com.renovandelve.game.domain.gamestateentity.Difficulty;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.engine.Faction;
import com.renovandelve.game.repository.ChronicleChapterRepository;
import com.renovandelve.member.domain.MemberEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 레노바 연대기 규칙 테스트. 저장소는 목이라 DB 없이 돈다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChronicleServiceTest {

    @Mock private ChronicleChapterRepository repository;
    @InjectMocks private ChronicleService chronicleService;

    private GameStateEntity game;

    @BeforeEach
    void setUp() {
        MemberEntity member = mock(MemberEntity.class);
        when(member.getMemberId()).thenReturn(1L);
        game = GameStateEntity.builder().gameStateId(9L).member(member).currentStage(3).currentNodeId(4L)
                .heavenAffinity(5).difficulty(Difficulty.HARD).build();
    }

    private RunReviewDTO review(String chapterTitle, String chapter) {
        return new RunReviewDTO("칭호", "묘비명", "분석", List.of(), chapterTitle, chapter);
    }

    private ChronicleChapterEntity saved() {
        ArgumentCaptor<ChronicleChapterEntity> captor = ArgumentCaptor.forClass(ChronicleChapterEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("AI가 쓴 장을 다음 번호로 남기고, 결과·도달 지점·세력·난이도는 서버 값으로 채운다")
    void recordsTheAiChapter() {
        when(repository.countByMemberId(1L)).thenReturn(2L);

        chronicleService.record(game, false, review("금화를 쥔 손", "그는 금화를 쥐었다."));

        ChronicleChapterEntity chapter = saved();
        assertThat(chapter.getChapterNo()).isEqualTo(3);
        assertThat(chapter.getTitle()).isEqualTo("금화를 쥔 손");
        assertThat(chapter.getBody()).isEqualTo("그는 금화를 쥐었다.");
        assertThat(chapter.isCleared()).isFalse();
        assertThat(chapter.getStage()).isEqualTo((short) 3);
        assertThat(chapter.getNode()).isEqualTo((short) 4);
        assertThat(chapter.getFaction()).isEqualTo("HEAVEN");
        assertThat(chapter.getDifficulty()).isEqualTo("HARD");
    }

    @Test
    @DisplayName("회고가 없거나 장이 비어 있으면 서버가 사실만으로 장을 쓴다 (연대기에 빈 장이 생기지 않는다)")
    void fallsBackWhenTheAiWroteNothing() {
        chronicleService.record(game, false, null);
        ChronicleChapterEntity chapter = saved();
        assertThat(chapter.getTitle()).isEqualTo("스테이지 3의 침묵");
        assertThat(chapter.getBody()).contains("스테이지 3의 4번째 걸음", "천상의 방식에 기대어");
    }

    @Test
    @DisplayName("장 제목만 있고 본문이 비면 AI의 장으로 보지 않는다")
    void halfWrittenChapterFallsBack() {
        chronicleService.record(game, true, review("제목만", " "));
        assertThat(saved().getTitle()).isEqualTo(ChronicleService.fallbackTitle(true, 3));
    }

    @Test
    @DisplayName("한 원정에 장은 하나뿐이다")
    void oneChapterPerRun() {
        when(repository.existsByGameStateId(9L)).thenReturn(true);
        assertThat(chronicleService.record(game, false, review("a", "b"))).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("앞 이야기는 최근 3장을 오래된 것부터, 본문을 줄여 넘긴다")
    void previousChaptersAreOldestFirst() {
        ChronicleChapterEntity second = ChronicleChapterEntity.builder().chapterNo(2).title("둘").body("가".repeat(300))
                .difficulty("NORMAL").build();
        ChronicleChapterEntity third = ChronicleChapterEntity.builder().chapterNo(3).title("셋").body("다")
                .difficulty("NORMAL").build();
        when(repository.findTop3ByMemberIdOrderByChapterNoDesc(1L)).thenReturn(List.of(third, second));

        List<String> previous = chronicleService.previousChapters(1L);

        assertThat(previous).hasSize(2);
        assertThat(previous.get(0)).startsWith("제2장 「둘」").endsWith("…").hasSizeLessThan(180);
        assertThat(previous.get(1)).isEqualTo("제3장 「셋」 다");
    }

    @Test
    @DisplayName("클리어한 원정의 기본 장은 문 앞에서 닫힌다")
    void clearedFallback() {
        assertThat(ChronicleService.fallbackBody(true, 5, 10, Faction.ABYSS)).contains("마계의 방식에 기대어", "문 앞에");
    }

    @Test
    @DisplayName("회고 프롬프트에 앞선 장과 이번 원정의 선택이 들어간다")
    void reviewPromptCarriesTheStory() {
        String story = RunReviewService.story(List.of("제1장 「첫」 시작"), List.of("마계의 손을 잡았다"));
        assertThat(story).contains("[앞선 장]", "제1장 「첫」 시작", "[이번 원정의 선택]", "마계의 손을 잡았다");
        assertThat(RunReviewService.story(List.of(), List.of())).contains("이번이 연대기의 첫 장", "기록 없음");
    }
}
