package com.renovandelve.game.service;

import com.renovandelve.global.exception.BusinessException;
import com.renovandelve.global.exception.ErrorCode;
import java.util.Optional;
import com.renovandelve.ai.service.AiCallRunner;
import com.renovandelve.catalog.mapper.CatalogMapper;
import com.renovandelve.catalog.service.MonsterService;

import com.renovandelve.game.domain.GameCharacterEntity;
import com.renovandelve.game.domain.GameConsumableInventoryEntity;
import com.renovandelve.game.domain.GameEquipmentEntity;
import com.renovandelve.game.domain.GameSkillSlotEntity;
import com.renovandelve.game.domain.WraithEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.catalog.domain.MonsterBaseStatusEntity;
import com.renovandelve.catalog.domain.consumableentity.ConsumableEntity;
import com.renovandelve.game.domain.gamestateentity.Difficulty;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.catalog.domain.skillentity.Target;
import com.renovandelve.catalog.domain.weaponentity.Type;
import com.renovandelve.catalog.dto.ConsumableDTO;
import com.renovandelve.catalog.dto.MonsterDTO;
import com.renovandelve.catalog.dto.RelicDTO;
import com.renovandelve.game.dto.SkillInfoDTO;
import com.renovandelve.game.dto.request.ConsumableUseRequest;
import com.renovandelve.game.dto.request.EncounterChoiceRequest;
import com.renovandelve.game.dto.request.PurchaseRequest;
import com.renovandelve.game.dto.request.RewardSelectRequest;
import com.renovandelve.game.dto.request.SkillDiscardRequest;
import com.renovandelve.game.dto.request.SkillUseRequest;
import com.renovandelve.ai.dto.response.AsyncStageResponseDTO;
import com.renovandelve.game.dto.response.BattleMonsterDTO;
import com.renovandelve.ai.dto.response.Encounter;
import com.renovandelve.game.dto.response.EncounterDTO;
import com.renovandelve.game.dto.response.EncounterOutcomeDTO;
import com.renovandelve.game.dto.response.GameSummaryDTO;
import com.renovandelve.game.dto.response.GameViewDTO;
import com.renovandelve.game.dto.response.NodeDTO;
import com.renovandelve.game.dto.response.RewardViewDTO;
import com.renovandelve.ai.dto.response.RunReviewDTO;
import com.renovandelve.game.dto.response.ShopItemDTO;
import com.renovandelve.game.dto.response.StatusDTO;
import com.renovandelve.game.dto.response.TurnResultDTO;
import com.renovandelve.game.mapper.GameMapper;
import com.renovandelve.game.engine.Alignment;
import com.renovandelve.game.engine.DemonContract;
import com.renovandelve.game.engine.KingdomState;
import com.renovandelve.game.engine.EncounterChoice;
import com.renovandelve.game.engine.Faction;
import com.renovandelve.game.engine.GameSession;
import com.renovandelve.game.engine.Pact;
import com.renovandelve.game.engine.PathChoice;
import com.renovandelve.game.engine.RarityTable;
import com.renovandelve.game.engine.RelicEffects;
import com.renovandelve.game.engine.RewardOptions;
import com.renovandelve.game.engine.RunStats;
import com.renovandelve.game.engine.SessionMonster;
import com.renovandelve.game.engine.ShopStockItem;
import com.renovandelve.game.engine.SkillSynergies;
import com.renovandelve.game.engine.StatusEffect;
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
import com.renovandelve.ai.service.RunReviewService;
import com.renovandelve.game.service.redis.GameSessionService;
import com.renovandelve.ai.service.StageCacheService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameEngineServiceImpl implements GameEngineService {

    private final GameStateRepository gameStateRepository;
    private final MonsterBaseStatusRepository monsterBaseStatusRepository;
    private final SkillRepository skillRepository;
    private final RelicRepository relicRepository;
    private final ConsumableRepository consumableRepository;
    private final GameRelicInventoryRepository gameRelicInventoryRepository;
    private final GameConsumableInventoryRepository gameConsumableInventoryRepository;
    private final MonsterService monsterService;
    private final MonsterRagService monsterRagService;
    private final BattleService battleService;
    private final BattleResultService battleResultService;
    private final ShopService shopService;
    private final GameSessionService gameSessionService;
    private final StatusAssembler statusAssembler;
    private final CatalogMapper catalogMapper;
    private final GameMapper gameMapper;
    private final RunReviewService runReviewService;
    private final ChatMemory chatMemory;
    private final StageCacheService stageCacheService;
    private final SkillSlotService skillSlotService;
    private final WraithService wraithService;
    private final WraithVoiceService wraithVoiceService;
    private final AsyncPrefetchService asyncPrefetchService;
    private final ChronicleService chronicleService;
    private final AiCallRunner aiCallRunner;

    private final Random random = new Random();

    /** 망령을 처치한 자리에서 지난 원정의 유물이 보상 후보로 돌아왔다는 안내 */
    private static final String RECOVERED_RELIC_LINE = "흩어진 자리에 낯익은 유물이 떨어져 있다. (보상 후보)";

    /** 마지막 스테이지. 이 스테이지의 보스를 쓰러뜨리면 클리어 (가이드 Tool도 이 값을 쓴다) */
    public static final int FINAL_STAGE = 5;
    /** 스테이지당 노드 수. 마지막 노드가 보스 */
    public static final int NODES_PER_STAGE = 10;
    private static final double BASE_CRIT_CHANCE = 0.08;
    private static final double DAGGER_CRIT_BONUS = 0.15;
    private static final double PLAYER_GUARD_MULTIPLIER = 0.4; // 방어 태세: 받는 피해 60% 감소
    private static final int PASS_COOLDOWN_TICKS = 2;          // 숨 고르기: 쿨타임이 줄어드는 턴 수
    private static final double BOSS_REST_HEAL_RATIO = 0.35;   // 모닥불 휴식 회복량 (최대 HP 비율)
    private static final int REST_TRAIN_ATTACK = 3;            // 모닥불 단련: 공격력 영구 상승
    private static final int REST_TRAIN_DEFENSE = 1;           // 모닥불 단련: 방어력 영구 상승
    private static final int SHOP_REROLL_BASE_COST = 40;
    private static final int SHOP_REROLL_STEP = 30;

    /**
     * AI 노드 생성에 쓰는 시간 상한. 한 노드에 LLM 호출이 여러 번 이어지고 재시도까지 붙을 수 있어
     * 호출별 타임아웃만으로는 전체 시간이 묶이지 않는다. 이 시간을 넘기면 기다리지 않고 폴백한다.
     * 프론트 요청 타임아웃(90초)과 게임 락 TTL(120초)보다 짧아야 한다.
     */
    static final long AI_NODE_BUDGET_SECONDS = 75;
    /** 테스트에서 줄일 수 있도록 필드로 둔다 */
    long aiNodeBudgetSeconds = AI_NODE_BUDGET_SECONDS;
    /** 망령 장면은 한 번의 짧은 호출이라 상한을 따로 짧게 둔다. 넘기면 정해진 문장을 쓴다 */
    static final long WRAITH_VOICE_BUDGET_SECONDS = 20;
    /** 조우에서 계약서를 내밀 확률 (설정 renovaNDelve.game.contract-offer-chance, 기본 0.25). 테스트에서 0 / 1로 고정한다 */
    @Value("${renovaNDelve.game.contract-offer-chance:" + DemonContract.OFFER_CHANCE + "}")
    double contractOfferChance = DemonContract.OFFER_CHANCE;
    /** 망령 기록에 옮겨 둘 선택 줄 수 */
    private static final int WRAITH_MEMORY_LINES = 3;
    private static final String CHOICE_PREFIX = "[플레이어의 선택]";
    /**
     * 스킬을 버릴 수 있는 단계. 전투(쿨타임 회피)와 조우(선택 미루기)는 빠진다.
     * 허용하는 쪽을 적어 두어, 새 단계가 생기면 기본이 "거부"가 되게 한다.
     */
    static final Set<String> SKILL_DISCARD_PHASES = Set.of(
            GameSession.PHASE_EXPLORE, GameSession.PHASE_REWARD, GameSession.PHASE_SHOP, GameSession.PHASE_REST);
    /** 연대기 장에 넘길 선택 줄 수 */
    private static final int CHRONICLE_CHOICE_LINES = 6;

    /* ==================== 조회 ==================== */

    /**
     * 진행 중인 원정의 현재 화면 상태를 서버 기준으로 만든다. 단계(phase)에 맞는 노드·보상·상점·요약을 함께 싣는다.
     * 새로고침이나 화면-서버 단계 불일치 때 화면을 복원하는 데 쓴다.
     *
     * @param member 로그인 회원
     * @return 화면 상태 (원정이 없으면 phase = NONE)
     */
    @Override
    @Transactional
    public GameViewDTO getView(MemberEntity member) {
        Optional<GameStateEntity> active = gameStateRepository.findByMember_MemberIdAndIsActiveTrue(member.getMemberId());
        if (active.isEmpty()) {
            return new GameViewDTO("NONE", null, null, null, null, null, null, null);
        }
        GameStateEntity gameState = active.get();
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());

        NodeDTO node = null;
        RewardViewDTO reward = null;
        List<ShopItemDTO> shop = null;
        GameSummaryDTO summary = null;

        switch (session.getPhase()) {
            case GameSession.PHASE_BATTLE -> node = battleNode(session);
            case GameSession.PHASE_ENCOUNTER -> node = encounterNode(session, session.getStory());
            case GameSession.PHASE_REWARD -> reward = toRewardView(session.getReward());
            case GameSession.PHASE_SHOP -> shop = toShopItems(session.getShop(), gameState);
            case GameSession.PHASE_GAMEOVER, GameSession.PHASE_CLEAR -> summary = buildSummary(gameState, session);
            default -> { /* EXPLORE */ }
        }

        Integer rerollCost = GameSession.PHASE_SHOP.equals(session.getPhase()) ? rerollCost(gameState, session) : null;
        return new GameViewDTO(session.getPhase(), statusAssembler.toStatusDTO(gameState), node, reward, shop, summary,
                rerollCost, session.getStory());
    }

    /* ==================== 노드 진행 ==================== */

    /**
     * 갈림길을 골라 다음 노드로 나아간다.
     * AI가 서사와 전투/조우를 만들고(실패·시간 초과 시 폴백), 몬스터 능력치는 서버가 스테이지·난이도로 정한다.
     * 보스 노드는 AI 응답과 관계없이 보스전으로 만들고, 이미 전투·조우 중이면 새로 만들지 않고 현재 노드를 돌려준다(멱등).
     *
     * @param member    로그인 회원
     * @param pathValue 고른 길 (SAFE / DANGER / AMBUSH, 알 수 없으면 SAFE)
     * @return 새 노드
     */
    @Override
    @Transactional
    public NodeDTO nextNode(MemberEntity member, String pathValue) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());

        // 이미 노드가 진행 중이면(서버-클라이언트 단계 불일치 등) 현재 노드를 그대로 반환 -> 멱등 재진입
        if (GameSession.PHASE_BATTLE.equals(session.getPhase())) {
            return battleNode(session);
        }
        if (GameSession.PHASE_ENCOUNTER.equals(session.getPhase())) {
            return encounterNode(session, session.getStory());
        }
        requirePhase(session, GameSession.PHASE_EXPLORE, "지금은 이동할 수 없습니다.");
        PathChoice path = PathChoice.parse(pathValue);

        gameState.moveToNextNode();
        session.setContractNote(tickDebt(gameState));
        gameStateRepository.save(gameState);

        int stage = gameState.getCurrentStage();
        int node = gameState.getCurrentNodeId() == null ? 1 : gameState.getCurrentNodeId().intValue();
        String conversationId = conversationId(gameState);

        // 지난 원정에서 이 자리에 쓰러졌다면, 그 망령이 AI가 고른 적 대신 길을 막는다 (보스 노드 제외)
        if (node % NODES_PER_STAGE != 0) {
            Optional<WraithEntity> wraith = wraithService.waitingAt(member.getMemberId(), gameState.getGameStateId(), stage, node);
            if (wraith.isPresent()) {
                return startWraithBattle(gameState, session, wraith.get(), path, conversationId);
            }
        }

        // 원정의 첫 걸음에서 왕국의 정세를 대화 기록에 남겨, 이후 장면이 그 분위기를 이어받게 한다
        KingdomState kingdom = gameState.kingdomState();
        if (stage == 1 && node == 1 && kingdom != null) {
            remember(gameState, kingdom.chronicleLine());
        }

        AsyncStageResponseDTO ai = generateNodeContent(stage, node, conversationId);

        // 보스 노드(10노드)는 무조건 전투.
        // AI가 인카운터를 돌려주거나 몬스터 목록이 비어 있어도 랜덤 보스로 강제 전환한다.
        boolean bossNode = node % NODES_PER_STAGE == 0;
        if (bossNode && (!"BATTLE".equals(ai.type()) || ai.monsters() == null || ai.monsters().isEmpty())) {
            log.warn("보스 노드에 전투가 아닌 응답 감지 -> 보스전 강제 전환 stage={} node={} type={}", stage, node, ai.type());
            ai = AsyncStageResponseDTO.battle(
                    ai.story() != null ? ai.story() : fallbackStory(stage, true),
                    monsterService.getRandomMonsterBoss());
        }

        if ("BATTLE".equals(ai.type()) && ai.monsters() != null && !ai.monsters().isEmpty()) {
            List<SessionMonster> monsters = buildSessionMonsters(ai.monsters(), gameState);
            applyPath(monsters, path);           // 갈림길 선택에 따른 적 강도 / 기습
            applyPacts(monsters, pacts(gameState)); // 서약으로 스스로 건 제약
            applyKingdom(monsters, gameState.kingdomState()); // 왕국의 정세
            String nodeType = resolveNodeType(node, monsters);
            session.setContractNote(joinNotes(session.getContractNote(), applyContractToBattle(gameState, monsters, nodeType)));
            battleService.rollIntents(monsters); // 첫 턴 행동 예고

            session.setPhase(GameSession.PHASE_BATTLE);
            session.setPath(path.name());
            session.setPathNote(path.battleNote());
            session.setNodeType(nodeType);
            session.setStory(ai.story());
            session.setWraithId(null);
            session.setMonsters(monsters);
            session.setChoices(new ArrayList<>());
            gameSessionService.save(session);

            return battleNode(session);
        }

        // 인카운터
        Encounter encounter = ai.encounter() != null ? ai.encounter()
                : new Encounter("빛과 그림자가 동시에 당신을 부른다.", List.of());
        List<EncounterChoice> choices = buildEncounterChoices(encounter, gameState);
        String encounterType = "ENCOUNTER";
        // 걸린 계약이 없으면 가끔 마계가 계약서를 내민다 (조항은 숨기고 작은 글씨만 보여 준다)
        if (gameState.demonContract() == null && random.nextDouble() < contractOfferChance) {
            choices = buildContractChoices(gameState);
            encounterType = "CONTRACT";
        }

        session.setPhase(GameSession.PHASE_ENCOUNTER);
        session.setPath(PathChoice.SAFE.name()); // 조우 이벤트에는 길 선택이 영향을 주지 않는다
        // 고른 길이 무시된 것을 숨기지 않는다 (효과가 적용되지 않은 것처럼 보인다는 플레이 피드백)
        session.setPathNote(path == PathChoice.SAFE ? null
                : "%s을 골랐지만 싸움이 벌어지지 않았다. 조우에서는 길의 효과가 적용되지 않는다.".formatted(path.title()));
        session.setNodeType(encounterType);
        session.setStory(ai.story() != null ? ai.story() : encounter.text());
        session.setMonsters(new ArrayList<>());
        session.setChoices(choices);
        gameSessionService.save(session);

        return encounterNode(session, "CONTRACT".equals(encounterType)
                ? "어둠 속에서 붉은 인장이 찍힌 계약서가 펼쳐진다. 서명란 아래, 작은 글씨가 희미하게 빛난다."
                : encounter.text());
    }

    /* ==================== 전투 ==================== */

    /**
     * 스킬로 한 턴을 진행한다: 공격 → 상태이상 → 쿨타임 → 지속 피해 → 승리 판정 또는 적의 반격.
     *
     * @param member  로그인 회원
     * @param request 슬롯 ID와 대상
     * @return 턴 결과
     * @throws BusinessException 전투 중이 아니거나 쿨타임이 남았으면
     */
    @Override
    @Transactional
    public TurnResultDTO useSkill(MemberEntity member, SkillUseRequest request) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        requirePhase(session, GameSession.PHASE_BATTLE, "전투 중이 아닙니다.");

        GameCharacterEntity character = character(gameState);
        List<GameSkillSlotEntity> slots = gameState.getGameSkillSlot();

        GameSkillSlotEntity slot = slots.stream()
                .filter(s -> s.getSlotId().equals(request.slotId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SKILL_SLOT_NOT_OWNED, request.slotId()));

        if (!slot.isReady()) {
            throw new BusinessException(ErrorCode.SKILL_ON_COOLDOWN, slot.getCoolTime());
        }

        SkillEntity skill = slot.getSkill();
        RelicEffects effects = relicEffects(gameState);
        SkillSynergies synergies = skillSynergies(gameState);
        List<String> log = new ArrayList<>();
        log.add("[%s] 사용!".formatted(skill.getName()));

        int attack = (int) Math.round((character.getAttack() + session.getTempAtkBonus())
                * effects.getAttackMultiplier() * gameState.alignment().attackMultiplier());
        int monsterHpBefore = totalMonsterHp(session);
        double critChance = BASE_CRIT_CHANCE + effects.getCritBonus() + daggerCritBonus(gameState);

        List<SessionMonster> targets = resolveTargets(session, skill, request.targetKeys());

        // 1) 플레이어 공격
        battleService.applyPlayerAttack(skill, attack, critChance,
                effects.getSkillDamageMultiplier() * synergies.skillDamageMultiplier(skill),
                effects.getBleedMultiplier() + synergies.getBleedMultiplierBonus(),
                effects.getCritBleedStacks(), synergies, targets, log);
        lifesteal(gameState, character, monsterHpBefore - totalMonsterHp(session), log);

        if (battleService.isAllMonsterDead(session.getMonsters())) {
            return processVictory(gameState, session, character, effects, log);
        }

        // 2) 상태이상 부여 + 쿨타임 갱신 + 도트 피해
        battleService.applyStatusEffect(skill, synergies.extraStacks(StatusEffect.ofCode(skill.getStatusEffect())), targets, log);
        battleService.updateCoolTimes(slots, slot.getSlotId(), effects.getCooldownReduction());
        battleService.applyDotDamage(session.getMonsters(), log);

        if (battleService.isAllMonsterDead(session.getMonsters())) {
            return processVictory(gameState, session, character, effects, log);
        }

        // 3) 몬스터 반격
        return processMonsterCounterattack(gameState, session, character, effects, 1.0, log);
    }

    /**
     * 방어 태세로 한 턴을 넘긴다. 받는 피해가 60% 줄고 모든 스킬 쿨타임이 1턴 진행된다.
     *
     * @param member 로그인 회원
     * @return 턴 결과
     */
    @Override
    @Transactional
    public TurnResultDTO guard(MemberEntity member) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        requirePhase(session, GameSession.PHASE_BATTLE, "전투 중이 아닙니다.");

        GameCharacterEntity character = character(gameState);
        RelicEffects effects = relicEffects(gameState);
        List<String> log = new ArrayList<>();
        log.add("방어 태세! 몸을 낮추고 공격에 대비한다");

        // 공격을 포기하는 대신: 받는 피해 감소 + 모든 스킬 쿨타임 1턴 진행
        battleService.updateCoolTimes(gameState.getGameSkillSlot(), null, effects.getCooldownReduction());
        battleService.applyDotDamage(session.getMonsters(), log);

        if (battleService.isAllMonsterDead(session.getMonsters())) {
            return processVictory(gameState, session, character, effects, log);
        }
        return processMonsterCounterattack(gameState, session, character, effects, PLAYER_GUARD_MULTIPLIER, log);
    }

    /**
     * 숨 고르기: 쓸 수 있는 스킬이 하나도 없는 턴에만 고를 수 있다.
     * 방어 태세(피해 -60%, 쿨타임 1턴)와 달리 피해는 그대로 받는 대신 쿨타임이 2턴 줄어든다.
     * "버티며 기다린다"와 "맞더라도 빨리 되찾는다" 중에서 고르게 하려는 것이다.
     */
    @Override
    @Transactional
    public TurnResultDTO passTurn(MemberEntity member) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        requirePhase(session, GameSession.PHASE_BATTLE, "전투 중이 아닙니다.");

        List<GameSkillSlotEntity> slots = gameState.getGameSkillSlot();
        if (gameState.hasReadySkill()) {
            throw new BusinessException(ErrorCode.PASS_NOT_ALLOWED);
        }

        GameCharacterEntity character = character(gameState);
        RelicEffects effects = relicEffects(gameState);
        List<String> log = new ArrayList<>();
        log.add("숨을 고르며 다음 기회를 노린다. (모든 스킬 쿨타임 -%d)".formatted(PASS_COOLDOWN_TICKS));

        battleService.tickCooldowns(slots, PASS_COOLDOWN_TICKS);
        battleService.applyDotDamage(session.getMonsters(), log);

        if (battleService.isAllMonsterDead(session.getMonsters())) {
            return processVictory(gameState, session, character, effects, log);
        }
        return processMonsterCounterattack(gameState, session, character, effects, 1.0, log);
    }

    /**
     * 도망치기: 보상 없이 전투를 끝내고 다음 길로 나아간다.
     * 등을 보이는 순간 적이 한 번 공격한다(방어 없음). 그 공격에 쓰러지면 패배다.
     * 보스전에서는 달아날 수 없다. 보스를 건너뛰고 다음 스테이지로 가는 길을 막기 위해서다.
     */
    @Override
    @Transactional
    public TurnResultDTO flee(MemberEntity member) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        requirePhase(session, GameSession.PHASE_BATTLE, "전투 중이 아닙니다.");
        if ("BOSS".equals(session.getNodeType())) {
            throw new BusinessException(ErrorCode.FLEE_NOT_ALLOWED);
        }

        GameCharacterEntity character = character(gameState);
        RelicEffects effects = relicEffects(gameState);
        List<String> log = new ArrayList<>();
        log.add("등을 돌려 달아난다!");

        // 등을 보인 순간의 공격만 받는다. 곧 전투가 끝나므로 다음 턴 예고는 굴리지 않고, 세션은 아래에서 한 번만 저장한다
        TurnResultDTO fallen = receiveMonsterAttack(gameState, session, character, effects, 1.0, log);
        if (fallen != null) {
            return fallen; // 달아나다 쓰러졌다
        }

        log.add("적을 따돌렸다. 전리품은 없다.");
        if (session.getWraithId() != null) {
            boolean faded = wraithService.escape(session.getWraithId());
            log.add(faded ? "세 번째로 등을 보이자 망령은 더 쫓아오지 않았다. 안개처럼 흩어져 다시는 나타나지 않는다."
                    : "망령은 쫓아오지 않았다. 다음 원정에도 같은 자리에서 기다릴 것이다.");
            session.setWraithId(null);
        }
        stats(session).recordFlee(session.getMonsters());
        remember(gameState, "[플레이어의 선택] 스테이지 %d에서 %s을(를) 상대하다 싸움을 포기하고 달아났다."
                .formatted(gameState.getCurrentStage(), session.getMonsters().stream()
                        .map(SessionMonster::getName).findFirst().orElse("적")));

        gameState.resetSkillCooldowns();
        session.setTempAtkBonus(0);
        session.setTempDefBonus(0);
        session.setMonsters(new ArrayList<>());
        session.setPhase(GameSession.PHASE_EXPLORE);
        gameSessionService.save(session);
        gameStateRepository.save(gameState);

        return fledResult(log, statusAssembler.toStatusDTO(gameState), session.getPhase());
    }

    /**
     * 스킬 버리기. 전투 중에는 쿨타임 회피 수단이 되고 조우 중에는 선택을 미루는 수단이 되므로 막는다.
     */
    @Override
    @Transactional
    public StatusDTO discardSkill(MemberEntity member, SkillDiscardRequest request) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        String phase = session.getPhase();
        if (!SKILL_DISCARD_PHASES.contains(phase)) {
            throw new BusinessException(ErrorCode.SKILL_DISCARD_NOT_ALLOWED, phase);
        }
        skillSlotService.discard(gameState, request.slotId());
        return statusAssembler.toStatusDTO(gameState);
    }

    /**
     * 소모품을 쓴다. 탐험 중에는 효과만 적용하고, 전투 중에는 턴을 소모해 적이 반격한다.
     * 쓴 소모품은 애그리거트 루트에서 빠지며 같은 응답의 상태에도 바로 반영된다.
     *
     * @param member  로그인 회원
     * @param request 소모품 칸 ID
     * @return 턴 결과
     * @throws BusinessException 탐험·전투가 아닌 단계면
     */
    @Override
    @Transactional
    public TurnResultDTO useConsumable(MemberEntity member, ConsumableUseRequest request) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());

        boolean inBattle = GameSession.PHASE_BATTLE.equals(session.getPhase());
        if (!inBattle && !GameSession.PHASE_EXPLORE.equals(session.getPhase())) {
            throw new BusinessException(ErrorCode.CONSUMABLE_NOT_ALLOWED);
        }

        GameConsumableInventoryEntity inventory = gameState.findConsumable(request.inventorySlotId());

        GameCharacterEntity character = character(gameState);
        ConsumableEntity consumable = inventory.getConsumable();
        List<String> log = new ArrayList<>();

        applyConsumableEffect(gameState, session, character, consumable, log);
        // 루트의 목록에서 빼면 orphanRemoval로 DB에서도 지워지고, 같은 응답의 상태에도 바로 반영된다
        gameState.consume(inventory);
        stats(session).recordConsumable();

        if (!inBattle) {
            gameSessionService.save(session);
            return ongoingResult(log, toBattleMonsterDTOs(session.getMonsters()),
                    statusAssembler.toStatusDTO(gameState), session.getPhase());
        }

        // 전투 중 소모품 사용은 턴을 소모한다
        RelicEffects effects = relicEffects(gameState);
        if (battleService.isAllMonsterDead(session.getMonsters())) {
            return processVictory(gameState, session, character, effects, log);
        }
        return processMonsterCounterattack(gameState, session, character, effects, 1.0, log);
    }

    /* ==================== 인카운터 ==================== */

    /**
     * 조우 선택지를 고른다. 효과를 적용하고, 고른 세력의 성향을 1 올리고, 선택을 AI 대화 기록에 남긴 뒤
     * 선택 이전에 만들어 둔 다음 노드는 버린다(다음 장면이 이 선택을 이어받도록).
     *
     * @param member  로그인 회원
     * @param request 선택지 번호
     * @return 선택 결과
     * @throws BusinessException 범위를 벗어난 번호면
     */
    @Override
    @Transactional
    public EncounterOutcomeDTO chooseEncounter(MemberEntity member, EncounterChoiceRequest request) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        requirePhase(session, GameSession.PHASE_ENCOUNTER, "인카운터 진행 중이 아닙니다.");

        List<EncounterChoice> choices = session.getChoices();
        if (request.choiceIndex() < 0 || request.choiceIndex() >= choices.size()) {
            throw new BusinessException(ErrorCode.INVALID_CHOICE);
        }

        EncounterChoice choice = choices.get(request.choiceIndex());
        GameCharacterEntity character = character(gameState);
        String outcome = applyEncounterEffect(gameState, character, choice);

        // 선택이 성향으로 쌓이고, AI 대화 기록에 남아 다음 장면과 결말이 이 선택을 이어받는다
        Faction faction = Faction.parse(choice.getFaction());
        gameState.gainAffinity(faction);
        stats(session).recordEncounter();
        remember(gameState, "[플레이어의 선택] 상황: %s / %s선택: %s / 결과: %s / 현재 성향: %s".formatted(
                shorten(session.getStory()), faction == null ? "" : "(" + faction.label() + "의 방식) ",
                choice.getLabel(), outcome, gameState.alignment().describe()));
        invalidatePrefetchedNode(gameState);

        gameState.gainScore(10 * gameState.getCurrentStage());
        gameStateRepository.save(gameState);

        // 플레이어가 인카운터 피해로 사망할 수도 있다
        if (character.getCurrentHp() <= 0 && !tryRevive(gameState, character, new ArrayList<>())) {
            terminate(gameState, session, false);
            gameSessionService.save(session);
            return new EncounterOutcomeDTO(outcome + " ... 치명적인 결과였다.", statusAssembler.toStatusDTO(gameState), session.getPhase());
        }

        session.setPhase(GameSession.PHASE_EXPLORE);
        session.setChoices(new ArrayList<>());
        gameSessionService.save(session);

        return new EncounterOutcomeDTO(outcome, statusAssembler.toStatusDTO(gameState), session.getPhase());
    }

    /* ==================== 보상 / 상점 ==================== */

    /**
     * 전투 보상 중 하나를 고르거나 건너뛴다. 스킬이 가득 찼으면 버릴 슬롯을 함께 받는다.
     *
     * @param member  로그인 회원
     * @param request 보상 종류, ID, 버릴 슬롯
     * @return 바뀐 상태
     * @throws BusinessException 후보에 없는 보상이면
     */
    @Override
    @Transactional
    public StatusDTO selectReward(MemberEntity member, RewardSelectRequest request) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        requirePhase(session, GameSession.PHASE_REWARD, "보상 선택 단계가 아닙니다.");

        RewardOptions options = session.getReward();
        String type = request.itemType() == null ? "SKIP" : request.itemType().toUpperCase();

        switch (type) {
            case "SKILL" -> {
                require(options != null && options.getSkillIds().contains(request.itemId()), "보상 후보에 없는 스킬입니다.");
                skillSlotService.learn(gameState, request.itemId(), request.replaceSlotId());
            }
            case "RELIC" -> {
                require(options != null && options.getRelicIds().contains(request.itemId()), "보상 후보에 없는 유물입니다.");
                addRelic(gameState, request.itemId());
            }
            case "CONSUMABLE" -> {
                require(options != null && options.getConsumableIds().contains(request.itemId()), "보상 후보에 없는 소모품입니다.");
                addConsumable(gameState, request.itemId());
            }
            case "SKIP" -> { /* 보상 포기 */ }
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, "알 수 없는 보상 타입: " + type);
        }

        // 상점 오픈
        session.setReward(null);
        session.setPhase(GameSession.PHASE_SHOP);
        session.setShop(shopService.generateStock(gameState));
        session.setShopRerolls(0);
        gameSessionService.save(session);

        return statusAssembler.toStatusDTO(gameState);
    }

    /**
     * 상점 진열품. 처음 들어왔을 때 한 번 뽑아 세션에 둔다.
     *
     * @param member 로그인 회원
     * @return 진열품 목록
     */
    @Override
    @Transactional
    public List<ShopItemDTO> getShop(MemberEntity member) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        requirePhase(session, GameSession.PHASE_SHOP, "상점 이용 단계가 아닙니다.");

        if (session.getShop() == null || session.getShop().isEmpty()) {
            session.setShop(shopService.generateStock(gameState));
            gameSessionService.save(session);
        }
        return toShopItems(session.getShop(), gameState);
    }

    /**
     * 상점에서 산다. 가격은 서버의 진열품 정보로 정하고, 같은 품목은 한 번만 살 수 있다.
     *
     * @param member  로그인 회원
     * @param request 품목 종류, ID, 버릴 슬롯
     * @return 바뀐 상태
     */
    @Override
    @Transactional
    public StatusDTO buyItem(MemberEntity member, PurchaseRequest request) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        requirePhase(session, GameSession.PHASE_SHOP, "상점 이용 단계가 아닙니다.");

        ShopStockItem stockItem = session.getShop().stream()
                .filter(item -> item.getItemType().equalsIgnoreCase(request.itemType())
                        && item.getItemId().equals(request.itemId())
                        && !item.isSold())
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SHOP_ITEM_UNAVAILABLE));

        shopService.applyPurchase(gameState, member, stockItem, request.replaceSlotId());
        stockItem.setSold(true);
        gameSessionService.save(session);

        return statusAssembler.toStatusDTO(gameState);
    }

    /**
     * 골드를 내고 진열품을 새로 뽑는다. 할 때마다 비용이 오른다.
     *
     * @param member 로그인 회원
     * @return 바뀐 화면 상태
     * @throws BusinessException 골드가 부족하면
     */
    @Override
    @Transactional
    public GameViewDTO rerollShop(MemberEntity member) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        requirePhase(session, GameSession.PHASE_SHOP, "상점 이용 단계가 아닙니다.");

        // 비용은 서버가 계산한다. 골드가 부족하면 deductGold가 예외를 던지고 재고는 그대로 남는다.
        gameState.deductGold(rerollCost(gameState, session));
        gameStateRepository.save(gameState);

        session.setShop(shopService.generateStock(gameState));
        session.setShopRerolls(session.getShopRerolls() + 1);
        gameSessionService.save(session);

        return getView(member);
    }

    /**
     * 끝난 원정의 AI 회고를 만든다. 한 번 만든 회고와 실패 여부는 세션에 두어 다시 부르지 않는다.
     *
     * @param member 로그인 회원
     * @return 회고, 만들 수 없으면 null
     */
    @Override
    @Transactional
    public RunReviewDTO getRunReview(MemberEntity member) {
        GameStateEntity gameState = gameStateRepository
                .findTopByMember_MemberIdOrderByCreatedAtDesc(member.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_RECORD_NOT_FOUND));

        // 마지막 전투 정보가 세션에 있어야 회고할 수 있다 (세션 TTL 24시간이 지나면 회고는 제공하지 않는다)
        GameSession session = gameSessionService.get(gameState.getGameStateId());
        if (session == null) return null;

        boolean cleared = GameSession.PHASE_CLEAR.equals(session.getPhase());
        if (!cleared && !GameSession.PHASE_GAMEOVER.equals(session.getPhase())) return null;

        if (session.getReview() != null || session.isReviewAttempted()) {
            return session.getReview();
        }

        // 연대기의 다음 장도 같은 호출에서 받는다: 앞선 장과 이번 원정의 선택을 함께 넘긴다
        RunReviewDTO review = runReviewService.review(gameState, session, cleared, previousChapters(gameState),
                recentChoices(gameState, CHRONICLE_CHOICE_LINES));
        session.setReview(review);
        session.setReviewAttempted(true);
        gameSessionService.save(session);
        try {
            chronicleService.record(gameState, cleared, review);
        } catch (Exception e) {
            log.warn("연대기 기록 실패 gameStateId={} 원인={}", gameState.getGameStateId(), e.getMessage());
        }
        return review;
    }

    /**
     * 상점을 나간다. 보스를 쓰러뜨린 직후라면 모닥불로, 아니면 탐험으로 간다.
     *
     * @param member 로그인 회원
     * @return 다음 화면 상태
     */
    @Override
    @Transactional
    public GameViewDTO leaveShop(MemberEntity member) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        requirePhase(session, GameSession.PHASE_SHOP, "상점 이용 단계가 아닙니다.");

        // 보스를 쓰러뜨린 직후에는 다음 구역으로 내려가기 전 모닥불에 들른다
        long node = gameState.getCurrentNodeId() == null ? 0 : gameState.getCurrentNodeId();
        session.setPhase(node >= NODES_PER_STAGE ? GameSession.PHASE_REST : GameSession.PHASE_EXPLORE);
        session.setMonsters(new ArrayList<>());
        session.setChoices(new ArrayList<>());
        session.setShop(new ArrayList<>());
        session.setTempAtkBonus(0);
        session.setTempDefBonus(0);
        gameSessionService.save(session);

        return getView(member);
    }

    /**
     * 모닥불: 스테이지 사이의 유일한 확정 회복 지점이자, 회복을 포기하고 다른 것을 얻을 수 있는 선택 지점.
     * 체력이 넉넉하면 단련이나 서원이 이득이고, 위태로우면 쉬어야 한다. 정답이 상황에 따라 달라지게 하는 것이 목적이다.
     */
    @Override
    @Transactional
    public GameViewDTO chooseRest(MemberEntity member, String choiceValue) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        requirePhase(session, GameSession.PHASE_REST, "모닥불 앞이 아닙니다.");

        GameCharacterEntity character = character(gameState);
        String choice = choiceValue == null ? "" : choiceValue.trim().toUpperCase();
        String story;

        switch (choice) {
            case "HEAL" -> {
                int healed = healing(gameState, (int) (character.getMaxHp() * BOSS_REST_HEAL_RATIO));
                character.heal(healed);
                story = "모닥불 곁에서 눈을 붙였다. HP %d 회복.".formatted(healed);
            }
            case "TRAIN" -> {
                character.applyAttackDelta(REST_TRAIN_ATTACK);
                character.applyDefenseDelta(REST_TRAIN_DEFENSE);
                story = "쉬지 않고 검을 갈았다. 공격력 +%d, 방어력 +%d.".formatted(REST_TRAIN_ATTACK, REST_TRAIN_DEFENSE);
            }
            case "HEAVEN", "ABYSS", "HUMAN" -> {
                Faction faction = Faction.valueOf(choice);
                gameState.gainAffinity(faction);
                story = switch (faction) {
                    case HEAVEN -> "불빛 너머의 흰 빛에 무릎을 꿇었다. 천상 성향 +1.";
                    case ABYSS -> "불씨 속의 속삭임에 이름을 내주었다. 마계 성향 +1.";
                    case HUMAN -> "누구에게도 빌지 않고 스스로에게 맹세했다. 인간 성향 +1.";
                };
                remember(gameState, "[플레이어의 선택] 보스를 쓰러뜨린 뒤 모닥불 앞에서 %s에 서원했다. 현재 성향: %s"
                        .formatted(faction.label(), gameState.alignment().describe()));
                invalidatePrefetchedNode(gameState);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, "알 수 없는 선택입니다: " + choiceValue);
        }

        gameStateRepository.save(gameState);
        session.setStory(story);
        session.setPhase(GameSession.PHASE_EXPLORE);
        gameSessionService.save(session);
        return getView(member);
    }

    /* ==================== 종료 요약 ==================== */

    /**
     * 가장 최근 원정의 요약 (끝난 원정 포함).
     *
     * @param member 로그인 회원
     * @return 요약
     */
    @Override
    @Transactional
    public GameSummaryDTO getSummary(MemberEntity member) {
        GameStateEntity gameState = gameStateRepository
                .findTopByMember_MemberIdOrderByCreatedAtDesc(member.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_RECORD_NOT_FOUND));
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        return buildSummary(gameState, session);
    }

    /* ==================== 내부 로직 ==================== */

    /**
     * 진행 중인 원정을 찾는다.
     *
     * @param member 로그인 회원
     * @return 진행 중인 게임
     * @throws java.util.NoSuchElementException 없으면
     */
    private GameStateEntity activeGame(MemberEntity member) {
        return gameStateRepository.findByMember_MemberIdAndIsActiveTrue(member.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_FOUND));
    }

    /**
     * 게임의 캐릭터 (한 판에 하나).
     *
     * @param gameState 게임
     * @return 캐릭터
     */
    private GameCharacterEntity character(GameStateEntity gameState) {
        return gameState.getGameCharacter().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("캐릭터 정보가 없습니다."));
    }

    /**
     * 플레이어의 결정을 AI 대화 기록에 남긴다.
     * MonsterRagService는 대화 기록(ChatMemory)을 읽어 서사를 잇기 때문에, 여기 남긴 한 줄이 이후 장면의 재료가 된다.
     * 실패해도 게임 진행에는 영향이 없어야 한다.
     */
    private void remember(GameStateEntity gameState, String note) {
        try {
            chatMemory.add(conversationId(gameState), new UserMessage(note));
        } catch (Exception e) {
            log.warn("선택 기록 저장 실패 gameStateId={} 원인={}", gameState.getGameStateId(), e.getMessage());
        }
    }

    /**
     * 미리 만들어 둔 다음 노드를 버린다.
     * 프리페치는 선택 "이전"의 기록으로 만들어졌으므로 그대로 쓰면 방금 내린 선택이 다음 장면에 반영되지 않는다.
     * 조우 직후의 전진 한 번은 대기 시간이 생기지만, 선택이 곧바로 이야기에 이어지는 쪽을 택했다.
     * (프리페치가 아직 생성 중이면 뒤늦게 저장될 수 있다. 그 경우 반영은 한 노드 늦어진다)
     */
    private void invalidatePrefetchedNode(GameStateEntity gameState) {
        try {
            int stage = gameState.getCurrentStage();
            int node = gameState.getCurrentNodeId() == null ? 0 : gameState.getCurrentNodeId().intValue();
            if (node >= NODES_PER_STAGE) {
                stageCacheService.delete(conversationId(gameState), stage + 1, 1);
            } else {
                stageCacheService.delete(conversationId(gameState), stage, node + 1);
            }
        } catch (Exception e) {
            log.warn("프리페치 무효화 실패: {}", e.getMessage());
        }
    }

    /** 성향에 따른 결말 한 줄 (서버가 정하는 확정 결과. AI 요약은 이 줄을 근거로 살을 붙인다) */
    private String endingLine(Alignment alignment) {
        Faction dominant = alignment.dominant();
        if (dominant == null) {
            return "마계의 문이 닫혔다. 천상도 마계도 이 인간을 제 편이라 부르지 못했고, 왕국에는 주인 없는 새벽이 찾아온다.";
        }
        return switch (dominant) {
            case HEAVEN -> "천상의 빛이 문을 봉인했다. 정화의 불은 멈췄고, 살아남은 자들은 하늘에 진 빚을 영원히 갚게 될 것이다.";
            case ABYSS -> "문은 닫히지 않았다. 다만 이제 그 문의 주인은 왕이 아니라 당신이다.";
            case HUMAN -> "인간의 손이 문을 닫았다. 누구의 가호도 없이. 천사와 악마는 처음으로 인간을 두려워하기 시작했다.";
        };
    }

    /**
     * 줄바꿈을 접고 120자로 자른다 (AI 기록용).
     *
     * @param text 원문
     * @return 줄인 문장
     */
    private String shorten(String text) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "...";
    }

    /**
     * 게임 서사의 대화 기록 ID.
     *
     * @param gameState 게임
     * @return game-{게임 ID}
     */
    private String conversationId(GameStateEntity gameState) {
        return "game-" + gameState.getGameStateId();
    }

    /**
     * 지금 단계가 기대한 단계인지 확인한다.
     *
     * @param session  게임 세션
     * @param expected 기대하는 단계
     * @param message  아닐 때의 안내
     * @throws BusinessException 단계가 다르면
     */
    private void requirePhase(GameSession session, String expected, String message) {
        if (!expected.equals(session.getPhase())) {
            throw new BusinessException(ErrorCode.INVALID_PHASE, message, session.getPhase());
        }
    }

    /**
     * 조건이 맞지 않으면 잘못된 요청으로 거부한다.
     *
     * @param condition 지켜야 할 조건
     * @param message   거부 문구
     * @throws BusinessException 조건이 거짓이면
     */
    private void require(boolean condition, String message) {
        if (!condition) throw new BusinessException(ErrorCode.INVALID_REQUEST, message);
    }

    /**
     * 보유 유물로 효과와 유물 조합 시너지를 계산한다.
     *
     * @param gameState 게임
     * @return 유물 효과
     */
    private RelicEffects relicEffects(GameStateEntity gameState) {
        List<String> names = gameState.getGameRelicInventory() == null ? List.of()
                : gameState.getGameRelicInventory().stream()
                    .map(inventory -> inventory.getRelic().getName())
                    .toList();
        return RelicEffects.of(names);
    }

    /**
     * 보유 스킬로 스킬 조합 시너지를 계산한다.
     *
     * @param gameState 게임
     * @return 스킬 시너지
     */
    private SkillSynergies skillSynergies(GameStateEntity gameState) {
        if (gameState.getGameSkillSlot() == null) return SkillSynergies.NONE;
        return SkillSynergies.of(gameState.getGameSkillSlot().stream()
                .map(GameSkillSlotEntity::getSkill)
                .filter(Objects::nonNull)
                .toList());
    }

    /**
     * 이번 원정에 건 서약.
     *
     * @param gameState 게임
     * @return 서약 집합
     */
    private Set<Pact> pacts(GameStateEntity gameState) {
        return Pact.parse(gameState.getPacts());
    }

    /** 서약 효과: 적 HP / 공격력 상승, 보스 선제 격노 */
    private void applyPacts(List<SessionMonster> monsters, Set<Pact> pacts) {
        if (pacts.isEmpty()) return;
        for (SessionMonster monster : monsters) {
            if (pacts.contains(Pact.IRON_FOE)) {
                int hp = Math.max(1, (int) Math.round(monster.getMaxHp() * 1.2));
                monster.setMaxHp(hp);
                monster.setCurrentHp(hp);
            }
            if (pacts.contains(Pact.FURY)) {
                monster.setAtk(Math.max(1, (int) Math.round(monster.getAtk() * 1.15)));
            }
            if (pacts.contains(Pact.WRATHFUL_LORDS) && monster.getMonsterType() == 2) {
                monster.setEnraged(true);
                monster.setAtk(Math.max(1, (int) Math.round(monster.getAtk() * 1.3)));
            }
        }
    }

    /** 왕국의 정세: 적 HP / 공격력 배율 (정세가 없는 예전 원정은 그대로) */
    private void applyKingdom(List<SessionMonster> monsters, KingdomState kingdom) {
        if (kingdom == null) return;
        for (SessionMonster monster : monsters) {
            if (kingdom.enemyHpMultiplier() != 1.0) {
                int hp = Math.max(1, (int) Math.round(monster.getMaxHp() * kingdom.enemyHpMultiplier()));
                monster.setMaxHp(hp);
                monster.setCurrentHp(hp);
            }
            if (kingdom.enemyAttackMultiplier() != 1.0) {
                monster.setAtk(Math.max(1, (int) Math.round(monster.getAtk() * kingdom.enemyAttackMultiplier())));
            }
        }
    }

    /** 살아 있는 적의 HP 합 */
    private static int totalMonsterHp(GameSession session) {
        return session.getMonsters() == null ? 0 : session.getMonsters().stream().mapToInt(SessionMonster::getCurrentHp).sum();
    }

    /** 마계의 낙인: 준 피해의 일부를 HP로 되돌린다 */
    private void lifesteal(GameStateEntity gameState, GameCharacterEntity character, int dealt, List<String> log) {
        double ratio = gameState.alignment().lifestealRatio();
        if (ratio <= 0 || dealt <= 0 || character.getCurrentHp() >= character.getMaxHp()) return;
        int healed = healing(gameState, Math.max(1, (int) Math.round(dealt * ratio)));
        character.heal(healed);
        log.add("마계의 낙인이 피를 들이킨다. HP %d 회복".formatted(healed));
    }

    /** 회복량 계산. 서약 '옅은 피'가 걸려 있으면 모든 회복이 절반이 된다 */
    private int healing(GameStateEntity gameState, int amount) {
        return pacts(gameState).contains(Pact.THIN_BLOOD) ? Math.max(1, amount / 2) : amount;
    }

    /** 재입고 비용: 횟수마다 오르고, 깊은 스테이지일수록 물가가 비싸다 */
    private int rerollCost(GameStateEntity gameState, GameSession session) {
        double stageFactor = (1.0 + 0.15 * (gameState.getCurrentStage() - 1))
                * (pacts(gameState).contains(Pact.GREED_TAX) ? 1.3 : 1.0);
        return (int) Math.round((SHOP_REROLL_BASE_COST + SHOP_REROLL_STEP * session.getShopRerolls()) * stageFactor);
    }

    /**
     * 갈림길 효과 적용.
     * 위험한 길: 적 HP·공격력 상승 / 은밀한 길: 기습으로 적 전원이 첫 턴에 움직이지 못한다
     */
    private void applyPath(List<SessionMonster> monsters, PathChoice path) {
        for (SessionMonster monster : monsters) {
            if (path.enemyMultiplier() != 1.0) {
                int hp = Math.max(1, (int) Math.round(monster.getMaxHp() * path.enemyMultiplier()));
                monster.setMaxHp(hp);
                monster.setCurrentHp(hp);
                monster.setAtk(Math.max(1, (int) Math.round(monster.getAtk() * path.enemyMultiplier())));
            }
            if (path.ambush()) {
                monster.getBuffs().merge(BattleServiceImpl.BUFF_STUN, 1, Integer::sum);
            }
        }
    }

    /**
     * 단검을 들었으면 치명타 확률이 오른다.
     *
     * @param gameState 게임
     * @return 추가 치명타 확률 (단검이 아니면 0)
     */
    private double daggerCritBonus(GameStateEntity gameState) {
        GameEquipmentEntity equipment = gameState.getGameEquipment();
        if (equipment != null && equipment.getWeapon() != null && equipment.getWeapon().getType() == Type.DAGGER) {
            return DAGGER_CRIT_BONUS;
        }
        return 0.0;
    }

    /**
     * AI(RAG) 노드 생성. 실패 시 서버 자체 폴백으로 게임 진행을 보장한다.
     */
    private AsyncStageResponseDTO generateNodeContent(int stage, int node, String conversationId) {
        Optional<AsyncStageResponseDTO> generated = aiCallRunner.call(
                () -> monsterRagService.chat(stage, node, conversationId), aiNodeBudgetSeconds,
                "노드 생성 stage=%d node=%d".formatted(stage, node));
        if (generated.isPresent()) return generated.get();

        boolean isBoss = node % NODES_PER_STAGE == 0;
        if (isBoss || random.nextDouble() > 0.3) {
            List<MonsterDTO> monsters = isBoss
                    ? monsterService.getRandomMonsterBoss()
                    : monsterService.getRandomMonsterNormal();
            return AsyncStageResponseDTO.battle(fallbackStory(stage, true), monsters);
        }
        return AsyncStageResponseDTO.encounter(fallbackStory(stage, false),
                new Encounter("머리 위에서는 희미한 종소리가, 발밑에서는 낮은 속삭임이 들려온다.", List.of()));
    }

    /**
     * AI가 서사를 만들지 못했을 때 쓰는 기본 서사.
     *
     * @param stage  스테이지
     * @param battle 전투면 true, 조우면 false
     * @return 서사 한 줄
     */
    private String fallbackStory(int stage, boolean battle) {
        String place = switch (stage) {
            case 1 -> "천상의 빛이 희미하게 걸린 무너진 정문";
            case 2 -> "왕실 기사단의 연무장";
            case 3 -> "궁정 마법사의 탑";
            case 4 -> "축축한 지하 묘지";
            default -> "마계의 문으로 향하는 회랑";
        };
        return battle
                ? place + " — 마계의 숨결에 뒤틀린 것이 어둠 너머에서 다가온다."
                : place + " — 낯선 정적 속에서 빛과 그림자가 당신의 선택을 기다린다.";
    }

    /**
     * 노드 종류를 정한다: 10번째 노드는 보스, 엘리트가 있으면 강적, 아니면 전투.
     *
     * @param node     노드 번호
     * @param monsters 출현한 몬스터
     * @return BOSS / ELITE / BATTLE
     */
    private String resolveNodeType(int node, List<SessionMonster> monsters) {
        if (node % NODES_PER_STAGE == 0) return "BOSS";
        boolean hasElite = monsters.stream().anyMatch(m -> m.getMonsterType() == 1);
        return hasElite ? "ELITE" : "BATTLE";
    }

    /**
     * 필드 강도 시스템: AI는 몬스터 종류(서사)만 결정하고,
     * 능력치는 스테이지 x 난이도 기반의 확정 수치를 서버가 부여한다.
     */
    private List<SessionMonster> buildSessionMonsters(List<MonsterDTO> monsterDTOs, GameStateEntity gameState) {
        BaseStats base = baseStats(gameState);
        List<SessionMonster> monsters = new ArrayList<>();
        int key = 0;
        for (MonsterDTO dto : limitElites(monsterDTOs, base.stage())) {
            monsters.add(newSessionMonster(key++, dto.monsterId(), dto.name(), dto.monsterType(),
                    dto.scoreReward(), dto.description(), base));
        }
        return monsters;
    }

    /** 이 스테이지·난이도에서 일반 몬스터의 기준 능력치 */
    private record BaseStats(int stage, int hp, int atk, int def) {
    }

    /**
     * 이 스테이지·난이도의 기준 능력치. {@code monster_base_status}에 행이 없으면 식으로 계산한다.
     *
     * @param gameState 게임
     * @return 기준 능력치
     */
    private BaseStats baseStats(GameStateEntity gameState) {
        int stage = Math.min(FINAL_STAGE, Math.max(1, gameState.getCurrentStage()));
        short difficultyIndex = (short) gameState.getDifficulty().ordinal();

        MonsterBaseStatusEntity base = monsterBaseStatusRepository
                .findByStageAndDifficulty((short) stage, difficultyIndex)
                .orElse(null);
        double factor = difficultyFactor(gameState.getDifficulty());

        int hp = base != null ? base.getBaseHp() : (int) ((40 + stage * 25) * factor);
        int atk = base != null ? base.getBaseAtk() : (int) ((7 + stage * 4) * factor);
        int def = base != null ? base.getBaseDef() : (int) ((1 + stage * 2) * factor);
        return new BaseStats(stage, hp, atk, def);
    }

    /**
     * 몬스터 한 마리를 만든다. 종류(일반 / 엘리트 / 보스)에 따라 기준 능력치에 배율을 곱하고 HP에 +-10% 편차를 준다.
     *
     * @param key         전투 안에서의 번호
     * @param monsterId   몬스터 ID (망령처럼 DB에 없는 몬스터는 null)
     * @param name        이름
     * @param monsterType 0 일반 / 1 엘리트 / 2 보스
     * @param scoreReward 처치 점수
     * @param description 설명
     * @param base        기준 능력치
     * @return 세션 몬스터
     */
    private SessionMonster newSessionMonster(int key, Long monsterId, String name, short monsterType,
                                             int scoreReward, String description, BaseStats base) {
        double hpMult = switch (monsterType) { case 2 -> 2.8; case 1 -> 1.6; default -> 1.0; };
        double atkMult = switch (monsterType) { case 2 -> 1.55; case 1 -> 1.25; default -> 1.0; };
        double defMult = switch (monsterType) { case 2 -> 1.5; case 1 -> 1.2; default -> 1.0; };
        double variance = 0.9 + random.nextDouble() * 0.2; // +-10%

        SessionMonster monster = new SessionMonster();
        monster.setKey(key);
        monster.setMonsterId(monsterId);
        monster.setName(name);
        monster.setMonsterType(monsterType);
        int hp = Math.max(1, (int) (base.hp() * hpMult * variance));
        monster.setMaxHp(hp);
        monster.setCurrentHp(hp);
        monster.setAtk(Math.max(1, (int) (base.atk() * atkMult)));
        monster.setDef(Math.max(0, (int) (base.def() * defMult)));
        monster.setScoreReward(scoreReward);
        monster.setDescription(description);
        return monster;
    }

    /**
     * 한 전투에 등장하는 엘리트 수 제한 (보스전 제외).
     * AI는 서사에 어울리는 몬스터를 고를 뿐 난이도를 고려하지 않아, 초반 노드에 엘리트가 여럿 나오는 일이 있었다.
     * 난이도는 서버의 책임이므로 여기서 조정한다: 스테이지 1~2는 엘리트 1마리, 그 이후는 2마리까지.
     */
    private List<MonsterDTO> limitElites(List<MonsterDTO> monsterDTOs, int stage) {
        int allowed = stage <= 2 ? 1 : 2;
        List<MonsterDTO> result = new ArrayList<>();
        int elites = 0;
        for (MonsterDTO dto : monsterDTOs) {
            if (dto.monsterType() == 1 && ++elites > allowed) continue;
            result.add(dto);
        }
        return result;
    }

    /**
     * 난이도에 따른 몬스터 능력치 배율 (기준 능력치가 없을 때의 계산용).
     *
     * @param difficulty 난이도
     * @return 배율
     */
    private double difficultyFactor(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 0.8;
            case NORMAL -> 1.0;
            case HARD -> 1.3;
            case HELL -> 1.8;
        };
    }

    /**
     * 난이도에 따른 점수·골드 배율.
     *
     * @param difficulty 난이도
     * @return 배율
     */
    private double rewardMultiplier(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 0.7;
            case NORMAL -> 1.0;
            case HARD -> 1.3;
            case HELL -> 2.0;
        };
    }

    /**
     * 스킬이 때릴 대상을 정한다. 전체 공격이면 살아 있는 적 전부, 단일이면 고른 대상(없거나 쓰러졌으면 첫 번째 적).
     *
     * @param session    게임 세션
     * @param skill      쓴 스킬
     * @param targetKeys 고른 대상
     * @return 대상
     * @throws IllegalStateException 살아 있는 적이 없으면
     */
    private List<SessionMonster> resolveTargets(GameSession session, SkillEntity skill, List<Integer> targetKeys) {
        List<SessionMonster> alive = session.getMonsters().stream()
                .filter(SessionMonster::isAlive)
                .toList();
        if (alive.isEmpty()) throw new IllegalStateException("공격할 대상이 없습니다.");

        if (skill.getTarget() == Target.EnemyAll) {
            return alive;
        }
        if (targetKeys != null && !targetKeys.isEmpty()) {
            Set<Integer> keys = new HashSet<>(targetKeys);
            List<SessionMonster> selected = alive.stream()
                    .filter(m -> keys.contains(m.getKey()))
                    .limit(1)
                    .toList();
            if (!selected.isEmpty()) return selected;
        }
        return List.of(alive.get(0));
    }

    /**
     * @param incomingMultiplier 플레이어가 받는 피해 배율 (방어 태세면 1.0 미만)
     */
    private TurnResultDTO processMonsterCounterattack(GameStateEntity gameState, GameSession session,
                                                      GameCharacterEntity character, RelicEffects effects,
                                                      double incomingMultiplier, List<String> log) {
        TurnResultDTO fallen = receiveMonsterAttack(gameState, session, character, effects, incomingMultiplier, log);
        if (fallen != null) return fallen;

        // 다음 턴 행동 예고 (플레이어는 이것을 보고 공격/방어/대상을 정한다)
        battleService.rollIntents(session.getMonsters());
        gameSessionService.save(session);
        return ongoingResult(log, toBattleMonsterDTOs(session.getMonsters()),
                statusAssembler.toStatusDTO(gameState), session.getPhase());
    }

    /**
     * 적의 공격을 받는다. 쓰러지면 부활을 시도하고, 그래도 쓰러지면 패배로 원정을 끝낸다.
     *
     * @param incomingMultiplier 플레이어가 받는 피해 배율 (방어 태세면 1.0 미만)
     * @return 패배했으면 그 결과(세션 저장까지 끝남), 살아 있으면 null
     */
    private TurnResultDTO receiveMonsterAttack(GameStateEntity gameState, GameSession session,
                                               GameCharacterEntity character, RelicEffects effects,
                                               double incomingMultiplier, List<String> log) {
        // 이번 턴 플레이어의 공격으로 보스 HP가 절반 아래로 떨어졌다면 격노
        battleService.applyEnrage(session.getMonsters(), log);

        int defense = character.getDefense() + session.getTempDefBonus() + effects.getDefenseBonus();
        int damage = battleService.monsterAttackDamage(session.getMonsters(), defense,
                effects.getFlatDamageReduction(),
                incomingMultiplier * gameState.alignment().damageTakenMultiplier(), log);
        character.takeDamage(damage);
        stats(session).recordTurn();
        stats(session).recordDamageTaken(damage);

        if (character.getCurrentHp() > 0 || tryRevive(gameState, character, log)) {
            return null;
        }
        // 패배 처리
        stats(session).recordBattleEnd(session.getMonsters(), false);
        terminate(gameState, session, false);
        log.add("당신은 쓰러졌다... 모험이 여기서 끝났다.");
        gameSessionService.save(session);
        return lostResult(log, toBattleMonsterDTOs(session.getMonsters()),
                statusAssembler.toStatusDTO(gameState), session.getPhase());
    }

    /**
     * 불사조의 깃털: hasPhoenix 플래그 또는 인벤토리의 부활 아이템으로 1회 부활
     */
    private boolean tryRevive(GameStateEntity gameState, GameCharacterEntity character, List<String> log) {
        if (Boolean.TRUE.equals(gameState.getHasPhoenix())) {
            gameState.consumePhoenix();
            gameStateRepository.save(gameState);
            character.revive();
            log.add("불사조의 깃털이 타오르며 되살아났다! (HP 50% 회복)");
            return true;
        }
        Optional<GameConsumableInventoryEntity> phoenix =
                gameState.findConsumableByEffect(com.renovandelve.catalog.domain.consumableentity.Type.RESURRECTION);
        if (phoenix.isPresent()) {
            gameState.consume(phoenix.get());
            character.revive();
            log.add("불사조의 깃털이 타오르며 되살아났다! (HP 50% 회복)");
            return true;
        }
        return false;
    }

    /**
     * 승리 처리: 점수·골드·경험치를 주고(난이도·갈림길·서약·유물·가호 배율 반영), 레벨업과 천상의 가호 회복을 적용한 뒤
     * 마지막 보스면 원정을 끝내고, 아니면 보상 후보를 만들어 보상 단계로 넘긴다.
     *
     * @param gameState 게임
     * @param session   게임 세션
     * @param character 캐릭터
     * @param effects   유물 효과
     * @param log       전투 기록
     * @return 턴 결과 (WIN)
     */
    private TurnResultDTO processVictory(GameStateEntity gameState, GameSession session,
                                         GameCharacterEntity character, RelicEffects effects, List<String> log) {
        int stage = gameState.getCurrentStage();
        int node = gameState.getCurrentNodeId() == null ? 1 : gameState.getCurrentNodeId().intValue();
        PathChoice path = PathChoice.parse(session.getPath());
        double multiplier = rewardMultiplier(gameState.getDifficulty()) * path.rewardMultiplier();

        stats(session).recordTurn();
        stats(session).recordBattleEnd(session.getMonsters(), true);

        int totalScore = 0, totalGold = 0, totalExp = 0;
        for (SessionMonster monster : session.getMonsters()) {
            int score = monster.getScoreReward() > 0 ? monster.getScoreReward()
                    : switch (monster.getMonsterType()) { case 2 -> 400; case 1 -> 120; default -> 50; };
            score += stage * 10;
            totalScore += score;
            totalGold += score / 3;
            totalExp += switch (monster.getMonsterType()) { case 2 -> 200; case 1 -> 70; default -> 30; };
        }
        totalScore = (int) (totalScore * multiplier * Pact.scoreMultiplier(pacts(gameState)));
        Alignment alignment = gameState.alignment();
        totalGold = (int) (totalGold * multiplier * effects.getGoldMultiplier() * alignment.goldMultiplier());
        // 고른 길은 경험치에도 걸린다 (v0.9.0: 위험한 길의 보상이 골드·점수뿐이라 실제 전투력으로 이어지지 않았다)
        totalExp = (int) Math.round(totalExp * path.rewardMultiplier());
        totalExp = (int) Math.round(totalExp * effects.getExpMultiplier());
        KingdomState kingdom = gameState.kingdomState();
        if (kingdom != null) {
            totalGold = (int) (totalGold * kingdom.goldMultiplier());
            totalExp = (int) (totalExp * kingdom.expMultiplier());
        }

        gameState.gainScore(totalScore);
        gameState.gainGold(totalGold);
        stats(session).recordGold(totalGold);

        int levelUps = character.gainExp(totalExp);
        log.add("승리! 점수 +%d, 골드 +%d, 경험치 +%d".formatted(totalScore, totalGold, totalExp));
        if (effects.getVictoryHeal() > 0) {
            int healed = Math.min(effects.getVictoryHeal(), character.getMaxHp() - character.getCurrentHp());
            character.heal(effects.getVictoryHeal());
            if (healed > 0) log.add("유물의 가호로 HP %d 회복".formatted(healed));
        }
        if (path.rewardNote() != null) {
            log.add(path.rewardNote()); // 고른 길이 보상에 반영됐음을 드러낸다
        }
        if (levelUps > 0) {
            log.add("레벨 업! Lv.%d (최대HP/공격/방어 상승)".formatted(character.getLevel()));
        }

        if (alignment.victoryHealRatio() > 0) {
            int blessed = healing(gameState, Math.max(1, (int) (character.getMaxHp() * alignment.victoryHealRatio())));
            character.heal(blessed);
            log.add("천상의 가호가 상처를 감싼다. HP %d 회복".formatted(blessed));
        }

        // 보스를 쓰러뜨린 사실은 이후 서사가 이어받을 수 있게 기록해 둔다
        if (node >= NODES_PER_STAGE) {
            String bossName = session.getMonsters().stream()
                    .filter(m -> m.getMonsterType() == 2).map(SessionMonster::getName).findFirst().orElse("구역의 지배자");
            remember(gameState, "[플레이어의 선택] 스테이지 %d의 보스 '%s'을(를) 쓰러뜨리고 더 깊은 곳으로 내려간다."
                    .formatted(stage, bossName));
        }

        // 망령을 쓰러뜨렸으면 그 원정에서 가졌던 유물 하나가 보상 후보로 돌아온다
        Long recoveredRelicId = null;
        if (session.getWraithId() != null) {
            List<Long> owned = gameState.getGameRelicInventory() == null ? List.of()
                    : gameState.getGameRelicInventory().stream().map(i -> i.getRelic().getRelicId()).toList();
            recoveredRelicId = wraithService.slay(session.getWraithId(), owned).orElse(null);
            log.add("망령이 흩어진다. 지난 원정의 당신은 이제 이 자리에 없다.");
            if (recoveredRelicId != null) log.add(RECOVERED_RELIC_LINE);
            remember(gameState, "[플레이어의 선택] 스테이지 %d에서 지난 원정에 쓰러진 자신의 망령을 쓰러뜨렸다.".formatted(stage));
            session.setWraithId(null);
        }

        // 쿨타임 초기화
        gameState.resetSkillCooldowns();

        // 임시 버프 해제
        session.setTempAtkBonus(0);
        session.setTempDefBonus(0);

        boolean cleared = stage >= FINAL_STAGE && node >= NODES_PER_STAGE;
        if (cleared) {
            terminate(gameState, session, true);
            log.add(endingLine(gameState.alignment()));
            remember(gameState, "[모험의 결말] 악마화된 왕을 쓰러뜨렸다. " + endingLine(gameState.alignment()));
            gameSessionService.save(session);
            gameStateRepository.save(gameState);
            return clearedResult(log, toBattleMonsterDTOs(session.getMonsters()),
                    statusAssembler.toStatusDTO(gameState), session.getPhase());
        }

        // 보상 후보 생성 -> REWARD 단계
        // 강한 적일수록 유물을 고를 기회가 많다: 일반 1 / 엘리트 2 / 보스 3 (+ 위험한 길 보너스)
        int relicOptions = switch (String.valueOf(session.getNodeType())) {
            case "BOSS" -> 3;
            case "ELITE", "WRAITH" -> 2;
            default -> 1;
        } + path.bonusRelicOptions();
        // 계약 조항 '전리품은 그의 몫': 이번 전리품을 고르지 못하고 바로 상점으로 간다
        DemonContract contract = gameState.demonContract();
        if (contract != null && contract.clause() == DemonContract.Clause.SPOILS_CLAIM) {
            gameState.bindContract(null);
            log.remove(RECOVERED_RELIC_LINE); // 보상 후보라고 약속한 줄은 거둔다
            log.add(recoveredRelicId == null
                    ? "계약서의 작은 글씨가 타오른다: 전리품은 그의 몫이다. 붉은 손이 전리품을 거둬 간다. (계약 종료)"
                    : "계약서의 작은 글씨가 타오른다: 전리품은 그의 몫이다. 붉은 손이 낯익은 유물까지 거둬 간다. (계약 종료)");
            session.setPathNote(null);
            session.setReward(null);
            session.setPhase(GameSession.PHASE_SHOP);
            session.setShop(shopService.generateStock(gameState));
            session.setShopRerolls(0);
            gameSessionService.save(session);
            gameStateRepository.save(gameState);
            return wonResult(log, toBattleMonsterDTOs(session.getMonsters()),
                    statusAssembler.toStatusDTO(gameState), null, session.getPhase());
        }

        // 강한 적의 전리품은 더 깊은 스테이지의 등급 확률을 쓴다 (엘리트·망령 +1, 보스 +2)
        int rarityTier = stage + switch (String.valueOf(session.getNodeType())) {
            case "BOSS" -> 2;
            case "ELITE", "WRAITH" -> 1;
            default -> 0;
        };
        RewardOptions reward = buildRewardOptions(gameState, totalExp, totalGold, relicOptions, rarityTier);
        if (recoveredRelicId != null && !reward.getRelicIds().contains(recoveredRelicId)) {
            List<Long> relicIds = new ArrayList<>(reward.getRelicIds());
            relicIds.add(0, recoveredRelicId);
            reward.setRelicIds(relicIds);
        }
        session.setReward(reward);
        session.setPathNote(null);
        session.setPhase(GameSession.PHASE_REWARD);
        gameSessionService.save(session);
        gameStateRepository.save(gameState);

        return wonResult(log, toBattleMonsterDTOs(session.getMonsters()),
                statusAssembler.toStatusDTO(gameState), toRewardView(session.getReward()), session.getPhase());
    }

    /**
     * 전투 보상 후보를 만든다: 아직 없는 스킬, 아직 없는 유물, 소모품을 섞어 고른다.
     * 가호 3단계면 그 세력이 후보를 하나 더 얹는다(천상: 소모품, 마계: 유물, 인간: 스킬).
     *
     * @param gameState    게임
     * @param exp          이미 지급한 경험치 (표시용)
     * @param gold         이미 지급한 골드 (표시용)
     * @param relicOptions 유물 후보 수 (일반 1, 강적 2, 보스 3, 위험한 길 +1)
     * @param rarityTier   등급 확률 단계 (스테이지 + 강한 적 보정)
     * @return 보상 후보
     */
    private RewardOptions buildRewardOptions(GameStateEntity gameState, int exp, int gold, int relicOptions,
                                             int rarityTier) {
        RewardOptions options = new RewardOptions();
        options.setExp(exp);
        options.setGold(gold);

        // 스킬·유물 후보는 등급 확률로 고른다. 강한 적의 전리품일수록 깊은 단계의 확률을 쓴다
        // 가호 3단계에 이르면 그 세력이 보상 후보를 하나 더 얹어 준다 (천상: 소모품 / 마계: 유물 / 인간: 스킬)
        Alignment alignment = gameState.alignment();
        options.setSkillIds(RarityTable.pick(skillRepository.findBySkillNotOwner(gameState.getGameStateId()),
                        SkillEntity::getRarity, 3 + alignment.bonusSkillOptions(), rarityTier, random).stream()
                .map(SkillEntity::getSkillId).collect(Collectors.toList()));

        options.setRelicIds(RarityTable.pick(relicRepository.findByRelicNotOwner(gameState.getGameStateId()),
                        RelicEntity::getRarity, relicOptions + alignment.bonusRelicOptions(), rarityTier, random).stream()
                .map(RelicEntity::getRelicId).collect(Collectors.toList()));

        List<ConsumableEntity> consumables = new ArrayList<>(consumableRepository.findAll());
        Collections.shuffle(consumables, random);
        options.setConsumableIds(consumables.stream().limit(2 + alignment.bonusConsumableOptions())
                .map(ConsumableEntity::getConsumableId).collect(Collectors.toList()));

        return options;
    }

    /**
     * 소모품 효과를 적용한다 (회복, 해독, 일시 강화, 폭탄 피해, 부활 준비).
     *
     * @param gameState  게임
     * @param session    게임 세션 (일시 강화를 담는다)
     * @param character  캐릭터
     * @param consumable 쓴 소모품
     * @param log        기록
     */
    private void applyConsumableEffect(GameStateEntity gameState, GameSession session,
                                       GameCharacterEntity character, ConsumableEntity consumable, List<String> log) {
        int value = consumable.getEffectValue();
        switch (consumable.getEffectType()) {
            case RECOVERY -> {
                int healed = healing(gameState, value);
                character.heal(healed);
                log.add("%s 사용: HP %d 회복".formatted(consumable.getName(), healed));
            }
            case ATTACK, GRANT -> {
                session.setTempAtkBonus(session.getTempAtkBonus() + value);
                log.add("%s 사용: 이번 전투 동안 공격력 +%d".formatted(consumable.getName(), value));
            }
            case DEFENCE -> {
                session.setTempDefBonus(session.getTempDefBonus() + value);
                log.add("%s 사용: 이번 전투 동안 방어력 +%d".formatted(consumable.getName(), value));
            }
            case DAMAGE -> {
                session.getMonsters().stream().filter(SessionMonster::isAlive).forEach(monster -> {
                    monster.damage(value);
                    log.add("%s 폭발! %s에게 %d의 피해".formatted(consumable.getName(), monster.getName(), value));
                    if (!monster.isAlive()) log.add("%s 처치!".formatted(monster.getName()));
                });
            }
            case RELEASE -> {
                session.getMonsters().forEach(monster -> monster.getBuffs().clear());
                log.add("%s 사용: 장 내의 모든 기운이 정화되었다".formatted(consumable.getName()));
            }
            case RESURRECTION -> {
                gameState.obtainPhoenix();
                gameStateRepository.save(gameState);
                log.add("%s이(가) 은은하게 빛난다. 죽음이 찾아와도 한 번은 되살아난다.".formatted(consumable.getName()));
            }
        }
    }

    /**
     * 조우 선택지 구성. 선택지 문구는 AI가 쓰고(순서: 천상, 마계, 인간), 결과는 서버가 세력의 성격에 맞춰 정한다.
     *
     * - 천상: 안전하다. 회복을 주지만 가끔 헌물(골드)을 요구한다
     * - 마계: 크게 주고 크게 가져간다. 유물·큰 골드 아니면 큰 피해
     * - 인간: 중간. 스스로 뒤져서 골드나 소모품을 챙기고, 가끔 다친다
     *
     * 결과는 선택 전에 미리 굴려 세션에 숨겨 둔다(클라이언트에는 문구와 세력만 노출).
     */
    private List<EncounterChoice> buildEncounterChoices(Encounter encounter, GameStateEntity gameState) {
        int stage = gameState.getCurrentStage();
        List<String> texts = encounter.selections() == null ? List.of() : encounter.selections();
        String heavenText = texts.size() > 0 ? texts.get(0) : "무릎을 꿇고 머리 위의 빛에 몸을 맡긴다";
        String abyssText = texts.size() > 1 ? texts.get(1) : "발밑의 속삭임이 내민 손을 잡는다";
        String humanText = texts.size() > 2 ? texts.get(2) : "어느 쪽에도 답하지 않고 스스로 길을 찾는다";

        List<EncounterChoice> choices = new ArrayList<>();

        // 천상
        double heavenRoll = random.nextDouble();
        if (heavenRoll < 0.55) choices.add(choice(Faction.HEAVEN, heavenText, "HEAL", 15, null));
        else if (heavenRoll < 0.80) choices.add(choice(Faction.HEAVEN, heavenText, "NOTHING", 0, null));
        else choices.add(choice(Faction.HEAVEN, heavenText, "GOLD_MINUS", 15 + stage * 8, null));

        // 마계 (v0.9.0: 유물 30 -> 40%, 골드 증가, 피해 감소. 마계만 고르면 클리어율이 2%까지 떨어졌다)
        double abyssRoll = random.nextDouble();
        List<RelicEntity> relicPool = abyssRoll < 0.40
                ? relicRepository.findByRelicNotOwner(gameState.getGameStateId()) : List.of();
        if (!relicPool.isEmpty()) {
            RelicEntity picked = relicPool.get(random.nextInt(relicPool.size()));
            choices.add(choice(Faction.ABYSS, abyssText, "RELIC", 0, picked.getRelicId()));
        } else if (abyssRoll < 0.70) {
            choices.add(choice(Faction.ABYSS, abyssText, "GOLD_PLUS", 60 + stage * 25, null));
        } else {
            choices.add(choice(Faction.ABYSS, abyssText, "DAMAGE", 8 + stage * 2, null));
        }

        // 인간
        double humanRoll = random.nextDouble();
        List<ConsumableEntity> consumablePool = humanRoll >= 0.40 && humanRoll < 0.70
                ? consumableRepository.findAll() : List.of();
        if (!consumablePool.isEmpty()) {
            ConsumableEntity picked = consumablePool.get(random.nextInt(consumablePool.size()));
            choices.add(choice(Faction.HUMAN, humanText, "CONSUMABLE", 0, picked.getConsumableId()));
        } else if (humanRoll < 0.70) {
            choices.add(choice(Faction.HUMAN, humanText, "GOLD_PLUS", 25 + stage * 12, null));
        } else if (humanRoll < 0.85) {
            choices.add(choice(Faction.HUMAN, humanText, "DAMAGE", 6 + stage * 2, null));
        } else {
            choices.add(choice(Faction.HUMAN, humanText, "NOTHING", 0, null));
        }

        return choices;
    }

    /**
     * 조우 선택지 하나를 만든다. 결과(effect, value, refId)는 서버만 알고 화면에는 문구만 보낸다.
     *
     * @param faction 세력
     * @param label   선택지 문구 (AI가 쓴 것)
     * @param effect  결과 종류
     * @param value   결과 수치
     * @param refId   얻을 아이템 ID (없으면 null)
     * @return 선택지
     */
    private EncounterChoice choice(Faction faction, String label, String effect, int value, Long refId) {
        EncounterChoice choice = new EncounterChoice();
        choice.setFaction(faction.name());
        choice.setLabel(label);
        choice.setEffect(effect);
        choice.setValue(value);
        choice.setRefId(refId);
        return choice;
    }

    /**
     * 조우 선택지의 결과를 적용한다 (회복, 피해, 골드, 유물, 소모품, 아무 일 없음).
     *
     * @param gameState 게임
     * @param character 캐릭터
     * @param choice    고른 선택지
     * @return 결과 문구
     */
    private String applyEncounterEffect(GameStateEntity gameState, GameCharacterEntity character, EncounterChoice choice) {
        switch (choice.getEffect()) {
            case "HEAL" -> {
                int amount = healing(gameState, character.getMaxHp() * choice.getValue() / 100);
                character.heal(amount);
                return "흰 빛이 상처 위에 내려앉는다. HP %d 회복.".formatted(amount);
            }
            case "DAMAGE" -> {
                character.takeDamage(choice.getValue());
                return "대가는 피였다. HP %d 피해를 입었다.".formatted(choice.getValue());
            }
            case "GOLD_PLUS" -> {
                gameState.gainGold(choice.getValue());
                return "손에 묵직한 금화가 쥐어졌다. 골드 +%d.".formatted(choice.getValue());
            }
            case "GOLD_MINUS" -> {
                int loss = Math.min(gameState.getGold(), choice.getValue());
                gameState.gainGold(-loss);
                return "헌물을 바치고 무사히 지나갔다. 골드 -%d.".formatted(loss);
            }
            case "CONSUMABLE" -> {
                addConsumable(gameState, choice.getRefId());
                String name = consumableRepository.findById(choice.getRefId()).map(ConsumableEntity::getName).orElse("무언가");
                return "잔해를 뒤져 [%s]을(를) 찾아냈다.".formatted(name);
            }
            case "RELIC" -> {
                addRelic(gameState, choice.getRefId());
                String name = relicRepository.findById(choice.getRefId()).map(RelicEntity::getName).orElse("무언가");
                return "계약이 성립했다. 유물 [%s]이(가) 손안에서 맥박친다.".formatted(name);
            }
            case "CONTRACT_SIGN" -> {
                return signContract(gameState, character, choice);
            }
            case "CONTRACT_REFUSE" -> {
                return "계약서를 찢지도, 서명하지도 않고 돌아섰다. 붉은 인장이 아쉬운 듯 식어 간다.";
            }
            default -> {
                return "아무 일도 일어나지 않았다. 다만 무언가가 당신을 지켜보고 있다.";
            }
        }
    }

    /**
     * 유물을 얻는다. 같은 유물은 두 번 가질 수 없다.
     *
     * @param gameState 게임
     * @param relicId   유물 ID
     * @throws BusinessException 이미 가진 유물이면
     */
    private void addRelic(GameStateEntity gameState, Long relicId) {
        if (gameState.ownsRelic(relicId)) throw new BusinessException(ErrorCode.RELIC_ALREADY_OWNED);
        RelicEntity relic = relicRepository.findById(relicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND, "유물을 찾을 수 없습니다."));
        // cascade로도 저장되지만, 같은 응답에 담을 칸 ID를 지금 받기 위해 바로 저장한다
        gameRelicInventoryRepository.save(gameState.addRelic(relic));
    }

    /**
     * 소모품을 얻는다.
     *
     * @param gameState    게임
     * @param consumableId 소모품 ID
     */
    private void addConsumable(GameStateEntity gameState, Long consumableId) {
        ConsumableEntity consumable = consumableRepository.findById(consumableId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND, "소모품을 찾을 수 없습니다."));
        // 소모품 칸 ID는 화면이 "사용" 요청에 실어 보내므로 응답 전에 받아 둬야 한다
        gameConsumableInventoryRepository.save(gameState.addConsumable(consumable));
    }

    /**
     * 원정을 끝낸다: 도감·랭킹 기록, 단계 전환, AI 결말 요약.
     *
     * @param gameState 게임
     * @param session   게임 세션
     * @param cleared   클리어했는지
     */
    private void terminate(GameStateEntity gameState, GameSession session, boolean cleared) {
        if (!cleared) leaveWraith(gameState, session);
        session.setWraithId(null);
        battleResultService.terminateGame(gameState, cleared);
        session.setPhase(cleared ? GameSession.PHASE_CLEAR : GameSession.PHASE_GAMEOVER);
        session.setSummaryNarrative(generateNarrative(gameState));
    }

    /**
     * 패배한 원정을 망령으로 남긴다. 실패해도 원정 종료는 막지 않는다.
     *
     * @param gameState 패배한 원정
     * @param session   게임 세션 (쓰러뜨린 적)
     */
    private void leaveWraith(GameStateEntity gameState, GameSession session) {
        try {
            String killer = session.getMonsters() == null ? null : session.getMonsters().stream()
                    .filter(SessionMonster::isAlive).map(SessionMonster::getName).findFirst().orElse(null);
            wraithService.recordDeath(gameState, killer, recentChoices(gameState, WRAITH_MEMORY_LINES));
        } catch (Exception e) {
            log.warn("망령 기록 실패 gameStateId={} 원인={}", gameState.getGameStateId(), e.getMessage());
        }
    }

    /**
     * 이 원정에서 내린 선택 중 최근 몇 줄 (AI 대화 기록의 "[플레이어의 선택]" 줄).
     *
     * @param gameState 게임
     * @param limit     최대 줄 수
     * @return 선택 문장, 오래된 것부터
     */
    private List<String> recentChoices(GameStateEntity gameState, int limit) {
        try {
            List<String> lines = chatMemory.get(conversationId(gameState)).stream()
                    .map(Message::getText)
                    .filter(text -> text != null && text.startsWith(CHOICE_PREFIX))
                    .map(text -> shorten(text.substring(CHOICE_PREFIX.length())))
                    .toList();
            return lines.subList(Math.max(0, lines.size() - limit), lines.size());
        } catch (Exception e) {
            log.warn("선택 기록 읽기 실패 gameStateId={} 원인={}", gameState.getGameStateId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * 연대기의 앞선 장. 읽지 못해도 회고는 만든다.
     *
     * @param gameState 게임
     * @return 앞선 장 (없거나 실패하면 비어 있음)
     */
    private List<String> previousChapters(GameStateEntity gameState) {
        try {
            return chronicleService.previousChapters(gameState.getMember().getMemberId());
        } catch (Exception e) {
            log.warn("연대기 앞 이야기 읽기 실패 gameStateId={} 원인={}", gameState.getGameStateId(), e.getMessage());
            return List.of();
        }
    }

    /**
     * 악마의 계약을 파기한다. 천상 성향이 충분할 때만, 골드를 바치고 탐험 중에만 할 수 있다.
     *
     * @param member 로그인 회원
     * @return 바뀐 화면 상태
     * @throws BusinessException 계약이 없거나, 탐험 중이 아니거나, 천상 성향이 모자라면
     */
    @Override
    @Transactional
    public GameViewDTO breakContract(MemberEntity member) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        requirePhase(session, GameSession.PHASE_EXPLORE, "탐험 중에만 계약을 파기할 수 있습니다.");
        gameState.breakContract();
        gameStateRepository.save(gameState);
        remember(gameState, "[플레이어의 선택] 천상의 빛에 금화를 바쳐 마계와 맺은 계약을 불살랐다.");
        session.setStory("흰 불꽃이 계약서를 삼킨다. 작은 글씨는 끝내 읽히지 않은 채 재가 되었다.");
        gameSessionService.save(session);
        return getView(member);
    }

    /**
     * 계약 선택지: 서명(마계)과 거절(인간). 혜택은 문구에 드러내고, 조항은 작은 글씨 힌트로만 보여 준다.
     *
     * @param gameState 게임
     * @return 선택지 두 개
     */
    private List<EncounterChoice> buildContractChoices(GameStateEntity gameState) {
        int stage = gameState.getCurrentStage();
        List<DemonContract.Boon> boons = new ArrayList<>(List.of(DemonContract.Boon.values()));
        List<RelicEntity> relicPool = relicRepository.findByRelicNotOwner(gameState.getGameStateId());
        if (relicPool.isEmpty()) boons.remove(DemonContract.Boon.RELIC);
        DemonContract.Boon boon = boons.get(random.nextInt(boons.size()));
        DemonContract.Clause[] clauses = DemonContract.Clause.values();
        DemonContract contract = DemonContract.of(boon, clauses[random.nextInt(clauses.length)]);

        String gift = switch (boon) {
            case GOLD -> "골드 +" + DemonContract.Boon.goldAmount(stage);
            case RELIC -> "유물 하나";
            default -> boon.summary();
        };
        EncounterChoice sign = choice(Faction.ABYSS, "서명한다 — 받는 것: %s · 작은 글씨: \"%s\""
                .formatted(gift, contract.clause().hint()), "CONTRACT_SIGN", 0,
                boon == DemonContract.Boon.RELIC ? relicPool.get(random.nextInt(relicPool.size())).getRelicId() : null);
        sign.setContract(contract.serialize());
        EncounterChoice refuse = choice(Faction.HUMAN, "서명하지 않고 돌아선다", "CONTRACT_REFUSE", 0, null);
        return new ArrayList<>(List.of(sign, refuse));
    }

    /**
     * 계약에 서명한다: 혜택을 지금 받고, 조항은 게임에 걸어 둔다.
     *
     * @param gameState 게임
     * @param character 캐릭터
     * @param choice    서명 선택지
     * @return 결과 문구
     */
    private String signContract(GameStateEntity gameState, GameCharacterEntity character, EncounterChoice choice) {
        DemonContract contract = DemonContract.parse(choice.getContract());
        if (contract == null) return "계약서의 글씨가 번져 읽을 수 없다. 아무 일도 일어나지 않았다.";
        String gift = switch (contract.boon()) {
            case GOLD -> {
                int gold = DemonContract.Boon.goldAmount(gameState.getCurrentStage());
                gameState.gainGold(gold);
                yield "골드 +" + gold;
            }
            case POWER -> {
                character.applyAttackDelta(4);
                yield "공격력 +4";
            }
            case VIGOR -> {
                character.applyMaxHpDelta(20);
                character.heal(20);
                yield "최대 HP +20";
            }
            case RELIC -> {
                if (choice.getRefId() == null || gameState.ownsRelic(choice.getRefId())) yield "유물은 이미 손에 있었다";
                addRelic(gameState, choice.getRefId());
                yield "유물 [%s]".formatted(relicRepository.findById(choice.getRefId()).map(RelicEntity::getName).orElse("?"));
            }
        };
        gameState.bindContract(contract);
        return "피로 서명했다. %s. 계약서는 품 안에서 따뜻하게 맥박친다.".formatted(gift);
    }

    /**
     * 노드를 하나 나아갈 때 빚 조항을 센다. 다 차면 골드를 가져간다.
     *
     * @param gameState 게임
     * @return 발동했으면 안내, 아니면 null
     */
    private String tickDebt(GameStateEntity gameState) {
        DemonContract contract = gameState.demonContract();
        if (contract == null || contract.clause() != DemonContract.Clause.GOLD_DEBT) return null;
        DemonContract next = contract.tick();
        if (!next.due()) {
            gameState.bindContract(next);
            return null;
        }
        int taken = (int) Math.round(gameState.getGold() * DemonContract.GOLD_DEBT_RATIO);
        gameState.gainGold(-taken);
        gameState.bindContract(null);
        return "계약서의 작은 글씨가 타오른다: 빚을 받으러 왔다. 골드 -%d. (계약 종료)".formatted(taken);
    }

    /**
     * 전투가 시작될 때 걸리는 조항: 그림자(적 공격력 +25%, 두 번), 피의 십일조(보스전에서 현재 HP 30%).
     *
     * @param gameState 게임
     * @param monsters  이번 전투의 적
     * @param nodeType  노드 종류
     * @return 발동했으면 안내, 아니면 null
     */
    private String applyContractToBattle(GameStateEntity gameState, List<SessionMonster> monsters, String nodeType) {
        DemonContract contract = gameState.demonContract();
        if (contract == null) return null;
        if (contract.clause() == DemonContract.Clause.SHADOW_PACT) {
            for (SessionMonster monster : monsters) {
                monster.setAtk(Math.max(1, (int) Math.round(monster.getAtk() * DemonContract.SHADOW_ATTACK_MULTIPLIER)));
            }
            DemonContract next = contract.tick();
            gameState.bindContract(next.due() ? null : next);
            return "계약서의 작은 글씨가 타오른다: 그림자가 적의 편에 섰다. 적 공격력 +25%%.%s"
                    .formatted(next.due() ? " (계약 종료)" : " (한 번 더 남았다)");
        }
        if (contract.clause() == DemonContract.Clause.BLOOD_TITHE && "BOSS".equals(nodeType)) {
            GameCharacterEntity character = character(gameState);
            int taken = Math.min(character.getCurrentHp() - 1, (int) Math.round(character.getCurrentHp() * DemonContract.BLOOD_TITHE_RATIO));
            character.takeDamage(Math.max(0, taken));
            gameState.bindContract(null);
            return "계약서의 작은 글씨가 타오른다: 왕좌 앞에서 피로 값을 치른다. HP -%d. (계약 종료)".formatted(Math.max(0, taken));
        }
        return null;
    }

    /**
     * 안내 두 줄을 합친다.
     *
     * @param a 앞 안내
     * @param b 뒤 안내
     * @return 합친 안내, 둘 다 없으면 null
     */
    private static String joinNotes(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return a + " / " + b;
    }

    /**
     * 망령전을 시작한다. 능력치는 이 스테이지·난이도의 강적 기준치로 서버가 정하고, 갈림길·서약도 그대로 적용한다.
     * AI는 등장 장면만 쓴다. 미리 만들어 둔 이 노드는 쓰지 않으므로 버리고, 다음 노드를 미리 만들기 시작한다.
     *
     * @param gameState      게임
     * @param session        게임 세션
     * @param wraith         망령
     * @param path           고른 길
     * @param conversationId 서사 대화 기록 ID
     * @return 전투 노드
     */
    private NodeDTO startWraithBattle(GameStateEntity gameState, GameSession session, WraithEntity wraith,
                                      PathChoice path, String conversationId) {
        int stage = gameState.getCurrentStage();
        int node = gameState.getCurrentNodeId().intValue();

        // 망령은 DB에 없는 몬스터라 엘리트(1) 기준치로 직접 만든다
        SessionMonster monster = newSessionMonster(0, null, WraithService.WRAITH_NAME, (short) 1,
                WraithService.SCORE_REWARD, null, baseStats(gameState));
        WraithService.dress(monster, wraith);
        List<SessionMonster> monsters = new ArrayList<>(List.of(monster));
        applyPath(monsters, path);
        applyPacts(monsters, pacts(gameState));
        applyKingdom(monsters, gameState.kingdomState());
        session.setContractNote(joinNotes(session.getContractNote(), applyContractToBattle(gameState, monsters, "WRAITH")));
        battleService.rollIntents(monsters);

        String story = wraithAppearance(wraith);
        remember(gameState, "[플레이어의 선택] 스테이지 %d에서 지난 원정에 쓰러진 자신의 망령과 마주쳤다.".formatted(stage));
        try {
            stageCacheService.delete(conversationId, stage, node);
            asyncPrefetchService.prefetchNextStage(stage, node + 1, conversationId);
        } catch (Exception e) {
            log.warn("망령 노드의 프리페치 처리 실패: {}", e.getMessage());
        }

        session.setPhase(GameSession.PHASE_BATTLE);
        session.setPath(path.name());
        session.setPathNote(path.battleNote());
        session.setNodeType("WRAITH");
        session.setStory(story);
        session.setWraithId(wraith.getWraithId());
        session.setMonsters(monsters);
        session.setChoices(new ArrayList<>());
        gameSessionService.save(session);
        log.info("망령 출현 gameStateId={} wraithId={} stage={} node={}",
                gameState.getGameStateId(), wraith.getWraithId(), stage, node);
        return battleNode(session);
    }

    /**
     * 망령의 등장 장면. 시간 상한을 넘기면 정해진 문장을 쓴다.
     *
     * @param wraith 망령
     * @return 장면
     */
    private String wraithAppearance(WraithEntity wraith) {
        Optional<String> text = aiCallRunner.call(() -> wraithVoiceService.appearance(wraith),
                WRAITH_VOICE_BUDGET_SECONDS, "망령 장면");
        if (text.isPresent() && !text.get().isBlank()) return text.get();
        return "안개 속에서 낯익은 걸음걸이가 다가온다. 지난 원정에서 이곳에 쓰러진 당신이다.";
    }

    /**
     * AI로 원정 결말 요약을 만든다. 실패하면 기본 문장을 쓴다.
     *
     * @param gameState 게임
     * @return 결말 요약
     */
    private String generateNarrative(GameStateEntity gameState) {
        try {
            return monsterRagService.genarateGameOverStory(conversationId(gameState));
        } catch (Exception e) {
            log.warn("AI 서사 요약 실패: {}", e.getMessage());
            return "천상과 마계 사이, 폐허가 된 왕궁을 헤매던 한 인간의 이야기는 여기서 막을 내렸다.";
        }
    }

    /**
     * 원정 요약을 만든다. 결말 요약이 아직 없으면 이때 만들어 세션에 둔다.
     *
     * @param gameState 게임
     * @param session   게임 세션
     * @return 요약
     */
    private GameSummaryDTO buildSummary(GameStateEntity gameState, GameSession session) {
        boolean cleared = GameSession.PHASE_CLEAR.equals(session.getPhase());
        int level = gameState.getGameCharacter() == null ? 1
                : gameState.getGameCharacter().stream().findFirst().map(GameCharacterEntity::getLevel).orElse(1);
        String narrative = session.getSummaryNarrative();
        if (narrative == null || narrative.isBlank()) {
            narrative = generateNarrative(gameState);
            session.setSummaryNarrative(narrative);
            gameSessionService.save(session);
        }
        return new GameSummaryDTO(cleared, gameState.getScore(), gameState.getGold(),
                gameState.getCurrentStage(),
                gameState.getCurrentNodeId() == null ? 0 : gameState.getCurrentNodeId(),
                level, narrative, gameMapper.toRunStatsDTO(session.getStats() == null ? new RunStats() : session.getStats()));
    }

    /**
     * 세션의 원정 통계. 통계가 생기기 전에 만든 세션이면 새로 붙인다.
     *
     * @param session 게임 세션
     * @return 원정 통계
     */
    private RunStats stats(GameSession session) {
        if (session.getStats() == null) session.setStats(new RunStats());
        return session.getStats();
    }

    /* ==================== DTO 변환 ==================== */

    /**
     * 세션의 몬스터를 화면용으로 옮긴다 (예고 행동, 격노 포함).
     *
     * @param monsters 몬스터
     * @return 화면용 몬스터
     */
    private List<BattleMonsterDTO> toBattleMonsterDTOs(List<SessionMonster> monsters) {
        if (monsters == null) return List.of();
        return gameMapper.toBattleMonsterDTOs(monsters);
    }

    /* 전투 한 턴의 응답. 결과 코드와 전리품·클리어 여부의 조합을 여기서만 정한다 */

    private TurnResultDTO ongoingResult(List<String> log, List<BattleMonsterDTO> monsters, StatusDTO status, String phase) {
        return new TurnResultDTO("CONTINUE", log, monsters, status, null, false, phase);
    }

    private TurnResultDTO wonResult(List<String> log, List<BattleMonsterDTO> monsters, StatusDTO status,
                                    RewardViewDTO reward, String phase) {
        return new TurnResultDTO("WIN", log, monsters, status, reward, false, phase);
    }

    private TurnResultDTO clearedResult(List<String> log, List<BattleMonsterDTO> monsters, StatusDTO status, String phase) {
        return new TurnResultDTO("WIN", log, monsters, status, null, true, phase);
    }

    private TurnResultDTO lostResult(List<String> log, List<BattleMonsterDTO> monsters, StatusDTO status, String phase) {
        return new TurnResultDTO("LOSE", log, monsters, status, null, false, phase);
    }

    private TurnResultDTO fledResult(List<String> log, StatusDTO status, String phase) {
        return new TurnResultDTO("FLED", log, List.of(), status, null, false, phase);
    }

    /**
     * 세션의 전투 노드를 응답으로 만든다 (고른 길과 그 효과 안내 포함).
     *
     * @param session 게임 세션
     * @return 노드
     */
    private NodeDTO battleNode(GameSession session) {
        return new NodeDTO(session.getNodeType(), session.getStory(), toBattleMonsterDTOs(session.getMonsters()),
                null, session.getPath(), session.getPathNote(), session.getContractNote());
    }

    /** 조우에는 길 효과가 없으므로 path는 비운다. 고른 길이 무시됐다면 그 안내만 싣는다 */
    private NodeDTO encounterNode(GameSession session, String encounterText) {
        String type = "CONTRACT".equals(session.getNodeType()) ? "CONTRACT" : "ENCOUNTER";
        return new NodeDTO(type, session.getStory(), null,
                new EncounterDTO(encounterText, choiceLabels(session), choiceFactions(session)),
                null, session.getPathNote(), session.getContractNote());
    }

    /**
     * 조우 선택지의 세력 목록 (화면 색과 말풍선용).
     *
     * @param session 게임 세션
     * @return 세력 이름 목록
     */
    private List<String> choiceFactions(GameSession session) {
        return session.getChoices().stream()
                .map(choice -> choice.getFaction() == null ? "" : choice.getFaction())
                .toList();
    }

    /**
     * 조우 선택지의 문구 목록.
     *
     * @param session 게임 세션
     * @return 문구 목록
     */
    private List<String> choiceLabels(GameSession session) {
        return session.getChoices().stream().map(EncounterChoice::getLabel).toList();
    }

    /**
     * 세션의 보상 후보 ID를 화면용 정보로 바꾼다.
     *
     * @param options 보상 후보
     * @return 화면용 보상, 없으면 null
     */
    private RewardViewDTO toRewardView(RewardOptions options) {
        if (options == null) return null;
        List<SkillInfoDTO> skills = catalogMapper.toSkillInfoDTOs(skillRepository.findAllById(options.getSkillIds()));
        List<RelicDTO> relics = catalogMapper.toRelicDTOs(relicRepository.findAllById(options.getRelicIds()));
        List<ConsumableDTO> consumables = catalogMapper.toConsumableDTOs(consumableRepository.findAllById(options.getConsumableIds()));
        return new RewardViewDTO(options.getExp(), options.getGold(), skills, relics, consumables);
    }

    /**
     * 세션의 진열품을 화면용으로 옮긴다. 장비에는 지금 착용 중인 같은 부위 장비의 보너스를 붙인다.
     *
     * @param stock     진열품
     * @param gameState 게임 (착용 장비 비교용)
     * @return 화면용 진열품
     */
    private List<ShopItemDTO> toShopItems(List<ShopStockItem> stock, GameStateEntity gameState) {
        if (stock == null) return List.of();
        GameEquipmentEntity equipped = gameState.getGameEquipment();
        return stock.stream()
                .map(item -> gameMapper.toShopItemDTO(item, currentBonus(item.getItemType(), equipped)))
                .toList();
    }

    /**
     * 착용 중인 같은 부위 장비의 보너스. 장비가 아닌 진열품이면 null, 그 부위가 비어 있으면 0.
     *
     * @param itemType 진열품 종류
     * @param equipped 장비 슬롯 (없을 수 있음)
     * @return 보너스
     */
    private Integer currentBonus(String itemType, GameEquipmentEntity equipped) {
        return switch (itemType) {
            case "WEAPON" -> equipped == null || equipped.getWeapon() == null ? 0 : equipped.getWeapon().getAtkBonus();
            case "ARMOR" -> equipped == null || equipped.getArmor() == null ? 0 : equipped.getArmor().getDefBonus();
            case "HELMET" -> equipped == null || equipped.getHelmet() == null ? 0 : equipped.getHelmet().getHpBonus();
            default -> null;
        };
    }
}
