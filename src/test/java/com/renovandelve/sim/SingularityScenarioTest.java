package com.renovandelve.sim;

import com.renovandelve.ai.dto.response.AsyncStageResponseDTO;
import com.renovandelve.ai.dto.response.Encounter;
import com.renovandelve.ai.dto.response.RunReviewDTO;
import com.renovandelve.ai.service.AsyncPrefetchService;
import com.renovandelve.ai.service.GameDataEtlService;
import com.renovandelve.ai.service.MonsterRagService;
import com.renovandelve.ai.service.RunReviewService;
import com.renovandelve.ai.service.WraithVoiceService;
import com.renovandelve.catalog.service.MonsterService;
import com.renovandelve.game.domain.ChronicleChapterEntity;
import com.renovandelve.game.domain.WraithEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.dto.request.EncounterChoiceRequest;
import com.renovandelve.game.dto.request.NewGameRequest;
import com.renovandelve.game.dto.request.RewardSelectRequest;
import com.renovandelve.game.dto.request.SkillUseRequest;
import com.renovandelve.game.dto.response.GameViewDTO;
import com.renovandelve.game.dto.response.NodeDTO;
import com.renovandelve.game.dto.response.TurnResultDTO;
import com.renovandelve.game.engine.DemonContract;
import com.renovandelve.game.engine.GameSession;
import com.renovandelve.game.engine.KingdomState;
import com.renovandelve.game.engine.SessionMonster;
import com.renovandelve.game.repository.ChronicleChapterRepository;
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.game.repository.WraithRepository;
import com.renovandelve.game.service.GameEngineService;
import com.renovandelve.game.service.GameStateService;
import com.renovandelve.game.service.KingdomService;
import com.renovandelve.game.service.WraithService;
import com.renovandelve.game.service.redis.GameSessionService;
import com.renovandelve.global.exception.BusinessException;
import com.renovandelve.global.exception.ErrorCode;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 특이점 4종(전생의 망령, 악마의 계약서, 레노바 연대기, 왕국의 저울) 시나리오.
 *
 * 실제 게임 엔진·DB·Redis 세션을 쓰고 AI만 목으로 둔다. 직접 플레이로는 만나기 어려운 자리(망령이 기다리는 노드,
 * 계약 조항이 발동하는 순간)는 DB의 진행 위치와 세션의 몬스터 값을 바꿔 만든다. 그 밖의 판정은 모두 엔진이 한다.
 * 별도 DB와 Redis를 쓴다(BalanceSimulation과 같은 설정).
 * <pre>
 * RD_SIM=true RD_SIM_DB_URL=jdbc:postgresql://localhost:5433/renova_sim RD_SIM_DB_PASSWORD=... \
 *   RD_SIM_REDIS_PORT=6380 ./gradlew test --tests '*SingularityScenarioTest'
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "RD_SIM", matches = "true")
@SpringBootTest(properties = {
        "spring.datasource.url=${RD_SIM_DB_URL}",
        "spring.datasource.password=${RD_SIM_DB_PASSWORD}",
        "spring.data.redis.port=${RD_SIM_REDIS_PORT:6380}",
        "logging.level.root=WARN",
        "logging.level.org.hibernate.SQL=OFF",
        "spring.jpa.show-sql=false",
})
class SingularityScenarioTest {

    @MockitoBean private MonsterRagService monsterRagService;
    @MockitoBean private RunReviewService runReviewService;
    @MockitoBean private WraithVoiceService wraithVoiceService;
    @MockitoBean private AsyncPrefetchService asyncPrefetchService;
    @MockitoBean private ChatMemory chatMemory;
    @MockitoBean private GameDataEtlService gameDataEtlService;

    @Autowired private GameEngineService engine;
    @Autowired private GameStateService gameStateService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private GameStateRepository gameStateRepository;
    @Autowired private WraithRepository wraithRepository;
    @Autowired private WraithService wraithService;
    @Autowired private ChronicleChapterRepository chronicleChapterRepository;
    @Autowired private GameSessionService gameSessionService;
    @Autowired private MonsterService monsterService;
    @Autowired private KingdomService kingdomService;
    @Autowired private JdbcTemplate jdbc;

    /** 다음 노드를 무엇으로 만들지 (BATTLE / BOSS / ENCOUNTER) */
    private final AtomicReference<String> nextContent = new AtomicReference<>("BATTLE");

    @BeforeEach
    void setUp() {
        nextContent.set("BATTLE");
        when(monsterRagService.chat(anyInt(), anyInt(), anyString())).thenAnswer(call -> switch (nextContent.get()) {
            case "ENCOUNTER" -> AsyncStageResponseDTO.encounter("갈림길 끝의 장면", new Encounter("세 갈래의 빛", List.of()));
            case "BOSS" -> AsyncStageResponseDTO.battle("구역의 지배자", monsterService.getRandomMonsterBoss());
            default -> AsyncStageResponseDTO.battle("적이 길을 막는다", monsterService.getRandomMonsterNormal());
        });
        when(wraithVoiceService.appearance(any())).thenReturn("안개 속에서 낯익은 그림자가 걸어 나온다.");
        contractChance(0.0);
    }

    /* ==================== 전생의 망령 ==================== */

    @Test
    @DisplayName("망령: 쓰러진 자리에 남고, 다음 원정의 그 노드에서 강적으로 나타나며, 세 번 달아나면 흩어진다")
    void wraithAppearsAndFadesAfterThreeEscapes() {
        MemberEntity member = newMember();
        GameStateEntity first = start(member, "HARD", List.of());
        long deathNode = 5;
        placeAt(first, 1, deathNode - 1);
        engine.nextNode(member, "SAFE");
        TurnResultDTO death = dieInBattle(member);
        assertThat(death.result()).isEqualTo("LOSE");

        List<WraithEntity> active = wraithRepository.findByMemberIdAndStatus(member.getMemberId(), WraithEntity.Status.ACTIVE);
        assertThat(active).hasSize(1);
        WraithEntity wraith = active.get(0);
        assertThat(wraith.getStage()).isEqualTo((short) 1);
        assertThat(wraith.getNode()).isEqualTo((short) WraithEntity.appearNode((int) deathNode));
        assertThat(wraith.getSourceGameStateId()).isEqualTo(first.getGameStateId());
        assertThat(wraith.getKillerName()).isNotBlank();
        assertThat(wraithService.current(member.getMemberId())).get()
                .satisfies(dto -> assertThat(dto.escapesLeft()).isEqualTo(WraithEntity.MAX_ESCAPES));

        for (int escape = 1; escape <= WraithEntity.MAX_ESCAPES; escape++) {
            GameStateEntity run = start(member, "HARD", List.of());
            placeAt(run, 1, wraith.getNode() - 1);
            NodeDTO node = engine.nextNode(member, "SAFE");
            assertThat(node.nodeType()).as("%d번째 원정", escape).isEqualTo("WRAITH");
            assertThat(node.monsters()).singleElement()
                    .satisfies(m -> assertThat(m.name()).isEqualTo(WraithService.WRAITH_NAME));
            assertThat(node.story()).contains("낯익은 그림자");

            makeMonstersHarmless(run);
            TurnResultDTO fled = engine.flee(member);
            assertThat(fled.result()).isEqualTo("FLED");
            WraithEntity now = wraithRepository.findById(wraith.getWraithId()).orElseThrow();
            if (escape < WraithEntity.MAX_ESCAPES) {
                assertThat(now.isActive()).isTrue();
                assertThat(fled.log()).anyMatch(l -> l.contains("다음 원정에도"));
            } else {
                assertThat(now.getStatus()).isEqualTo(WraithEntity.Status.FADED);
                assertThat(fled.log()).anyMatch(l -> l.contains("흩어져"));
            }
            gameStateService.abandonGame(member);
        }
        assertThat(wraithService.current(member.getMemberId())).isEmpty();

        // 흩어진 뒤에는 같은 자리에서 다시 나타나지 않는다
        GameStateEntity after = start(member, "HARD", List.of());
        placeAt(after, 1, wraith.getNode() - 1);
        assertThat(engine.nextNode(member, "SAFE").nodeType()).isNotEqualTo("WRAITH");
    }

    @Test
    @DisplayName("망령: 처치하면 지난 원정의 유물이 보상 후보로 돌아오고 망령은 SLAIN이 된다")
    void slainWraithReturnsRelic() {
        MemberEntity member = newMember();
        Long relicId = cheapestRelicId(member);
        GameStateEntity first = start(member, "HARD", List.of(relicId));
        placeAt(first, 1, 3);
        engine.nextNode(member, "SAFE");
        dieInBattle(member);
        WraithEntity wraith = wraithRepository.findByMemberIdAndStatus(member.getMemberId(), WraithEntity.Status.ACTIVE).get(0);
        assertThat(wraith.relicIdList()).contains(relicId);

        GameStateEntity second = start(member, "HARD", List.of());
        placeAt(second, 1, wraith.getNode() - 1);
        assertThat(engine.nextNode(member, "SAFE").nodeType()).isEqualTo("WRAITH");
        TurnResultDTO win = killMonsters(member, second);

        assertThat(win.result()).isEqualTo("WIN");
        assertThat(win.log()).anyMatch(l -> l.contains("낯익은 유물"));
        assertThat(win.reward()).isNotNull();
        assertThat(win.reward().relics()).anyMatch(r -> r.relicId().equals(relicId));
        assertThat(wraithRepository.findById(wraith.getWraithId()).orElseThrow().getStatus()).isEqualTo(WraithEntity.Status.SLAIN);

        // 돌아온 유물을 실제로 고를 수 있다
        engine.selectReward(member, new RewardSelectRequest("RELIC", relicId, null));
        assertThat(jdbc.queryForObject("select count(*) from game_relic_inventory where game_state_id = ? and relic_id = ?",
                Integer.class, second.getGameStateId(), relicId)).isEqualTo(1);
    }

    @Test
    @DisplayName("망령 + 계약 '전리품': 돌아온 유물도 조항이 거둬 가고, 문구가 보상 후보라고 약속하지 않는다")
    void spoilsClaimTakesRecoveredRelic() {
        MemberEntity member = newMember();
        Long relicId = cheapestRelicId(member);
        GameStateEntity first = start(member, "HARD", List.of(relicId));
        placeAt(first, 1, 3);
        engine.nextNode(member, "SAFE");
        dieInBattle(member);
        WraithEntity wraith = wraithRepository.findByMemberIdAndStatus(member.getMemberId(), WraithEntity.Status.ACTIVE).get(0);

        GameStateEntity second = start(member, "HARD", List.of());
        setContract(second, "GOLD:SPOILS_CLAIM:1");
        placeAt(second, 1, wraith.getNode() - 1);
        engine.nextNode(member, "SAFE");
        TurnResultDTO win = killMonsters(member, second);

        assertThat(win.phase()).isEqualTo(GameSession.PHASE_SHOP);
        assertThat(win.reward()).isNull();
        assertThat(win.log()).anyMatch(l -> l.contains("낯익은 유물까지 거둬 간다"));
        assertThat(win.log()).noneMatch(l -> l.contains("(보상 후보)"));
        assertThat(wraithRepository.findById(wraith.getWraithId()).orElseThrow().getStatus()).isEqualTo(WraithEntity.Status.SLAIN);
    }

    @Test
    @DisplayName("망령: 망령에게 다시 쓰러지면 이전 망령은 흩어지고 새 망령 하나만 기다린다")
    void dyingToWraithReplacesIt() {
        MemberEntity member = newMember();
        GameStateEntity first = start(member, "HARD", List.of());
        placeAt(first, 1, 5);
        engine.nextNode(member, "SAFE");
        dieInBattle(member);
        WraithEntity old = wraithRepository.findByMemberIdAndStatus(member.getMemberId(), WraithEntity.Status.ACTIVE).get(0);

        GameStateEntity second = start(member, "HARD", List.of());
        placeAt(second, 1, old.getNode() - 1);
        assertThat(engine.nextNode(member, "SAFE").nodeType()).isEqualTo("WRAITH");
        dieInBattle(member);

        assertThat(wraithRepository.findById(old.getWraithId()).orElseThrow().getStatus()).isEqualTo(WraithEntity.Status.FADED);
        List<WraithEntity> active = wraithRepository.findByMemberIdAndStatus(member.getMemberId(), WraithEntity.Status.ACTIVE);
        assertThat(active).singleElement().satisfies(w -> {
            assertThat(w.getSourceGameStateId()).isEqualTo(second.getGameStateId());
            assertThat(w.getKillerName()).isEqualTo(WraithService.WRAITH_NAME);
        });
    }

    @Test
    @DisplayName("망령: 원정을 포기하면 망령을 남기지 않고, 보스 노드에는 망령이 나타나지 않는다")
    void abandonLeavesNoWraithAndBossNodeIsKept() {
        MemberEntity member = newMember();
        start(member, "HARD", List.of());
        gameStateService.abandonGame(member);
        assertThat(wraithRepository.findByMemberIdAndStatus(member.getMemberId(), WraithEntity.Status.ACTIVE)).isEmpty();

        // 노드 9에서 쓰러져도 망령은 9에 선다 (10 = 보스 자리는 피한다)
        GameStateEntity run = start(member, "HARD", List.of());
        placeAt(run, 1, 8);
        engine.nextNode(member, "SAFE");
        dieInBattle(member);
        WraithEntity wraith = wraithRepository.findByMemberIdAndStatus(member.getMemberId(), WraithEntity.Status.ACTIVE).get(0);
        assertThat(wraith.getNode()).isEqualTo((short) 9);
    }

    /* ==================== 악마의 계약서 ==================== */

    @Test
    @DisplayName("계약서: 조우에서 제안되고, 서명하면 혜택을 받고 계약이 걸린다. 걸린 동안에는 다시 제안되지 않는다")
    void contractOfferAndSign() {
        MemberEntity member = newMember();
        GameStateEntity run = start(member, "NORMAL", List.of());
        contractChance(1.0);
        nextContent.set("ENCOUNTER");
        NodeDTO offer = engine.nextNode(member, "SAFE");
        assertThat(offer.nodeType()).isEqualTo("CONTRACT");
        assertThat(offer.encounter().choices()).hasSize(2);
        assertThat(offer.encounter().choices().get(0)).contains("작은 글씨");

        int goldBefore = gameStateRepository.findById(run.getGameStateId()).orElseThrow().getGold();
        engine.chooseEncounter(member, new EncounterChoiceRequest(0));
        GameStateEntity signed = gameStateRepository.findById(run.getGameStateId()).orElseThrow();
        DemonContract contract = signed.demonContract();
        assertThat(contract).isNotNull();
        assertThat(contract.remaining()).isEqualTo(contract.clause().count());
        if (contract.boon() == DemonContract.Boon.GOLD) {
            assertThat(signed.getGold()).isEqualTo(goldBefore + DemonContract.Boon.goldAmount(1));
        }
        GameViewDTO view = engine.getView(member);
        assertThat(view.status().getContract()).isNotNull();
        assertThat(view.status().getContract().hint()).isEqualTo(contract.clause().hint());

        assertThat(engine.nextNode(member, "SAFE").nodeType()).isEqualTo("ENCOUNTER");
    }

    @Test
    @DisplayName("계약서: 거절하면 아무것도 걸리지 않는다")
    void contractRefuse() {
        MemberEntity member = newMember();
        GameStateEntity run = start(member, "NORMAL", List.of());
        contractChance(1.0);
        nextContent.set("ENCOUNTER");
        engine.nextNode(member, "SAFE");
        engine.chooseEncounter(member, new EncounterChoiceRequest(1));
        assertThat(gameStateRepository.findById(run.getGameStateId()).orElseThrow().demonContract()).isNull();
        assertThat(engine.getView(member).phase()).isEqualTo(GameSession.PHASE_EXPLORE);
    }

    @Test
    @DisplayName("계약서 조항 '빚': 세 걸음째에 골드의 60%를 가져가고 계약이 끝난다")
    void goldDebtClause() {
        MemberEntity member = newMember();
        GameStateEntity run = start(member, "NORMAL", List.of());
        setContract(run, "GOLD:GOLD_DEBT:3");
        jdbc.update("update game_state set gold = 1000 where game_state_id = ?", run.getGameStateId());
        nextContent.set("ENCOUNTER");

        for (int step = 1; step <= 3; step++) {
            NodeDTO node = engine.nextNode(member, "SAFE");
            GameStateEntity now = gameStateRepository.findById(run.getGameStateId()).orElseThrow();
            if (step < 3) {
                assertThat(now.demonContract().remaining()).isEqualTo(3 - step);
                assertThat(now.getGold()).isEqualTo(1000);
                assertThat(node.contractNote()).isNull();
            } else {
                assertThat(now.demonContract()).isNull();
                assertThat(now.getGold()).isEqualTo(400);
                assertThat(node.contractNote()).contains("빚");
            }
            engine.chooseEncounter(member, new EncounterChoiceRequest(0));
        }
    }

    @Test
    @DisplayName("계약서 조항 '그림자': 두 번의 전투 동안 적 공격력 +25%, 그다음 계약이 끝난다")
    void shadowPactClause() {
        MemberEntity member = newMember();
        GameStateEntity run = start(member, "NORMAL", List.of());
        setContract(run, "POWER:SHADOW_PACT:2");

        NodeDTO first = engine.nextNode(member, "SAFE");
        assertThat(first.contractNote()).contains("그림자").contains("한 번 더");
        assertThat(contractOf(run).remaining()).isEqualTo(1);
        killMonsters(member, run);
        pickNothingAndExplore(member);

        NodeDTO second = engine.nextNode(member, "SAFE");
        assertThat(second.contractNote()).contains("계약 종료");
        assertThat(contractOf(run)).isNull();
        killMonsters(member, run);
        pickNothingAndExplore(member);

        assertThat(engine.nextNode(member, "SAFE").contractNote()).isNull();
    }

    @Test
    @DisplayName("계약서 조항 '전리품': 다음 승리에서 전리품을 고르지 못하고 바로 상점으로 간다")
    void spoilsClaimClause() {
        MemberEntity member = newMember();
        GameStateEntity run = start(member, "NORMAL", List.of());
        setContract(run, "GOLD:SPOILS_CLAIM:1");
        engine.nextNode(member, "SAFE");
        TurnResultDTO win = killMonsters(member, run);
        assertThat(win.result()).isEqualTo("WIN");
        assertThat(win.reward()).isNull();
        assertThat(win.phase()).isEqualTo(GameSession.PHASE_SHOP);
        assertThat(win.log()).anyMatch(l -> l.contains("전리품은 그의 몫"));
        assertThat(contractOf(run)).isNull();
        engine.leaveShop(member);
        assertThat(engine.getView(member).phase()).isEqualTo(GameSession.PHASE_EXPLORE);
    }

    @Test
    @DisplayName("계약서 조항 '피의 십일조': 일반 전투에서는 남아 있다가 보스전이 시작될 때 HP 30%를 가져간다")
    void bloodTitheClause() {
        MemberEntity member = newMember();
        GameStateEntity run = start(member, "NORMAL", List.of());
        setContract(run, "VIGOR:BLOOD_TITHE:1");
        engine.nextNode(member, "SAFE");
        assertThat(contractOf(run)).isNotNull();
        killMonsters(member, run);
        pickNothingAndExplore(member);

        placeAt(run, 1, 9);
        jdbc.update("update game_character set current_hp = 100, max_hp = 100 where game_state_id = ?", run.getGameStateId());
        nextContent.set("BOSS");
        NodeDTO boss = engine.nextNode(member, "SAFE");
        assertThat(boss.nodeType()).isEqualTo("BOSS");
        assertThat(boss.contractNote()).contains("피로 값을 치른다");
        assertThat(contractOf(run)).isNull();
        assertThat(engine.getView(member).status().getCharacterDTO().getCurrentHp()).isEqualTo(70);
    }

    @Test
    @DisplayName("계약서 파기: 천상 성향이 모자라면 거부, 채우면 스테이지 비용을 내고 계약이 사라진다. 탐험 중에만 된다")
    void breakContract() {
        MemberEntity member = newMember();
        GameStateEntity run = start(member, "NORMAL", List.of());
        setContract(run, "GOLD:GOLD_DEBT:3");
        jdbc.update("update game_state set gold = 500, heaven_affinity = 0 where game_state_id = ?", run.getGameStateId());
        assertThat(engine.getView(member).status().getContract().breakable()).isFalse();
        assertThatThrownBy(() -> engine.breakContract(member)).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTRACT_BREAK_LOCKED);

        jdbc.update("update game_state set heaven_affinity = ? where game_state_id = ?",
                DemonContract.BREAK_HEAVEN_AFFINITY, run.getGameStateId());
        assertThat(engine.getView(member).status().getContract().breakable()).isTrue();
        engine.breakContract(member);
        GameStateEntity broken = gameStateRepository.findById(run.getGameStateId()).orElseThrow();
        assertThat(broken.demonContract()).isNull();
        assertThat(broken.getGold()).isEqualTo(500 - DemonContract.breakCost(1));

        setContract(run, "GOLD:GOLD_DEBT:3");
        engine.nextNode(member, "SAFE"); // 전투 중
        assertThatThrownBy(() -> engine.breakContract(member)).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PHASE);
    }

    /* ==================== 레노바 연대기 ==================== */

    @Test
    @DisplayName("연대기: 결과 화면의 회고가 장을 쓰고, 다음 회고에는 앞선 장이 넘어간다. 같은 원정의 장은 한 번만")
    void chronicleChaptersContinue() {
        MemberEntity member = newMember();
        when(runReviewService.review(any(), any(), anyBoolean(), anyList(), anyList())).thenAnswer(call ->
                new RunReviewDTO("칭호", "묘비명", "분석", List.of("조언"), "첫 장 제목", "그는 무너진 정문에서 쓰러졌다."));

        GameStateEntity first = start(member, "HARD", List.of());
        engine.nextNode(member, "SAFE");
        dieInBattle(member);
        assertThat(engine.getRunReview(member)).isNotNull();
        engine.getRunReview(member); // 다시 열어도 장이 늘지 않는다
        List<ChronicleChapterEntity> chapters = chronicleChapterRepository.findByMemberIdOrderByChapterNoAsc(member.getMemberId());
        assertThat(chapters).singleElement().satisfies(c -> {
            assertThat(c.getChapterNo()).isEqualTo(1);
            assertThat(c.getTitle()).isEqualTo("첫 장 제목");
            assertThat(c.getGameStateId()).isEqualTo(first.getGameStateId());
            assertThat(c.isCleared()).isFalse();
        });

        GameStateEntity second = start(member, "HARD", List.of());
        engine.nextNode(member, "SAFE");
        dieInBattle(member);
        engine.getRunReview(member);
        verify(runReviewService).review(any(), any(), anyBoolean(),
                org.mockito.ArgumentMatchers.argThat(previous -> previous != null && previous.stream()
                        .anyMatch(p -> p.contains("무너진 정문"))), anyList());
        assertThat(chronicleChapterRepository.findByMemberIdOrderByChapterNoAsc(member.getMemberId()))
                .extracting(ChronicleChapterEntity::getChapterNo).containsExactly(1, 2);
        assertThat(chronicleChapterRepository.existsByGameStateId(second.getGameStateId())).isTrue();
    }

    @Test
    @DisplayName("연대기: 결과 화면을 거치지 않고 새 원정을 시작하면 서버가 사실만으로 빈 장을 채운다. 포기한 원정은 장이 없다")
    void chronicleFillsMissingChapter() {
        MemberEntity member = newMember();
        GameStateEntity lost = start(member, "HARD", List.of());
        engine.nextNode(member, "SAFE");
        dieInBattle(member);

        start(member, "HARD", List.of()); // 회고를 열지 않고 바로 다음 원정
        List<ChronicleChapterEntity> chapters = chronicleChapterRepository.findByMemberIdOrderByChapterNoAsc(member.getMemberId());
        assertThat(chapters).singleElement().satisfies(c -> {
            assertThat(c.getGameStateId()).isEqualTo(lost.getGameStateId());
            assertThat(c.getTitle()).isNotBlank();
            assertThat(c.getBody()).isNotBlank();
        });

        gameStateService.abandonGame(member);
        start(member, "HARD", List.of());
        assertThat(chronicleChapterRepository.countByMemberId(member.getMemberId())).isEqualTo(1);
    }

    /* ==================== 왕국의 저울 ==================== */

    @Test
    @DisplayName("왕국: 한 사람이 한 표로 정세를 정하고, 원정을 시작할 때 그 정세가 원정에 고정된다")
    void kingdomVotesAndSnapshot() {
        // 시뮬레이션 기록이 섞이지 않도록 먼 미래의 7일 창을 쓴다. 집계는 "기준 시각 이후"만 보므로(상한 없음)
        // 이 테스트가 미래로 옮긴 원정은 끝나면 되돌린다
        clearFutureVotes();
        Instant future = LocalDateTime.of(2031, 1, 8, 12, 0).atZone(ZoneId.systemDefault()).toInstant();
        ReflectionTestUtils.setField(kingdomService, "clock", Clock.fixed(future, ZoneId.systemDefault()));
        ReflectionTestUtils.setField(kingdomService, "cachedAt", Instant.EPOCH);
        try {
            assertThat(kingdomService.current().state()).isEqualTo(KingdomState.BALANCE.name()); // 표가 없으면 균형

            // 마계에 몰표를 던지는 한 사람(원정 5판)과 천상 한 판씩인 두 사람: 사람 수로는 천상 2 : 마계 1
            MemberEntity abyssFan = newMember();
            for (int i = 0; i < 5; i++) {
                GameStateEntity g = start(abyssFan, "NORMAL", List.of());
                vote(g, 0, 9, 0, future);
                gameStateService.abandonGame(abyssFan);
            }
            for (int i = 0; i < 2; i++) {
                MemberEntity saint = newMember();
                GameStateEntity g = start(saint, "NORMAL", List.of());
                vote(g, 3, 0, 0, future);
                gameStateService.abandonGame(saint);
            }
            ReflectionTestUtils.setField(kingdomService, "cachedAt", Instant.EPOCH);
            var kingdom = kingdomService.current();
            assertThat(kingdom.players()).isEqualTo(3);
            assertThat(kingdom.state()).isEqualTo(KingdomState.decide(2 / 3.0, 1 / 3.0, 0).name());

            MemberEntity newcomer = newMember();
            GameStateEntity run = start(newcomer, "NORMAL", List.of());
            assertThat(gameStateRepository.findById(run.getGameStateId()).orElseThrow().getKingdom()).isEqualTo(kingdom.state());
        } finally {
            clearFutureVotes();
            ReflectionTestUtils.setField(kingdomService, "clock", Clock.systemDefaultZone());
            ReflectionTestUtils.setField(kingdomService, "cachedAt", Instant.EPOCH);
        }
    }

    /* ==================== 도우미 ==================== */

    private MemberEntity newMember() {
        String id = "sg" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return memberRepository.save(MemberEntity.register(id, "x", id.substring(0, 10), "테스터", id + "@example.com", "test"));
    }

    private GameStateEntity start(MemberEntity member, String difficulty, List<Long> relicIds) {
        return gameStateService.createNewGame(new NewGameRequest(difficulty, null, null, null, List.of(), List.of(), relicIds), member);
    }

    /** 다음 nextNode가 이 노드의 다음 칸으로 가도록 진행 위치를 옮긴다 */
    private void placeAt(GameStateEntity game, int stage, long node) {
        jdbc.update("update game_state set current_stage = ?, current_node_id = ? where game_state_id = ?",
                stage, node, game.getGameStateId());
    }

    private void setContract(GameStateEntity game, String contract) {
        jdbc.update("update game_state set contract = ? where game_state_id = ?", contract, game.getGameStateId());
    }

    private DemonContract contractOf(GameStateEntity game) {
        return gameStateRepository.findById(game.getGameStateId()).orElseThrow().demonContract();
    }

    private void vote(GameStateEntity game, int heaven, int abyss, int human, Instant at) {
        jdbc.update("update game_state set heaven_affinity = ?, abyss_affinity = ?, human_affinity = ?, created_at = ? "
                + "where game_state_id = ?", heaven, abyss, human,
                LocalDateTime.ofInstant(at.minusSeconds(3600), ZoneId.systemDefault()), game.getGameStateId());
    }

    private void clearFutureVotes() {
        jdbc.update("update game_state set created_at = now() where created_at > now()");
    }

    private void contractChance(double chance) {
        Object target = AopTestUtils.getTargetObject(engine);
        ReflectionTestUtils.setField(target, "contractOfferChance", chance);
    }

    private Long cheapestRelicId(MemberEntity member) {
        return gameStateService.getSetupOptions(member).relics().stream()
                .min(java.util.Comparator.comparingInt(r -> r.scoreCost())).orElseThrow().relicId();
    }

    private GameSession session(GameStateEntity game) {
        return gameSessionService.get(game.getGameStateId());
    }

    /** 적의 공격이 확실히 닿게 하고 캐릭터 HP를 1로 둔 뒤 방어 -> 쓰러진다 */
    private TurnResultDTO dieInBattle(MemberEntity member) {
        GameStateEntity game = gameStateRepository.findByMember_MemberIdAndIsActiveTrue(member.getMemberId()).orElseThrow();
        GameSession s = session(game);
        for (SessionMonster m : s.getMonsters()) {
            m.setAtk(100_000);
            m.setIntent("ATTACK");
            m.getBuffs().clear();
        }
        gameSessionService.save(s);
        jdbc.update("update game_character set current_hp = 1 where game_state_id = ?", game.getGameStateId());
        return engine.guard(member);
    }

    /** 적이 이번 턴에 해를 끼치지 못하게 한다 (도주 시험용) */
    private void makeMonstersHarmless(GameStateEntity game) {
        GameSession s = session(game);
        for (SessionMonster m : s.getMonsters()) {
            m.setAtk(1);
        }
        gameSessionService.save(s);
        jdbc.update("update game_character set current_hp = max_hp where game_state_id = ?", game.getGameStateId());
    }

    /** 적 HP를 1로 두고 기본 공격으로 모두 쓰러뜨린다 */
    private TurnResultDTO killMonsters(MemberEntity member, GameStateEntity game) {
        TurnResultDTO last = null;
        for (int guard = 0; guard < 10; guard++) {
            GameSession s = session(game);
            if (!GameSession.PHASE_BATTLE.equals(s.getPhase())) break;
            for (SessionMonster m : s.getMonsters()) {
                if (m.getCurrentHp() > 0) m.setCurrentHp(1);
                m.setAtk(1);
                m.setDef(0);
                m.setIntent("ATTACK");
            }
            gameSessionService.save(s);
            jdbc.update("update game_character set current_hp = max_hp where game_state_id = ?", game.getGameStateId());
            Long slotId = engine.getView(member).status().getSkillSlotDTO().get(0).getSlotId();
            Integer target = s.getMonsters().stream().filter(m -> m.getCurrentHp() > 0).map(SessionMonster::getKey).findFirst().orElseThrow();
            last = engine.useSkill(member, new SkillUseRequest(slotId, List.of(target)));
            if (!"CONTINUE".equals(last.result())) break;
        }
        return last;
    }

    /** 전리품 단계면 경험치·골드만 받고(고르지 않음) 탐험으로 돌아간다 */
    private void pickNothingAndExplore(MemberEntity member) {
        for (int i = 0; i < 5; i++) {
            String phase = engine.getView(member).phase();
            switch (phase) {
                case GameSession.PHASE_REWARD -> engine.selectReward(member, new RewardSelectRequest("SKIP", null, null));
                case GameSession.PHASE_SHOP -> engine.leaveShop(member);
                case GameSession.PHASE_REST -> engine.chooseRest(member, "HEAL");
                default -> { return; }
            }
        }
    }
}
