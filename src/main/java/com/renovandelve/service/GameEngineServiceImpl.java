package com.renovandelve.service;

import com.renovandelve.config.RecordMapper;
import com.renovandelve.domain.*;
import com.renovandelve.domain.consumableentity.ConsumableEntity;
import com.renovandelve.domain.gamestateentity.Difficulty;
import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.domain.relicentity.RelicEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.domain.skillentity.Target;
import com.renovandelve.domain.weaponentity.Type;
import com.renovandelve.dto.*;
import com.renovandelve.dto.request.*;
import com.renovandelve.dto.response.*;
import com.renovandelve.engine.*;
import com.renovandelve.repository.*;
import com.renovandelve.service.ai.MonsterRagService;
import com.renovandelve.service.ai.RunReviewService;
import com.renovandelve.service.redis.GameSessionService;
import com.renovandelve.service.redis.StageCacheService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Log4j2
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
    private final RunReviewService runReviewService;
    private final ChatMemory chatMemory;
    private final StageCacheService stageCacheService;
    private final SkillSlotService skillSlotService;

    private final Random random = new Random();

    private static final int FINAL_STAGE = 5;
    private static final int NODES_PER_STAGE = 10;
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
    /** LLM 호출은 대기 시간이 대부분인 블로킹 IO -> 가상 스레드 */
    private final ExecutorService aiExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /* ==================== 조회 ==================== */

    @Override
    @Transactional
    public GameViewDTO getView(MemberEntity member) {
        Optional<GameStateEntity> active = gameStateRepository.findByMember_MemberIdAndIsActiveTrue(member.getMemberId());
        if (active.isEmpty()) {
            return new GameViewDTO("NONE", null, null, null, null, null, null);
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
            case GameSession.PHASE_SHOP -> shop = toShopItems(session.getShop());
            case GameSession.PHASE_GAMEOVER, GameSession.PHASE_CLEAR -> summary = buildSummary(gameState, session);
            default -> { /* EXPLORE */ }
        }

        Integer rerollCost = GameSession.PHASE_SHOP.equals(session.getPhase()) ? rerollCost(gameState, session) : null;
        return new GameViewDTO(session.getPhase(), statusAssembler.toStatusDTO(gameState), node, reward, shop, summary,
                rerollCost);
    }

    /* ==================== 노드 진행 ==================== */

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
        gameStateRepository.save(gameState);

        int stage = gameState.getCurrentStage();
        int node = gameState.getCurrentNodeId() == null ? 1 : gameState.getCurrentNodeId().intValue();
        String conversationId = conversationId(gameState);

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
            battleService.rollIntents(monsters); // 첫 턴 행동 예고
            String nodeType = resolveNodeType(node, monsters);

            session.setPhase(GameSession.PHASE_BATTLE);
            session.setPath(path.name());
            session.setPathNote(path.battleNote());
            session.setNodeType(nodeType);
            session.setStory(ai.story());
            session.setMonsters(monsters);
            session.setChoices(new ArrayList<>());
            gameSessionService.save(session);

            return battleNode(session);
        }

        // 인카운터
        Encounter encounter = ai.encounter() != null ? ai.encounter()
                : new Encounter("빛과 그림자가 동시에 당신을 부른다.", List.of());
        List<EncounterChoice> choices = buildEncounterChoices(encounter, gameState);

        session.setPhase(GameSession.PHASE_ENCOUNTER);
        session.setPath(PathChoice.SAFE.name()); // 조우 이벤트에는 길 선택이 영향을 주지 않는다
        // 고른 길이 무시된 것을 숨기지 않는다 (효과가 적용되지 않은 것처럼 보인다는 플레이 피드백)
        session.setPathNote(path == PathChoice.SAFE ? null
                : "%s을 골랐지만 싸움이 벌어지지 않았다. 조우에서는 길의 효과가 적용되지 않는다.".formatted(path.title()));
        session.setNodeType("ENCOUNTER");
        session.setStory(ai.story() != null ? ai.story() : encounter.text());
        session.setMonsters(new ArrayList<>());
        session.setChoices(choices);
        gameSessionService.save(session);

        return encounterNode(session, encounter.text());
    }

    /* ==================== 전투 ==================== */

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
                .orElseThrow(() -> new IllegalArgumentException("보유하지 않은 스킬 슬롯입니다. slotId=" + request.slotId()));

        if (slot.getCoolTime() > 0) {
            throw new IllegalStateException("쿨타임이 남아 있습니다. (" + slot.getCoolTime() + "턴)");
        }

        SkillEntity skill = slot.getSkill();
        RelicEffects effects = relicEffects(gameState);
        SkillSynergies synergies = skillSynergies(gameState);
        List<String> log = new ArrayList<>();
        log.add("[%s] 사용!".formatted(skill.getName()));

        int attack = (int) Math.round((character.getAttack() + session.getTempAtkBonus())
                * effects.getAttackMultiplier() * gameState.alignment().attackMultiplier());
        double critChance = BASE_CRIT_CHANCE + effects.getCritBonus() + daggerCritBonus(gameState);

        List<SessionMonster> targets = resolveTargets(session, skill, request.targetKeys());

        // 1) 플레이어 공격
        battleService.applyPlayerAttack(skill, attack, critChance,
                effects.getSkillDamageMultiplier() * synergies.skillDamageMultiplier(skill),
                effects.getBleedMultiplier() + synergies.getBleedMultiplierBonus(),
                effects.getCritBleedStacks(), synergies, targets, log);

        if (battleService.isAllMonsterDead(session.getMonsters())) {
            return processVictory(gameState, session, character, effects, log);
        }

        // 2) 상태이상 부여 + 쿨타임 갱신 + 도트 피해
        battleService.applyStatusEffect(skill, synergies.extraStacks(skill.getStatusEffect()), targets, log);
        battleService.updateCoolTimes(slots, slot.getSlotId(), effects.getCooldownReduction());
        battleService.applyDotDamage(session.getMonsters(), log);

        if (battleService.isAllMonsterDead(session.getMonsters())) {
            return processVictory(gameState, session, character, effects, log);
        }

        // 3) 몬스터 반격
        return processMonsterCounterattack(gameState, session, character, effects, 1.0, log);
    }

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
        if (slots.stream().anyMatch(slot -> slot.getCoolTime() <= 0)) {
            throw new IllegalStateException("쓸 수 있는 스킬이 남아 있습니다. 숨 고르기는 모든 스킬이 쿨타임일 때만 할 수 있습니다.");
        }

        GameCharacterEntity character = character(gameState);
        RelicEffects effects = relicEffects(gameState);
        List<String> log = new ArrayList<>();
        log.add("숨을 고르며 다음 기회를 노린다. (모든 스킬 쿨타임 -%d)".formatted(PASS_COOLDOWN_TICKS));

        for (int i = 0; i < PASS_COOLDOWN_TICKS; i++) {
            battleService.updateCoolTimes(slots, null, effects.getCooldownReduction());
        }
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
            throw new IllegalStateException("이 구역의 지배자에게서는 달아날 수 없습니다.");
        }

        GameCharacterEntity character = character(gameState);
        RelicEffects effects = relicEffects(gameState);
        List<String> log = new ArrayList<>();
        log.add("등을 돌려 달아난다!");

        TurnResultDTO parting = processMonsterCounterattack(gameState, session, character, effects, 1.0, log);
        if ("LOSE".equals(parting.result())) {
            return parting; // 달아나다 쓰러졌다
        }

        log.add("적을 따돌렸다. 전리품은 없다.");
        remember(gameState, "[플레이어의 선택] 스테이지 %d에서 %s을(를) 상대하다 싸움을 포기하고 달아났다."
                .formatted(gameState.getCurrentStage(), session.getMonsters().stream()
                        .map(SessionMonster::getName).findFirst().orElse("적")));

        gameState.getGameSkillSlot().forEach(slot -> slot.setCoolTime(0));
        session.setTempAtkBonus(0);
        session.setTempDefBonus(0);
        session.setMonsters(new ArrayList<>());
        session.setPhase(GameSession.PHASE_EXPLORE);
        gameSessionService.save(session);
        gameStateRepository.save(gameState);

        return new TurnResultDTO("FLED", log, List.of(), statusAssembler.toStatusDTO(gameState),
                null, false, session.getPhase());
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
        if (GameSession.PHASE_BATTLE.equals(phase) || GameSession.PHASE_ENCOUNTER.equals(phase)) {
            throw new IllegalStateException("지금은 스킬을 버릴 수 없습니다. (현재 단계: " + phase + ")");
        }
        skillSlotService.discard(gameState, request.slotId());
        return statusAssembler.toStatusDTO(gameState);
    }

    @Override
    @Transactional
    public TurnResultDTO useConsumable(MemberEntity member, ConsumableUseRequest request) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());

        boolean inBattle = GameSession.PHASE_BATTLE.equals(session.getPhase());
        if (!inBattle && !GameSession.PHASE_EXPLORE.equals(session.getPhase())) {
            throw new IllegalStateException("지금은 소모품을 사용할 수 없습니다.");
        }

        GameConsumableInventoryEntity inventory = gameState.getGameConsumableInventory().stream()
                .filter(i -> i.getSlotId().equals(request.inventorySlotId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("보유하지 않은 소모품입니다."));

        GameCharacterEntity character = character(gameState);
        ConsumableEntity consumable = inventory.getConsumable();
        List<String> log = new ArrayList<>();

        applyConsumableEffect(gameState, session, character, consumable, log);
        gameConsumableInventoryRepository.delete(inventory);

        if (!inBattle) {
            gameSessionService.save(session);
            return new TurnResultDTO("CONTINUE", log, toBattleMonsterDTOs(session.getMonsters()),
                    statusAssembler.toStatusDTO(gameState), null, false, session.getPhase());
        }

        // 전투 중 소모품 사용은 턴을 소모한다
        RelicEffects effects = relicEffects(gameState);
        if (battleService.isAllMonsterDead(session.getMonsters())) {
            return processVictory(gameState, session, character, effects, log);
        }
        return processMonsterCounterattack(gameState, session, character, effects, 1.0, log);
    }

    /* ==================== 인카운터 ==================== */

    @Override
    @Transactional
    public EncounterOutcomeDTO chooseEncounter(MemberEntity member, EncounterChoiceRequest request) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        requirePhase(session, GameSession.PHASE_ENCOUNTER, "인카운터 진행 중이 아닙니다.");

        List<EncounterChoice> choices = session.getChoices();
        if (request.choiceIndex() < 0 || request.choiceIndex() >= choices.size()) {
            throw new IllegalArgumentException("잘못된 선택지입니다.");
        }

        EncounterChoice choice = choices.get(request.choiceIndex());
        GameCharacterEntity character = character(gameState);
        String outcome = applyEncounterEffect(gameState, character, choice);

        // 선택이 성향으로 쌓이고, AI 대화 기록에 남아 다음 장면과 결말이 이 선택을 이어받는다
        Faction faction = Faction.parse(choice.getFaction());
        gameState.gainAffinity(faction);
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
            default -> throw new IllegalArgumentException("알 수 없는 보상 타입: " + type);
        }

        // 상점 오픈
        session.setReward(null);
        session.setPhase(GameSession.PHASE_SHOP);
        session.setShop(shopService.generateStock(gameState));
        session.setShopRerolls(0);
        gameSessionService.save(session);

        return statusAssembler.toStatusDTO(gameState);
    }

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
        return toShopItems(session.getShop());
    }

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
                .orElseThrow(() -> new IllegalArgumentException("상점에 없는 아이템이거나 이미 판매된 상품입니다."));

        shopService.applyPurchase(gameState, member, stockItem, request.replaceSlotId());
        stockItem.setSold(true);
        gameSessionService.save(session);

        return statusAssembler.toStatusDTO(gameState);
    }

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

    @Override
    @Transactional
    public RunReviewDTO getRunReview(MemberEntity member) {
        GameStateEntity gameState = gameStateRepository
                .findTopByMember_MemberIdOrderByCreatedAtDesc(member.getMemberId())
                .orElseThrow(() -> new NoSuchElementException("게임 기록이 없습니다."));

        // 마지막 전투 정보가 세션에 있어야 회고할 수 있다 (세션 TTL 24시간이 지나면 회고는 제공하지 않는다)
        GameSession session = gameSessionService.get(gameState.getGameStateId());
        if (session == null) return null;

        boolean cleared = GameSession.PHASE_CLEAR.equals(session.getPhase());
        if (!cleared && !GameSession.PHASE_GAMEOVER.equals(session.getPhase())) return null;

        if (session.getReview() != null || session.isReviewAttempted()) {
            return session.getReview();
        }

        RunReviewDTO review = runReviewService.review(gameState, session, cleared);
        session.setReview(review);
        session.setReviewAttempted(true);
        gameSessionService.save(session);
        return review;
    }

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
            default -> throw new IllegalArgumentException("알 수 없는 선택입니다: " + choiceValue);
        }

        gameStateRepository.save(gameState);
        session.setStory(story);
        session.setPhase(GameSession.PHASE_EXPLORE);
        gameSessionService.save(session);
        return getView(member);
    }

    /* ==================== 종료 요약 ==================== */

    @Override
    @Transactional
    public GameSummaryDTO getSummary(MemberEntity member) {
        GameStateEntity gameState = gameStateRepository
                .findTopByMember_MemberIdOrderByCreatedAtDesc(member.getMemberId())
                .orElseThrow(() -> new NoSuchElementException("게임 기록이 없습니다."));
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        return buildSummary(gameState, session);
    }

    /* ==================== 내부 로직 ==================== */

    private GameStateEntity activeGame(MemberEntity member) {
        return gameStateRepository.findByMember_MemberIdAndIsActiveTrue(member.getMemberId())
                .orElseThrow(() -> new NoSuchElementException("진행 중인 게임이 없습니다."));
    }

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

    private String shorten(String text) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "...";
    }

    private String conversationId(GameStateEntity gameState) {
        return "game-" + gameState.getGameStateId();
    }

    private void requirePhase(GameSession session, String expected, String message) {
        if (!expected.equals(session.getPhase())) {
            throw new IllegalStateException(message + " (현재 단계: " + session.getPhase() + ")");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private RelicEffects relicEffects(GameStateEntity gameState) {
        List<String> names = gameState.getGameRelicInventory() == null ? List.of()
                : gameState.getGameRelicInventory().stream()
                    .map(inventory -> inventory.getRelic().getName())
                    .toList();
        return RelicEffects.of(names);
    }

    private SkillSynergies skillSynergies(GameStateEntity gameState) {
        if (gameState.getGameSkillSlot() == null) return SkillSynergies.NONE;
        return SkillSynergies.of(gameState.getGameSkillSlot().stream()
                .map(GameSkillSlotEntity::getSkill)
                .filter(Objects::nonNull)
                .toList());
    }

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
        Future<AsyncStageResponseDTO> pending = aiExecutor.submit(
                () -> monsterRagService.chat(stage, node, conversationId));
        try {
            return pending.get(aiNodeBudgetSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            pending.cancel(true); // 시간 초과면 진행 중인 LLM 호출을 끊는다
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("AI 노드 생성 실패, 폴백 사용. stage={} node={} 원인={}", stage, node,
                    e instanceof TimeoutException ? aiNodeBudgetSeconds + "초 초과" : e.getMessage());
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
    }

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
        int stage = Math.min(FINAL_STAGE, Math.max(1, gameState.getCurrentStage()));
        short difficultyIndex = (short) gameState.getDifficulty().ordinal();

        MonsterBaseStatusEntity base = monsterBaseStatusRepository
                .findByStageAndDifficulty((short) stage, difficultyIndex)
                .orElse(null);

        int baseHp = base != null ? base.getBaseHp() : (int) ((40 + stage * 25) * difficultyFactor(gameState.getDifficulty()));
        int baseAtk = base != null ? base.getBaseAtk() : (int) ((7 + stage * 4) * difficultyFactor(gameState.getDifficulty()));
        int baseDef = base != null ? base.getBaseDef() : (int) ((1 + stage * 2) * difficultyFactor(gameState.getDifficulty()));

        List<SessionMonster> monsters = new ArrayList<>();
        int key = 0;
        for (MonsterDTO dto : limitElites(monsterDTOs, stage)) {
            double hpMult = switch (dto.monsterType()) { case 2 -> 3.2; case 1 -> 1.6; default -> 1.0; };
            double atkMult = switch (dto.monsterType()) { case 2 -> 1.7; case 1 -> 1.25; default -> 1.0; };
            double defMult = switch (dto.monsterType()) { case 2 -> 1.5; case 1 -> 1.2; default -> 1.0; };
            double variance = 0.9 + random.nextDouble() * 0.2; // +-10%

            SessionMonster monster = new SessionMonster();
            monster.setKey(key++);
            monster.setMonsterId(dto.monsterId());
            monster.setName(dto.name());
            monster.setMonsterType(dto.monsterType());
            int hp = Math.max(1, (int) (baseHp * hpMult * variance));
            monster.setMaxHp(hp);
            monster.setCurrentHp(hp);
            monster.setAtk(Math.max(1, (int) (baseAtk * atkMult)));
            monster.setDef(Math.max(0, (int) (baseDef * defMult)));
            monster.setScoreReward(dto.scoreReward());
            monster.setDescription(dto.description());
            monsters.add(monster);
        }
        return monsters;
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

    private double difficultyFactor(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 0.8;
            case NORMAL -> 1.0;
            case HARD -> 1.3;
            case HELL -> 1.8;
        };
    }

    private double rewardMultiplier(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 0.7;
            case NORMAL -> 1.0;
            case HARD -> 1.3;
            case HELL -> 2.0;
        };
    }

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
        // 이번 턴 플레이어의 공격으로 보스 HP가 절반 아래로 떨어졌다면 격노
        battleService.applyEnrage(session.getMonsters(), log);

        int defense = character.getDefense() + session.getTempDefBonus() + effects.getDefenseBonus();
        int damage = battleService.monsterAttackDamage(session.getMonsters(), defense,
                effects.getFlatDamageReduction(),
                incomingMultiplier * gameState.alignment().damageTakenMultiplier(), log);
        character.takeDamage(damage);

        // 다음 턴 행동 예고 (플레이어는 이것을 보고 공격/방어/대상을 정한다)
        battleService.rollIntents(session.getMonsters());

        if (character.getCurrentHp() <= 0) {
            if (tryRevive(gameState, character, log)) {
                gameSessionService.save(session);
                return new TurnResultDTO("CONTINUE", log, toBattleMonsterDTOs(session.getMonsters()),
                        statusAssembler.toStatusDTO(gameState), null, false, session.getPhase());
            }
            // 패배 처리
            terminate(gameState, session, false);
            log.add("당신은 쓰러졌다... 모험이 여기서 끝났다.");
            gameSessionService.save(session);
            return new TurnResultDTO("LOSE", log, toBattleMonsterDTOs(session.getMonsters()),
                    statusAssembler.toStatusDTO(gameState), null, false, session.getPhase());
        }

        gameSessionService.save(session);
        return new TurnResultDTO("CONTINUE", log, toBattleMonsterDTOs(session.getMonsters()),
                statusAssembler.toStatusDTO(gameState), null, false, session.getPhase());
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
        Optional<GameConsumableInventoryEntity> phoenix = gameState.getGameConsumableInventory().stream()
                .filter(i -> i.getConsumable().getEffectType() == com.renovandelve.domain.consumableentity.Type.RESURRECTION)
                .findFirst();
        if (phoenix.isPresent()) {
            gameConsumableInventoryRepository.delete(phoenix.get());
            character.revive();
            log.add("불사조의 깃털이 타오르며 되살아났다! (HP 50% 회복)");
            return true;
        }
        return false;
    }

    private TurnResultDTO processVictory(GameStateEntity gameState, GameSession session,
                                         GameCharacterEntity character, RelicEffects effects, List<String> log) {
        int stage = gameState.getCurrentStage();
        int node = gameState.getCurrentNodeId() == null ? 1 : gameState.getCurrentNodeId().intValue();
        PathChoice path = PathChoice.parse(session.getPath());
        double multiplier = rewardMultiplier(gameState.getDifficulty()) * path.rewardMultiplier();

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

        gameState.gainScore(totalScore);
        gameState.gainGold(totalGold);

        int levelUps = character.gainExp(totalExp);
        log.add("승리! 점수 +%d, 골드 +%d, 경험치 +%d".formatted(totalScore, totalGold, totalExp));
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

        // 쿨타임 초기화
        gameState.getGameSkillSlot().forEach(slot -> slot.setCoolTime(0));

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
            return new TurnResultDTO("WIN", log, toBattleMonsterDTOs(session.getMonsters()),
                    statusAssembler.toStatusDTO(gameState), null, true, session.getPhase());
        }

        // 보상 후보 생성 -> REWARD 단계
        // 강한 적일수록 유물을 고를 기회가 많다: 일반 1 / 엘리트 2 / 보스 3 (+ 위험한 길 보너스)
        int relicOptions = switch (String.valueOf(session.getNodeType())) {
            case "BOSS" -> 3;
            case "ELITE" -> 2;
            default -> 1;
        } + path.bonusRelicOptions();
        session.setReward(buildRewardOptions(gameState, totalExp, totalGold, relicOptions));
        session.setPathNote(null);
        session.setPhase(GameSession.PHASE_REWARD);
        gameSessionService.save(session);
        gameStateRepository.save(gameState);

        return new TurnResultDTO("WIN", log, toBattleMonsterDTOs(session.getMonsters()),
                statusAssembler.toStatusDTO(gameState), toRewardView(session.getReward()), false, session.getPhase());
    }

    private RewardOptions buildRewardOptions(GameStateEntity gameState, int exp, int gold, int relicOptions) {
        RewardOptions options = new RewardOptions();
        options.setExp(exp);
        options.setGold(gold);

        List<SkillEntity> skills = new ArrayList<>(skillRepository.findBySkillNotOwner(gameState.getGameStateId()));
        Collections.shuffle(skills, random);
        // 가호 3단계에 이르면 그 세력이 보상 후보를 하나 더 얹어 준다 (천상: 소모품 / 마계: 유물 / 인간: 스킬)
        Alignment alignment = gameState.alignment();
        options.setSkillIds(skills.stream().limit(3 + alignment.bonusSkillOptions())
                .map(SkillEntity::getSkillId).collect(Collectors.toList()));

        List<RelicEntity> relics = new ArrayList<>(relicRepository.findByRelicNotOwner(gameState.getGameStateId()));
        Collections.shuffle(relics, random);
        options.setRelicIds(relics.stream().limit(relicOptions + alignment.bonusRelicOptions())
                .map(RelicEntity::getRelicId).collect(Collectors.toList()));

        List<ConsumableEntity> consumables = new ArrayList<>(consumableRepository.findAll());
        Collections.shuffle(consumables, random);
        options.setConsumableIds(consumables.stream().limit(2 + alignment.bonusConsumableOptions())
                .map(ConsumableEntity::getConsumableId).collect(Collectors.toList()));

        return options;
    }

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

        // 마계
        double abyssRoll = random.nextDouble();
        List<RelicEntity> relicPool = abyssRoll < 0.30
                ? relicRepository.findByRelicNotOwner(gameState.getGameStateId()) : List.of();
        if (!relicPool.isEmpty()) {
            RelicEntity picked = relicPool.get(random.nextInt(relicPool.size()));
            choices.add(choice(Faction.ABYSS, abyssText, "RELIC", 0, picked.getRelicId()));
        } else if (abyssRoll < 0.58) {
            choices.add(choice(Faction.ABYSS, abyssText, "GOLD_PLUS", 50 + stage * 20, null));
        } else {
            choices.add(choice(Faction.ABYSS, abyssText, "DAMAGE", 14 + stage * 4, null));
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

    private EncounterChoice choice(Faction faction, String label, String effect, int value, Long refId) {
        EncounterChoice choice = new EncounterChoice();
        choice.setFaction(faction.name());
        choice.setLabel(label);
        choice.setEffect(effect);
        choice.setValue(value);
        choice.setRefId(refId);
        return choice;
    }

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
            default -> {
                return "아무 일도 일어나지 않았다. 다만 무언가가 당신을 지켜보고 있다.";
            }
        }
    }

    private void addRelic(GameStateEntity gameState, Long relicId) {
        boolean owned = gameState.getGameRelicInventory().stream()
                .anyMatch(inventory -> inventory.getRelic().getRelicId().equals(relicId));
        if (owned) throw new IllegalStateException("이미 보유 중인 유물입니다.");
        RelicEntity relic = relicRepository.findById(relicId)
                .orElseThrow(() -> new NoSuchElementException("유물을 찾을 수 없습니다."));
        gameRelicInventoryRepository.save(GameRelicInventoryEntity.builder()
                .gameState(gameState).relic(relic).build());
    }

    private void addConsumable(GameStateEntity gameState, Long consumableId) {
        ConsumableEntity consumable = consumableRepository.findById(consumableId)
                .orElseThrow(() -> new NoSuchElementException("소모품을 찾을 수 없습니다."));
        gameConsumableInventoryRepository.save(GameConsumableInventoryEntity.builder()
                .gameState(gameState).consumable(consumable).build());
    }

    private void terminate(GameStateEntity gameState, GameSession session, boolean cleared) {
        battleResultService.terminateGame(gameState, cleared);
        session.setPhase(cleared ? GameSession.PHASE_CLEAR : GameSession.PHASE_GAMEOVER);
        session.setSummaryNarrative(generateNarrative(gameState));
    }

    private String generateNarrative(GameStateEntity gameState) {
        try {
            return monsterRagService.genarateGameOverStory(conversationId(gameState));
        } catch (Exception e) {
            log.warn("AI 서사 요약 실패: {}", e.getMessage());
            return "천상과 마계 사이, 폐허가 된 왕궁을 헤매던 한 인간의 이야기는 여기서 막을 내렸다.";
        }
    }

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
                level, narrative);
    }

    /* ==================== DTO 변환 ==================== */

    private List<BattleMonsterDTO> toBattleMonsterDTOs(List<SessionMonster> monsters) {
        if (monsters == null) return List.of();
        return monsters.stream()
                .map(m -> new BattleMonsterDTO(m.getKey(), m.getMonsterId(), m.getName(), m.getMonsterType(),
                        m.getMaxHp(), m.getCurrentHp(), m.getAtk(), m.getDef(), m.getBuffs(), m.getDescription(),
                        m.getIntent() == null ? BattleServiceImpl.INTENT_ATTACK : m.getIntent(), m.isEnraged()))
                .toList();
    }

    private NodeDTO battleNode(GameSession session) {
        return new NodeDTO(session.getNodeType(), session.getStory(), toBattleMonsterDTOs(session.getMonsters()),
                null, session.getPath(), session.getPathNote());
    }

    /** 조우에는 길 효과가 없으므로 path는 비운다. 고른 길이 무시됐다면 그 안내만 싣는다 */
    private NodeDTO encounterNode(GameSession session, String encounterText) {
        return new NodeDTO("ENCOUNTER", session.getStory(), null,
                new EncounterDTO(encounterText, choiceLabels(session), choiceFactions(session)),
                null, session.getPathNote());
    }

    private List<String> choiceFactions(GameSession session) {
        return session.getChoices().stream()
                .map(choice -> choice.getFaction() == null ? "" : choice.getFaction())
                .toList();
    }

    private List<String> choiceLabels(GameSession session) {
        return session.getChoices().stream().map(EncounterChoice::getLabel).toList();
    }

    private RewardViewDTO toRewardView(RewardOptions options) {
        if (options == null) return null;
        List<SkillInfoDTO> skills = skillRepository.findAllById(options.getSkillIds()).stream()
                .map(SkillInfoDTO::from).toList();
        List<RelicDTO> relics = relicRepository.findAllById(options.getRelicIds()).stream()
                .map(r -> RecordMapper.map(r, RelicDTO.class)).toList();
        List<ConsumableDTO> consumables = consumableRepository.findAllById(options.getConsumableIds()).stream()
                .map(c -> RecordMapper.map(c, ConsumableDTO.class)).toList();
        return new RewardViewDTO(options.getExp(), options.getGold(), skills, relics, consumables);
    }

    private List<ShopItemDTO> toShopItems(List<ShopStockItem> stock) {
        if (stock == null) return List.of();
        return stock.stream()
                .map(item -> new ShopItemDTO(item.getItemType(), item.getItemId(), item.getName(),
                        item.getDescription(), item.getPrice(), item.isSold()))
                .toList();
    }
}
