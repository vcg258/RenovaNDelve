package com.renovandelve.game.service;

import com.renovandelve.catalog.service.MonsterService;

import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyDouble;
import com.renovandelve.game.dto.request.SkillDiscardRequest;
import com.renovandelve.game.domain.GameCharacterEntity;
import com.renovandelve.game.domain.GameSkillSlotEntity;
import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.catalog.domain.skillentity.Target;
import com.renovandelve.game.dto.request.RewardSelectRequest;
import com.renovandelve.game.dto.request.SkillUseRequest;
import com.renovandelve.game.dto.response.TurnResultDTO;
import com.renovandelve.game.engine.RewardOptions;
import com.renovandelve.game.engine.SessionMonster;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.catalog.dto.MonsterDTO;
import com.renovandelve.game.dto.request.EncounterChoiceRequest;
import com.renovandelve.ai.dto.response.AsyncStageResponseDTO;
import com.renovandelve.ai.dto.response.Encounter;
import com.renovandelve.game.dto.response.NodeDTO;
import com.renovandelve.game.engine.EncounterChoice;
import com.renovandelve.game.engine.GameSession;
import com.renovandelve.catalog.repository.ConsumableRepository;
import com.renovandelve.game.repository.GameConsumableInventoryRepository;
import com.renovandelve.game.repository.GameRelicInventoryRepository;
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.catalog.repository.MonsterBaseStatusRepository;
import com.renovandelve.catalog.repository.RelicRepository;
import com.renovandelve.catalog.repository.SkillRepository;
import com.renovandelve.ai.service.AsyncPrefetchService;
import com.renovandelve.ai.service.MonsterRagService;
import com.renovandelve.ai.service.WraithVoiceService;
import com.renovandelve.game.domain.WraithEntity;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.test.util.ReflectionTestUtils;
import com.renovandelve.ai.service.RunReviewService;
import com.renovandelve.ai.dto.response.RunReviewDTO;
import com.renovandelve.game.service.redis.GameSessionService;
import com.renovandelve.ai.service.StageCacheService;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
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
    @Mock private SkillSlotService skillSlotService;
    @Mock private WraithService wraithService;
    @Mock private WraithVoiceService wraithVoiceService;
    @Mock private AsyncPrefetchService asyncPrefetchService;
    @Mock private ChronicleService chronicleService;

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

    /* ==================== 갈림길 ==================== */

    private NodeDTO enterBattle(String path) {
        activeGame(1, 1);
        session.setPhase(GameSession.PHASE_EXPLORE);
        when(monsterRagService.chat(1, 2, CONVERSATION_ID)).thenReturn(AsyncStageResponseDTO.battle("서사",
                List.of(monster("고블린", 0), monster("해골 병사", 0))));
        return engine.nextNode(member, path);
    }

    @Test
    @DisplayName("위험한 길: 적 공격력과 HP가 25% 오르고, 무엇이 적용됐는지 노드에 실려 나간다")
    void dangerPathStrengthensEnemies() {
        enterBattle("SAFE");
        int safeAtk = session.getMonsters().get(0).getAtk();
        assertThat(session.getPathNote()).isNull();

        NodeDTO node = enterBattle("DANGER");
        var danger = session.getMonsters().get(0);

        assertThat(danger.getAtk()).isEqualTo((int) Math.round(safeAtk * 1.25));
        // HP는 개체마다 +-10% 편차가 있으므로 범위로 본다: 기본 65 x 0.9 x 1.25 ~ 65 x 1.1 x 1.25
        assertThat(danger.getMaxHp()).isBetween(72, 90).isEqualTo(danger.getCurrentHp());
        assertThat(session.getPath()).isEqualTo("DANGER");
        assertThat(node.path()).isEqualTo("DANGER");
        assertThat(node.pathNote()).contains("위험한 길").contains("+25%");
    }

    @Test
    @DisplayName("은밀한 길: 적 전원이 기절 1스택으로 시작하고 능력치는 그대로다")
    void ambushPathStunsEveryEnemy() {
        enterBattle("SAFE");
        int safeAtk = session.getMonsters().get(0).getAtk();

        NodeDTO node = enterBattle("AMBUSH");

        assertThat(session.getMonsters()).hasSize(2).allSatisfy(monster -> {
            assertThat(monster.getBuffs()).containsEntry(BattleServiceImpl.BUFF_STUN, 1);
            assertThat(monster.getAtk()).isEqualTo(safeAtk);
        });
        assertThat(node.monsters()).allSatisfy(monster -> assertThat(monster.buffs()).containsKey("STUN"));
        assertThat(node.pathNote()).contains("기습");
    }

    @Test
    @DisplayName("알 수 없는 길 값은 기본 길로 처리한다")
    void unknownPathFallsBackToSafe() {
        NodeDTO node = enterBattle("TELEPORT");

        assertThat(session.getPath()).isEqualTo("SAFE");
        assertThat(node.pathNote()).isNull();
    }

    @Test
    @DisplayName("조우 노드에서는 길 효과가 없고, 고른 길이 무시됐다는 안내가 나간다")
    void encounterTellsPathWasIgnored() {
        activeGame(1, 1);
        when(monsterRagService.chat(1, 2, CONVERSATION_ID)).thenReturn(AsyncStageResponseDTO.encounter("서사",
                new Encounter("상황", List.of("a", "b", "c"))));

        NodeDTO node = engine.nextNode(member, "DANGER");

        assertThat(node.path()).isNull();
        assertThat(node.pathNote()).contains("위험한 길").contains("적용되지 않는다");
    }

    @Test
    @DisplayName("새로고침(재진입)해도 적용된 길과 안내가 그대로 복원된다")
    void pathNoteSurvivesReentry() {
        enterBattle("DANGER");

        NodeDTO again = engine.nextNode(member, "SAFE"); // 전투 중 재진입: 멱등

        assertThat(again.path()).isEqualTo("DANGER");
        assertThat(again.pathNote()).contains("위험한 길");
    }

    /** 스킬 한 번으로 전투가 끝나게 해 놓고 승리 처리를 돌린다 */
    private TurnResultDTO winBattleOn(String path) {
        GameCharacterEntity character = GameCharacterEntity.builder()
                .currentHp(50).maxHp(50).attack(10).defense(3).build();
        SkillEntity skill = SkillEntity.builder().skillId(1L).name("기본 공격").target(Target.EnemySingle).build();
        GameSkillSlotEntity slot = GameSkillSlotEntity.builder().slotId(1L).slotIndex(0).skill(skill).build();
        GameStateEntity gameState = GameStateEntity.builder()
                .gameStateId(GAME_ID).currentStage(1).currentNodeId(3L)
                .gameCharacter(new ArrayList<>(List.of(character)))
                .gameSkillSlot(new ArrayList<>(List.of(slot)))
                .build();
        when(gameStateRepository.findByMember_MemberIdAndIsActiveTrue(1L)).thenReturn(Optional.of(gameState));

        SessionMonster monster = new SessionMonster();
        monster.setName("고블린");
        monster.setMaxHp(10);
        monster.setCurrentHp(10);
        monster.setScoreReward(30);
        session.setPhase(GameSession.PHASE_BATTLE);
        session.setNodeType("BATTLE");
        session.setPath(path);
        session.setMonsters(new ArrayList<>(List.of(monster)));
        when(battleService.isAllMonsterDead(any())).thenReturn(true);

        TurnResultDTO result = engine.useSkill(member, new SkillUseRequest(1L, List.of()));
        lastGame = gameState;
        return result;
    }

    private GameStateEntity lastGame;

    @Test
    @DisplayName("갈림길의 보상 배율이 점수와 골드에 실제로 반영되고, 승리 로그에 드러난다")
    void pathRewardMultiplierIsApplied() {
        // 몬스터 점수 30 + 스테이지 1 보정 10 = 40, 골드는 그 1/3 = 13 (보통 난이도 배율 1.0)
        TurnResultDTO safe = winBattleOn("SAFE");
        assertThat(lastGame.getScore()).isEqualTo(40);
        assertThat(lastGame.getGold()).isEqualTo(13);
        assertThat(safe.log()).noneMatch(line -> line.contains("길"));

        TurnResultDTO danger = winBattleOn("DANGER");
        assertThat(lastGame.getScore()).isEqualTo(60);
        assertThat(lastGame.getGold()).isEqualTo(19);
        assertThat(danger.log()).anyMatch(line -> line.contains("위험한 길") && line.contains("+50%"));

        TurnResultDTO ambush = winBattleOn("AMBUSH");
        assertThat(lastGame.getScore()).isEqualTo(28);
        assertThat(ambush.log()).anyMatch(line -> line.contains("은밀한 길") && line.contains("-30%"));
    }

    @Test
    @DisplayName("위험한 길로 이기면 유물 후보가 하나 더 나온다")
    void dangerPathAddsRelicOption() {
        List<RelicEntity> pool = List.of(
                RelicEntity.builder().relicId(1L).name("a").build(),
                RelicEntity.builder().relicId(2L).name("b").build(),
                RelicEntity.builder().relicId(3L).name("c").build());
        when(relicRepository.findByRelicNotOwner(GAME_ID)).thenReturn(pool);

        winBattleOn("SAFE");
        assertThat(session.getReward().getRelicIds()).hasSize(1);

        winBattleOn("DANGER");
        assertThat(session.getReward().getRelicIds()).hasSize(2);
    }

    /* ==================== 단계별 가호의 보상 ==================== */

    @Test
    @DisplayName("마계 가호 3단계: 전투 보상의 유물 후보가 하나 늘어난다")
    void thirdTierBlessingAddsRewardOption() {
        List<RelicEntity> pool = List.of(
                RelicEntity.builder().relicId(1L).name("a").build(),
                RelicEntity.builder().relicId(2L).name("b").build(),
                RelicEntity.builder().relicId(3L).name("c").build());
        when(relicRepository.findByRelicNotOwner(GAME_ID)).thenReturn(pool);

        winBattleOn("SAFE");
        assertThat(session.getReward().getRelicIds()).hasSize(1);

        // 같은 전투를 마계 성향 9로 다시 치른다
        GameCharacterEntity character = GameCharacterEntity.builder().currentHp(50).maxHp(50).attack(10).defense(3).build();
        SkillEntity skill = SkillEntity.builder().skillId(1L).name("기본 공격").target(Target.EnemySingle).build();
        GameStateEntity abyss = GameStateEntity.builder()
                .gameStateId(GAME_ID).currentStage(1).currentNodeId(3L).abyssAffinity(9)
                .gameCharacter(new ArrayList<>(List.of(character)))
                .gameSkillSlot(new ArrayList<>(List.of(
                        GameSkillSlotEntity.builder().slotId(1L).slotIndex(0).skill(skill).build())))
                .build();
        when(gameStateRepository.findByMember_MemberIdAndIsActiveTrue(1L)).thenReturn(Optional.of(abyss));
        session.setPhase(GameSession.PHASE_BATTLE);

        engine.useSkill(member, new SkillUseRequest(1L, List.of()));

        assertThat(session.getReward().getRelicIds()).hasSize(2);
    }

    /* ==================== 스킬 교체 ==================== */

    @Test
    @DisplayName("스킬 보상을 고르면 버릴 슬롯이 그대로 슬롯 규칙에 전달되고 상점 단계로 넘어간다")
    void skillRewardPassesReplaceSlot() {
        GameStateEntity gameState = activeGame(1, 3);
        session.setPhase(GameSession.PHASE_REWARD);
        RewardOptions options = new RewardOptions();
        options.setSkillIds(new ArrayList<>(List.of(42L)));
        session.setReward(options);

        engine.selectReward(member, new RewardSelectRequest("SKILL", 42L, 5L));

        verify(skillSlotService).learn(gameState, 42L, 5L);
        assertThat(session.getPhase()).isEqualTo(GameSession.PHASE_SHOP);
    }

    @Test
    @DisplayName("슬롯이 가득 차 스킬을 배우지 못하면 보상 단계에 그대로 머문다 (버릴 스킬을 고른 뒤 다시 요청할 수 있다)")
    void fullSlotsKeepRewardPhase() {
        GameStateEntity gameState = activeGame(1, 3);
        session.setPhase(GameSession.PHASE_REWARD);
        RewardOptions options = new RewardOptions();
        options.setSkillIds(new ArrayList<>(List.of(42L)));
        session.setReward(options);
        when(skillSlotService.learn(gameState, 42L, null))
                .thenThrow(new IllegalStateException(SkillSlotService.FULL_MESSAGE));

        assertThatThrownBy(() -> engine.selectReward(member, new RewardSelectRequest("SKILL", 42L, null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(session.getPhase()).isEqualTo(GameSession.PHASE_REWARD);
        assertThat(session.getReward()).isNotNull();
    }

    @Test
    @DisplayName("보상 후보에 없는 스킬은 교체 요청으로도 얻을 수 없다")
    void rewardSkillMustBeOffered() {
        activeGame(1, 3);
        session.setPhase(GameSession.PHASE_REWARD);
        session.setReward(new RewardOptions());

        assertThatThrownBy(() -> engine.selectReward(member, new RewardSelectRequest("SKILL", 42L, 5L)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(skillSlotService, never()).learn(any(), any(), any());
    }

    /* ==================== 숨 고르기 / 도망치기 / 스킬 버리기 ==================== */

    private GameSkillSlotEntity slot(long slotId, int coolTime) {
        return GameSkillSlotEntity.builder().slotId(slotId).slotIndex((int) slotId).coolTime(coolTime)
                .skill(SkillEntity.builder().skillId(slotId).name("스킬" + slotId).cooldown(3).build()).build();
    }

    /** 전투 중인 게임. 슬롯별 남은 쿨타임을 받는다 */
    private GameStateEntity battleGame(String nodeType, int... coolTimes) {
        List<GameSkillSlotEntity> slots = new ArrayList<>();
        for (int i = 0; i < coolTimes.length; i++) slots.add(slot(i + 1, coolTimes[i]));
        GameCharacterEntity character = GameCharacterEntity.builder()
                .currentHp(50).maxHp(50).attack(10).defense(3).build();
        GameStateEntity gameState = GameStateEntity.builder()
                .gameStateId(GAME_ID).currentStage(1).currentNodeId(3L)
                .gameCharacter(new ArrayList<>(List.of(character)))
                .gameSkillSlot(slots)
                .gameConsumableInventory(new ArrayList<>())
                .build();
        when(gameStateRepository.findByMember_MemberIdAndIsActiveTrue(1L)).thenReturn(Optional.of(gameState));

        SessionMonster foe = new SessionMonster();
        foe.setName("광증의 기사");
        foe.setMaxHp(40);
        foe.setCurrentHp(40);
        session.setPhase(GameSession.PHASE_BATTLE);
        session.setNodeType(nodeType);
        session.setMonsters(new ArrayList<>(List.of(foe)));
        return gameState;
    }

    @Test
    @DisplayName("쓸 수 있는 스킬이 하나라도 있으면 숨 고르기를 할 수 없다")
    void passIsRejectedWhileASkillIsReady() {
        battleGame("BATTLE", 2, 0, 1);

        assertThatThrownBy(() -> engine.passTurn(member)).isInstanceOf(IllegalStateException.class);
        verify(battleService, never()).monsterAttackDamage(any(), anyInt(), anyInt(), anyDouble(), any());
    }

    @Test
    @DisplayName("모든 스킬이 쿨타임이면 숨 고르기로 턴을 넘기고, 적은 방어 없이 공격한다")
    void passEndsTheTurnWhenEverySkillIsCooling() {
        GameStateEntity gameState = battleGame("BATTLE", 2, 3);
        when(battleService.monsterAttackDamage(any(), anyInt(), anyInt(), anyDouble(), any())).thenReturn(7);

        TurnResultDTO result = engine.passTurn(member);

        assertThat(result.result()).isEqualTo("CONTINUE");
        assertThat(gameState.getGameCharacter().get(0).getCurrentHp()).isEqualTo(43);
        // 쿨타임 진행을 두 번 맡긴다 (방어 태세는 한 번)
        verify(battleService, times(2)).updateCoolTimes(any(), any(), anyInt());
        verify(battleService).monsterAttackDamage(any(), anyInt(), anyInt(), eq(1.0), any());
    }

    @Test
    @DisplayName("보스전에서는 달아날 수 없다")
    void cannotFleeFromBoss() {
        battleGame("BOSS", 0);

        assertThatThrownBy(() -> engine.flee(member)).isInstanceOf(IllegalStateException.class);
        assertThat(session.getPhase()).isEqualTo(GameSession.PHASE_BATTLE);
    }

    @Test
    @DisplayName("달아나면 적이 한 번 공격하고, 보상 없이 탐험 단계로 돌아가며, 그 사실이 대화 기록에 남는다")
    void fleeEndsBattleWithoutReward() {
        GameStateEntity gameState = battleGame("ELITE", 2, 1);
        when(battleService.monsterAttackDamage(any(), anyInt(), anyInt(), anyDouble(), any())).thenReturn(10);

        TurnResultDTO result = engine.flee(member);

        assertThat(result.result()).isEqualTo("FLED");
        assertThat(result.reward()).isNull();
        assertThat(result.phase()).isEqualTo(GameSession.PHASE_EXPLORE);
        assertThat(session.getMonsters()).isEmpty();
        assertThat(gameState.getGameCharacter().get(0).getCurrentHp()).isEqualTo(40);
        assertThat(gameState.getScore()).isZero();
        assertThat(gameState.getGameSkillSlot()).allSatisfy(slot -> assertThat(slot.getCoolTime()).isZero());
        verify(chatMemory).add(eq(CONVERSATION_ID), any(Message.class));
    }

    @Test
    @DisplayName("달아나다 쓰러지면 패배다")
    void fleeingCanBeFatal() {
        battleGame("BATTLE", 0);
        when(battleService.monsterAttackDamage(any(), anyInt(), anyInt(), anyDouble(), any())).thenReturn(999);

        TurnResultDTO result = engine.flee(member);

        assertThat(result.result()).isEqualTo("LOSE");
        assertThat(session.getPhase()).isEqualTo(GameSession.PHASE_GAMEOVER);
    }

    @Test
    @DisplayName("전투 중에는 스킬을 버릴 수 없다")
    void cannotDiscardSkillDuringBattle() {
        battleGame("BATTLE", 0, 0, 0);

        assertThatThrownBy(() -> engine.discardSkill(member, new SkillDiscardRequest(2L)))
                .isInstanceOf(IllegalStateException.class);
        verify(skillSlotService, never()).discard(any(), any());
    }

    @Test
    @DisplayName("탐험 중에는 스킬 버리기를 슬롯 규칙에 맡긴다")
    void discardSkillDelegatesOutsideBattle() {
        GameStateEntity gameState = activeGame(1, 3);

        engine.discardSkill(member, new SkillDiscardRequest(2L));

        verify(skillSlotService).discard(gameState, 2L);
    }

    /* ==================== 전생의 망령 ==================== */

    private WraithEntity wraith(long id, int stage, int node) {
        WraithEntity wraith = WraithEntity.builder()
                .memberId(1L).sourceGameStateId(3L).stage(stage).node(node).level(4)
                .killerName("광증의 기사").skills(List.of("화염구", "출혈 베기")).relicIds(List.of(77L))
                .memories(List.of("마계의 손을 잡았다")).build();
        ReflectionTestUtils.setField(wraith, "wraithId", id);
        return wraith;
    }

    @Test
    @DisplayName("망령이 기다리는 자리에 오면 AI가 고른 적 대신 망령이 나타나고, 능력치는 서버의 강적 기준치를 따른다")
    void wraithReplacesTheNodeWhereYouFell() {
        activeGame(2, 4); // 다음이 5번째 노드
        when(wraithService.waitingAt(1L, GAME_ID, 2, 5)).thenReturn(Optional.of(wraith(99L, 2, 5)));
        when(wraithVoiceService.appearance(any())).thenReturn("안개 속의 당신이 말한다.");

        NodeDTO node = engine.nextNode(member, "SAFE");

        assertThat(node.nodeType()).isEqualTo("WRAITH");
        assertThat(node.story()).isEqualTo("안개 속의 당신이 말한다.");
        assertThat(session.getWraithId()).isEqualTo(99L);
        SessionMonster foe = session.getMonsters().get(0);
        assertThat(foe.getName()).isEqualTo(WraithService.WRAITH_NAME);
        assertThat(foe.getMonsterType()).isEqualTo((short) 1);
        assertThat(foe.getMoves()).containsExactly("화염구", "출혈 베기");
        // 기준 능력치가 없을 때의 계산: (40 + 2 * 25) = 90, 강적 1.6배(편차 ±10%) 뒤 망령 1.25배
        assertThat(foe.getMaxHp()).isBetween((int) (90 * 1.6 * 0.9 * 1.25) - 1, (int) (90 * 1.6 * 1.1 * 1.25) + 1);
        verify(monsterRagService, never()).chat(anyInt(), anyInt(), anyString());
        verify(asyncPrefetchService).prefetchNextStage(2, 6, CONVERSATION_ID);
    }

    @Test
    @DisplayName("보스 노드에서는 망령을 찾지 않는다")
    void wraithNeverTakesTheBossNode() {
        activeGame(1, 9);
        when(monsterRagService.chat(1, 10, CONVERSATION_ID))
                .thenReturn(AsyncStageResponseDTO.battle("서사", List.of(monster("악마화된 왕", 2))));

        engine.nextNode(member, "SAFE");

        verify(wraithService, never()).waitingAt(any(), any(), anyInt(), anyInt());
        assertThat(session.getNodeType()).isEqualTo("BOSS");
    }

    @Test
    @DisplayName("망령을 쓰러뜨리면 처치로 기록되고, 그 원정의 유물이 보상 후보 맨 앞에 돌아온다")
    void slainWraithReturnsARelic() {
        when(relicRepository.findByRelicNotOwner(GAME_ID)).thenReturn(List.of(
                RelicEntity.builder().relicId(1L).name("a").build(),
                RelicEntity.builder().relicId(2L).name("b").build(),
                RelicEntity.builder().relicId(3L).name("c").build()));
        when(wraithService.slay(eq(99L), any())).thenReturn(Optional.of(77L));

        TurnResultDTO result = winBattleOnWraith(99L);

        assertThat(session.getReward().getRelicIds()).hasSize(3).first().isEqualTo(77L); // 강적 2개 + 돌아온 1개
        assertThat(session.getWraithId()).isNull();
        assertThat(result.log()).anyMatch(line -> line.contains("망령이 흩어진다"));
    }

    /** 일반 전투로 게임을 세운 뒤, 같은 게임에서 망령전을 이긴다 */
    private TurnResultDTO winBattleOnWraith(Long wraithId) {
        winBattleOn("SAFE");
        session.setWraithId(wraithId);
        session.setNodeType("WRAITH");
        session.setPhase(GameSession.PHASE_BATTLE);
        SessionMonster monster = new SessionMonster();
        monster.setName(WraithService.WRAITH_NAME);
        monster.setMaxHp(10);
        monster.setCurrentHp(10);
        monster.setMonsterType((short) 1);
        session.setMonsters(new ArrayList<>(List.of(monster)));
        return engine.useSkill(member, new SkillUseRequest(1L, List.of()));
    }

    @Test
    @DisplayName("망령에게서 달아나면 도주 횟수가 쌓이고, 이번 전투의 망령 표시는 지워진다")
    void fleeingFromWraithCountsAnEscape() {
        battleGame("WRAITH", 1);
        session.setWraithId(99L);
        when(battleService.monsterAttackDamage(any(), anyInt(), anyInt(), anyDouble(), any())).thenReturn(5);
        when(wraithService.escape(99L)).thenReturn(false);

        TurnResultDTO result = engine.flee(member);

        assertThat(result.result()).isEqualTo("FLED");
        verify(wraithService).escape(99L);
        assertThat(session.getWraithId()).isNull();
        assertThat(result.log()).anyMatch(line -> line.contains("같은 자리에서 기다릴 것"));
    }

    @Test
    @DisplayName("쓰러지면 쓰러뜨린 적과 최근 선택 3줄을 담아 망령을 남긴다")
    void defeatLeavesAWraith() {
        GameStateEntity gameState = battleGame("BATTLE", 0);
        when(battleService.monsterAttackDamage(any(), anyInt(), anyInt(), anyDouble(), any())).thenReturn(999);
        when(chatMemory.get(CONVERSATION_ID)).thenReturn(List.of(
                new UserMessage("[플레이어의 선택] 첫째"),
                new UserMessage("서사 요청"),
                new UserMessage("[플레이어의 선택] 둘째"),
                new UserMessage("[플레이어의 선택] 셋째"),
                new UserMessage("[플레이어의 선택] 넷째")));

        engine.flee(member);

        verify(wraithService).recordDeath(gameState, "광증의 기사", List.of("둘째", "셋째", "넷째"));
    }

    @Test
    @DisplayName("망령 기록이 실패해도 원정은 정상적으로 끝난다")
    void wraithFailureDoesNotBlockGameOver() {
        battleGame("BATTLE", 0);
        when(battleService.monsterAttackDamage(any(), anyInt(), anyInt(), anyDouble(), any())).thenReturn(999);
        when(wraithService.recordDeath(any(), any(), any())).thenThrow(new RuntimeException("DB down"));

        TurnResultDTO result = engine.flee(member);

        assertThat(result.result()).isEqualTo("LOSE");
        assertThat(session.getPhase()).isEqualTo(GameSession.PHASE_GAMEOVER);
    }

    /* ==================== 레노바 연대기 ==================== */

    @Test
    @DisplayName("회고를 만들 때 앞선 장과 이번 원정의 선택을 넘기고, 받은 회고로 연대기의 장을 남긴다")
    void reviewWritesTheNextChapter() {
        GameStateEntity gameState = GameStateEntity.builder().gameStateId(GAME_ID).member(member).build();
        when(gameStateRepository.findTopByMember_MemberIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(gameState));
        session.setPhase(GameSession.PHASE_GAMEOVER);
        when(gameSessionService.get(GAME_ID)).thenReturn(session);
        when(chronicleService.previousChapters(1L)).thenReturn(List.of("제1장 「첫」 시작"));
        when(chatMemory.get(CONVERSATION_ID)).thenReturn(List.of(new UserMessage("[플레이어의 선택] 마계의 손을 잡았다")));
        RunReviewDTO review = new RunReviewDTO("칭호", "묘비", "분석", List.of(), "둘째 장", "그는 내려갔다.");
        when(runReviewService.review(gameState, session, false, List.of("제1장 「첫」 시작"), List.of("마계의 손을 잡았다")))
                .thenReturn(review);

        assertThat(engine.getRunReview(member)).isSameAs(review);
        verify(chronicleService).record(gameState, false, review);

        engine.getRunReview(member); // 두 번째 조회는 세션에 남은 회고를 돌려준다
        verify(runReviewService, times(1)).review(any(), any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("연대기 기록이 실패해도 회고는 그대로 돌려준다")
    void chronicleFailureDoesNotHideTheReview() {
        GameStateEntity gameState = GameStateEntity.builder().gameStateId(GAME_ID).member(member).build();
        when(gameStateRepository.findTopByMember_MemberIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(gameState));
        session.setPhase(GameSession.PHASE_CLEAR);
        when(gameSessionService.get(GAME_ID)).thenReturn(session);
        RunReviewDTO review = new RunReviewDTO("칭호", "묘비", "분석", List.of(), null, null);
        when(runReviewService.review(any(), any(), anyBoolean(), any(), any())).thenReturn(review);
        when(chronicleService.record(any(), anyBoolean(), any())).thenThrow(new RuntimeException("DB down"));

        assertThat(engine.getRunReview(member)).isSameAs(review);
    }
}
