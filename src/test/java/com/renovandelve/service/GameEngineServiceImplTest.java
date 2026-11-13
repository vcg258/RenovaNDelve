package com.renovandelve.service;

import com.renovandelve.domain.GameCharacterEntity;
import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.dto.MonsterDTO;
import com.renovandelve.dto.request.EncounterChoiceRequest;
import com.renovandelve.dto.response.AsyncStageResponseDTO;
import com.renovandelve.dto.response.Encounter;
import com.renovandelve.dto.response.NodeDTO;
import com.renovandelve.engine.EncounterChoice;
import com.renovandelve.engine.GameSession;
import com.renovandelve.repository.ConsumableRepository;
import com.renovandelve.repository.GameConsumableInventoryRepository;
import com.renovandelve.repository.GameRelicInventoryRepository;
import com.renovandelve.repository.GameStateRepository;
import com.renovandelve.repository.MonsterBaseStatusRepository;
import com.renovandelve.repository.RelicRepository;
import com.renovandelve.repository.SkillRepository;
import com.renovandelve.service.ai.MonsterRagService;
import com.renovandelve.service.ai.RunReviewService;
import com.renovandelve.service.redis.GameSessionService;
import com.renovandelve.service.redis.StageCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 게임 엔진의 노드 진행 규칙 테스트.
 *
 * 검증 대상은 "AI가 무엇을 돌려주든 서버가 지키는 규칙"이다.
 * AI(MonsterRagService)와 저장소는 모두 목으로 대체하므로 DB / Redis / OpenAI 없이 돈다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameEngineServiceImplTest {

    private static final long GAME_ID = 7L;
    private static final String CONVERSATION_ID = "game-7";

    @Mock private GameStateRepository gameStateRepository;
    @Mock private MonsterBaseStatusRepository monsterBaseStatusRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private RelicRepository relicRepository;
    @Mock private ConsumableRepository consumableRepository;
    @Mock private GameRelicInventoryRepository gameRelicInventoryRepository;
    @Mock private GameConsumableInventoryRepository gameConsumableInventoryRepository;
    @Mock private MonsterService monsterService;
    @Mock private MonsterRagService monsterRagService;
    @Mock private BattleService battleService;
    @Mock private BattleResultService battleResultService;
    @Mock private ShopService shopService;
    @Mock private GameSessionService gameSessionService;
    @Mock private StatusAssembler statusAssembler;
    @Mock private RunReviewService runReviewService;
    @Mock private ChatMemory chatMemory;
    @Mock private StageCacheService stageCacheService;

    @InjectMocks private GameEngineServiceImpl engine;

    private MemberEntity member;
    private GameSession session;

    @BeforeEach
    void setUp() {
        member = mock(MemberEntity.class);
        when(member.getMemberId()).thenReturn(1L);

        session = new GameSession();
        session.setGameStateId(GAME_ID);
        when(gameSessionService.getOrCreate(GAME_ID)).thenReturn(session);
        when(monsterBaseStatusRepository.findByStageAndDifficulty(any(Short.class), any(Short.class)))
                .thenReturn(Optional.empty());
    }

    /** 진행 중인 게임을 하나 만들어 저장소가 돌려주게 한다 */
    private GameStateEntity activeGame(int stage, long node) {
        GameCharacterEntity character = GameCharacterEntity.builder()
                .currentHp(50).maxHp(50).attack(10).defense(3).build();
        GameStateEntity gameState = GameStateEntity.builder()
                .gameStateId(GAME_ID).currentStage(stage).currentNodeId(node)
                .gameCharacter(new ArrayList<>(List.of(character)))
                .build();
        when(gameStateRepository.findByMember_MemberIdAndIsActiveTrue(1L)).thenReturn(Optional.of(gameState));
        return gameState;
    }

    private MonsterDTO monster(String name, int type) {
        return new MonsterDTO((long) name.hashCode(), name, (short) type, 30, "설명");
    }

    private EncounterChoice choice(String faction, String effect) {
        EncounterChoice choice = new EncounterChoice();
        choice.setFaction(faction);
        choice.setLabel("선택지");
        choice.setEffect(effect);
        return choice;
    }

    /* ==================== 보스 노드 ==================== */

    @Test
    @DisplayName("보스 노드에서는 AI가 조우를 돌려줘도 보스전으로 강제 전환한다")
    void bossNodeForcesBossBattle() {
        activeGame(1, 9); // 다음이 10번째 노드
        when(monsterRagService.chat(1, 10, CONVERSATION_ID))
                .thenReturn(AsyncStageResponseDTO.encounter("AI가 쓴 서사", new Encounter("상황", List.of("a", "b", "c"))));
        when(monsterService.getRandomMonsterBoss()).thenReturn(List.of(monster("악마화된 왕", 2)));

        NodeDTO node = engine.nextNode(member, "SAFE");

        assertThat(node.nodeType()).isEqualTo("BOSS");
        assertThat(node.story()).isEqualTo("AI가 쓴 서사"); // 서사는 살리고 종류만 바꾼다
        assertThat(session.getPhase()).isEqualTo(GameSession.PHASE_BATTLE);
        assertThat(session.getMonsters()).hasSize(1);
        assertThat(session.getMonsters().get(0).getMonsterType()).isEqualTo((short) 2);
    }

    /* ==================== AI 폴백 ==================== */

    @Test
    @DisplayName("AI 호출이 실패해도 랜덤 몬스터와 기본 서사로 노드가 만들어진다")
    void aiFailureFallsBackToRandomMonsters() {
        activeGame(1, 9);
        when(monsterRagService.chat(anyInt(), anyInt(), anyString())).thenThrow(new RuntimeException("OpenAI 502"));
        when(monsterService.getRandomMonsterBoss()).thenReturn(List.of(monster("유혹의 마왕", 2)));

        NodeDTO node = engine.nextNode(member, "SAFE");

        assertThat(node.nodeType()).isEqualTo("BOSS");
        assertThat(node.story()).isNotBlank();
        assertThat(session.getMonsters()).extracting("name").containsExactly("유혹의 마왕");
    }

    @Test
    @DisplayName("AI 응답이 시간 상한을 넘기면 기다리지 않고 폴백한다")
    void aiTimeoutFallsBack() {
        activeGame(1, 9);
        engine.aiNodeBudgetSeconds = 1;
        when(monsterRagService.chat(anyInt(), anyInt(), anyString())).thenAnswer(invocation -> {
            Thread.sleep(20_000);
            return AsyncStageResponseDTO.battle("늦게 도착한 서사", List.of(monster("늦은 몬스터", 2)));
        });
        when(monsterService.getRandomMonsterBoss()).thenReturn(List.of(monster("뼈의 군주", 2)));

        long started = System.currentTimeMillis();
        NodeDTO node = engine.nextNode(member, "SAFE");

        assertThat(System.currentTimeMillis() - started).isLessThan(10_000);
        assertThat(node.story()).isNotEqualTo("늦게 도착한 서사");
        assertThat(session.getMonsters()).extracting("name").containsExactly("뼈의 군주");
    }

    /* ==================== 멱등 / 단계 검증 ==================== */

    @Test
    @DisplayName("이미 전투 중이면 AI를 다시 부르지 않고 현재 노드를 그대로 돌려준다")
    void nextNodeIsIdempotentDuringBattle() {
        GameStateEntity gameState = activeGame(1, 3);
        session.setPhase(GameSession.PHASE_BATTLE);
        session.setNodeType("BATTLE");
        session.setStory("진행 중인 전투의 서사");

        NodeDTO node = engine.nextNode(member, "DANGER");

        assertThat(node.nodeType()).isEqualTo("BATTLE");
        assertThat(node.story()).isEqualTo("진행 중인 전투의 서사");
        assertThat(gameState.getCurrentNodeId()).isEqualTo(3L); // 노드가 전진하지 않는다
        verify(monsterRagService, never()).chat(anyInt(), anyInt(), anyString());
    }

    @Test
    @DisplayName("보상을 고르는 중에는 다음 노드로 갈 수 없다")
    void nextNodeRejectedOutsideExplorePhase() {
        activeGame(1, 3);
        session.setPhase(GameSession.PHASE_REWARD);

        assertThatThrownBy(() -> engine.nextNode(member, "SAFE"))
                .isInstanceOf(IllegalStateException.class);
        verify(monsterRagService, never()).chat(anyInt(), anyInt(), anyString());
    }

    /* ==================== 난이도는 서버 책임 ==================== */

    @Test
    @DisplayName("스테이지 1에서는 AI가 엘리트를 여럿 골라도 한 마리만 나온다")
    void elitesAreLimitedInEarlyStages() {
        activeGame(1, 1);
        when(monsterRagService.chat(1, 2, CONVERSATION_ID)).thenReturn(AsyncStageResponseDTO.battle("서사",
                List.of(monster("기사단장", 1), monster("망령 기사", 1), monster("고블린", 0))));

        NodeDTO node = engine.nextNode(member, "SAFE");

        assertThat(node.nodeType()).isEqualTo("ELITE");
        assertThat(session.getMonsters()).extracting("name").containsExactly("기사단장", "고블린");
    }

    @Test
    @DisplayName("몬스터 능력치는 AI가 아니라 서버가 정한다: 등급이 높을수록 강하다")
    void monsterStatsComeFromServer() {
        activeGame(3, 1);
        when(monsterRagService.chat(3, 2, CONVERSATION_ID)).thenReturn(AsyncStageResponseDTO.battle("서사",
                List.of(monster("고블린", 0), monster("기사단장", 1))));

        engine.nextNode(member, "SAFE");

        var normal = session.getMonsters().get(0);
        var elite = session.getMonsters().get(1);
        assertThat(normal.getAtk()).isPositive();
        assertThat(elite.getAtk()).isGreaterThan(normal.getAtk());
        assertThat(elite.getDef()).isGreaterThanOrEqualTo(normal.getDef());
        assertThat(normal.getCurrentHp()).isEqualTo(normal.getMaxHp());
    }

    /* ==================== 조우 ==================== */

    @Test
    @DisplayName("조우 선택지는 항상 [천상, 마계, 인간] 순서의 3개이고 문구만 AI의 것이다")
    void encounterChoicesFollowFactionOrder() {
        activeGame(1, 1);
        when(monsterRagService.chat(1, 2, CONVERSATION_ID)).thenReturn(AsyncStageResponseDTO.encounter("서사",
                new Encounter("상황", List.of("빛에 맡긴다", "손을 잡는다", "스스로 연다"))));

        NodeDTO node = engine.nextNode(member, "DANGER");

        assertThat(node.nodeType()).isEqualTo("ENCOUNTER");
        assertThat(session.getPhase()).isEqualTo(GameSession.PHASE_ENCOUNTER);
        assertThat(session.getChoices()).extracting("faction").containsExactly("HEAVEN", "ABYSS", "HUMAN");
        assertThat(session.getChoices()).extracting("label").containsExactly("빛에 맡긴다", "손을 잡는다", "스스로 연다");
        assertThat(session.getPath()).isEqualTo("SAFE"); // 조우에는 갈림길이 영향을 주지 않는다
    }

    @Test
    @DisplayName("AI가 선택지 문구를 주지 못해도 기본 문구로 3개가 만들어진다")
    void encounterWithoutSelectionsStillHasThreeChoices() {
        activeGame(1, 1);
        when(monsterRagService.chat(1, 2, CONVERSATION_ID)).thenReturn(AsyncStageResponseDTO.encounter("서사",
                new Encounter("상황", List.of())));

        engine.nextNode(member, "SAFE");

        assertThat(session.getChoices()).hasSize(3);
        assertThat(session.getChoices()).allSatisfy(choice -> assertThat(choice.getLabel()).isNotBlank());
    }

    @Test
    @DisplayName("범위를 벗어난 선택지 번호는 거부한다")
    void invalidEncounterChoiceIsRejected() {
        activeGame(1, 2);
        session.setPhase(GameSession.PHASE_ENCOUNTER);
        session.setChoices(new ArrayList<>(List.of(choice("HEAVEN", "NOTHING"))));

        assertThatThrownBy(() -> engine.chooseEncounter(member, new EncounterChoiceRequest(5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("조우에서 고른 세력이 성향으로 쌓이고, 선택이 AI 대화 기록에 남고, 미리 만든 다음 노드는 버려진다")
    void encounterChoiceIsRememberedAndInvalidatesPrefetch() {
        GameStateEntity gameState = activeGame(1, 2);
        session.setPhase(GameSession.PHASE_ENCOUNTER);
        session.setStory("갈라진 제단 앞");
        session.setChoices(new ArrayList<>(List.of(choice("HEAVEN", "NOTHING"), choice("ABYSS", "NOTHING"))));

        engine.chooseEncounter(member, new EncounterChoiceRequest(1));

        assertThat(gameState.getAbyssAffinity()).isEqualTo(1);
        assertThat(gameState.getHeavenAffinity()).isZero();
        verify(chatMemory).add(eq(CONVERSATION_ID), any(Message.class));
        verify(stageCacheService).delete(CONVERSATION_ID, 1, 3);
    }
}
