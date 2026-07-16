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
import com.renovandelve.service.redis.GameSessionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

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

    private final Random random = new Random();

    private static final int FINAL_STAGE = 5;
    private static final int NODES_PER_STAGE = 10;
    private static final double BASE_CRIT_CHANCE = 0.08;
    private static final double DAGGER_CRIT_BONUS = 0.15;

    /* ==================== 조회 ==================== */

    @Override
    @Transactional
    public GameViewDTO getView(MemberEntity member) {
        Optional<GameStateEntity> active = gameStateRepository.findByMember_MemberIdAndIsActiveTrue(member.getMemberId());
        if (active.isEmpty()) {
            return new GameViewDTO("NONE", null, null, null, null, null);
        }
        GameStateEntity gameState = active.get();
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());

        NodeDTO node = null;
        RewardViewDTO reward = null;
        List<ShopItemDTO> shop = null;
        GameSummaryDTO summary = null;

        switch (session.getPhase()) {
            case GameSession.PHASE_BATTLE ->
                    node = new NodeDTO(session.getNodeType(), session.getStory(), toBattleMonsterDTOs(session.getMonsters()), null);
            case GameSession.PHASE_ENCOUNTER ->
                    node = new NodeDTO("ENCOUNTER", session.getStory(),
                            null, new EncounterDTO(session.getStory(), choiceLabels(session)));
            case GameSession.PHASE_REWARD -> reward = toRewardView(session.getReward());
            case GameSession.PHASE_SHOP -> shop = toShopItems(session.getShop());
            case GameSession.PHASE_GAMEOVER, GameSession.PHASE_CLEAR -> summary = buildSummary(gameState, session);
            default -> { /* EXPLORE */ }
        }

        return new GameViewDTO(session.getPhase(), statusAssembler.toStatusDTO(gameState), node, reward, shop, summary);
    }

    /* ==================== 노드 진행 ==================== */

    @Override
    @Transactional
    public NodeDTO nextNode(MemberEntity member) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());

        // 이미 노드가 진행 중이면(서버-클라이언트 단계 불일치 등) 현재 노드를 그대로 반환 -> 멱등 재진입
        if (GameSession.PHASE_BATTLE.equals(session.getPhase())) {
            return new NodeDTO(session.getNodeType(), session.getStory(),
                    toBattleMonsterDTOs(session.getMonsters()), null);
        }
        if (GameSession.PHASE_ENCOUNTER.equals(session.getPhase())) {
            return new NodeDTO("ENCOUNTER", session.getStory(), null,
                    new EncounterDTO(session.getStory(), choiceLabels(session)));
        }
        requirePhase(session, GameSession.PHASE_EXPLORE, "지금은 이동할 수 없습니다.");

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
            String nodeType = resolveNodeType(node, monsters);

            session.setPhase(GameSession.PHASE_BATTLE);
            session.setNodeType(nodeType);
            session.setStory(ai.story());
            session.setMonsters(monsters);
            session.setChoices(new ArrayList<>());
            gameSessionService.save(session);

            return new NodeDTO(nodeType, ai.story(), toBattleMonsterDTOs(monsters), null);
        }

        // 인카운터
        Encounter encounter = ai.encounter() != null ? ai.encounter()
                : new Encounter("어둠 속에서 무언가가 움직인다.", List.of("이곳을 빠르게 벗어난다", "조심스럽게 다가간다"));
        List<EncounterChoice> choices = buildEncounterChoices(encounter, gameState);

        session.setPhase(GameSession.PHASE_ENCOUNTER);
        session.setNodeType("ENCOUNTER");
        session.setStory(ai.story() != null ? ai.story() : encounter.text());
        session.setMonsters(new ArrayList<>());
        session.setChoices(choices);
        gameSessionService.save(session);

        return new NodeDTO("ENCOUNTER", session.getStory(), null,
                new EncounterDTO(encounter.text(), choiceLabels(session)));
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
        List<String> log = new ArrayList<>();
        log.add("[%s] 사용!".formatted(skill.getName()));

        int attack = (int) Math.round((character.getAttack() + session.getTempAtkBonus()) * effects.getAttackMultiplier());
        double critChance = BASE_CRIT_CHANCE + effects.getCritBonus() + daggerCritBonus(gameState);

        List<SessionMonster> targets = resolveTargets(session, skill, request.targetKeys());

        // 1) 플레이어 공격
        battleService.applyPlayerAttack(skill, attack, critChance, effects.getSkillDamageMultiplier(),
                effects.getBleedMultiplier(), effects.getCritBleedStacks(), targets, log);

        if (battleService.isAllMonsterDead(session.getMonsters())) {
            return processVictory(gameState, session, character, effects, log);
        }

        // 2) 상태이상 부여 + 쿨타임 갱신 + 도트 피해
        battleService.applyStatusEffect(skill, targets, log);
        battleService.updateCoolTimes(slots, slot.getSlotId(), effects.getCooldownReduction());
        battleService.applyDotDamage(session.getMonsters(), log);

        if (battleService.isAllMonsterDead(session.getMonsters())) {
            return processVictory(gameState, session, character, effects, log);
        }

        // 3) 몬스터 반격
        return processMonsterCounterattack(gameState, session, character, effects, log);
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
        return processMonsterCounterattack(gameState, session, character, effects, log);
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
                addSkillToSlot(gameState, request.itemId());
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

        shopService.applyPurchase(gameState, member, stockItem);
        stockItem.setSold(true);
        gameSessionService.save(session);

        return statusAssembler.toStatusDTO(gameState);
    }

    @Override
    @Transactional
    public GameViewDTO leaveShop(MemberEntity member) {
        GameStateEntity gameState = activeGame(member);
        GameSession session = gameSessionService.getOrCreate(gameState.getGameStateId());
        requirePhase(session, GameSession.PHASE_SHOP, "상점 이용 단계가 아닙니다.");

        session.setPhase(GameSession.PHASE_EXPLORE);
        session.setMonsters(new ArrayList<>());
        session.setChoices(new ArrayList<>());
        session.setShop(new ArrayList<>());
        session.setTempAtkBonus(0);
        session.setTempDefBonus(0);
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
        try {
            return monsterRagService.chat(stage, node, conversationId);
        } catch (Exception e) {
            log.warn("AI 노드 생성 실패, 폴백 사용. stage={} node={} 원인={}", stage, node, e.getMessage());
            boolean isBoss = node % NODES_PER_STAGE == 0;
            if (isBoss || random.nextDouble() > 0.3) {
                List<MonsterDTO> monsters = isBoss
                        ? monsterService.getRandomMonsterBoss()
                        : monsterService.getRandomMonsterNormal();
                return AsyncStageResponseDTO.battle(fallbackStory(stage, true), monsters);
            }
            return AsyncStageResponseDTO.encounter(fallbackStory(stage, false),
                    new Encounter("어둠 속에서 희미한 기척이 느껴진다.", List.of("서둘러 자리를 피한다", "정체를 확인한다")));
        }
    }

    private String fallbackStory(int stage, boolean battle) {
        String place = switch (stage) {
            case 1 -> "무너진 왕궁의 정문";
            case 2 -> "왕실 기사단의 연무장";
            case 3 -> "궁정 마법사의 탑";
            case 4 -> "축축한 지하 묘지";
            default -> "악마의 왕좌로 향하는 회랑";
        };
        return battle
                ? place + " — 짙은 어둠 너머에서 적의가 느껴진다. 무언가가 다가온다."
                : place + " — 낯선 정적이 흐른다. 무슨 일이 일어날지 알 수 없다.";
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
        for (MonsterDTO dto : monsterDTOs) {
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

    private TurnResultDTO processMonsterCounterattack(GameStateEntity gameState, GameSession session,
                                                      GameCharacterEntity character, RelicEffects effects,
                                                      List<String> log) {
        int defense = character.getDefense() + session.getTempDefBonus() + effects.getDefenseBonus();
        int damage = battleService.monsterAttackDamage(session.getMonsters(), defense, effects.getFlatDamageReduction(), log);
        character.takeDamage(damage);

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
        double multiplier = rewardMultiplier(gameState.getDifficulty());

        int totalScore = 0, totalGold = 0, totalExp = 0;
        for (SessionMonster monster : session.getMonsters()) {
            int score = monster.getScoreReward() > 0 ? monster.getScoreReward()
                    : switch (monster.getMonsterType()) { case 2 -> 400; case 1 -> 120; default -> 50; };
            score += stage * 10;
            totalScore += score;
            totalGold += score / 3;
            totalExp += switch (monster.getMonsterType()) { case 2 -> 200; case 1 -> 70; default -> 30; };
        }
        totalScore = (int) (totalScore * multiplier);
        totalGold = (int) (totalGold * multiplier * effects.getGoldMultiplier());

        gameState.gainScore(totalScore);
        gameState.gainGold(totalGold);

        int levelUps = character.gainExp(totalExp);
        log.add("승리! 점수 +%d, 골드 +%d, 경험치 +%d".formatted(totalScore, totalGold, totalExp));
        if (levelUps > 0) {
            log.add("레벨 업! Lv.%d (최대HP/공격/방어 상승)".formatted(character.getLevel()));
        }

        // 쿨타임 초기화
        gameState.getGameSkillSlot().forEach(slot -> slot.setCoolTime(0));

        // 임시 버프 해제
        session.setTempAtkBonus(0);
        session.setTempDefBonus(0);

        boolean cleared = stage >= FINAL_STAGE && node >= NODES_PER_STAGE;
        if (cleared) {
            terminate(gameState, session, true);
            log.add("마계 포탈이 봉인되었다. 왕국에 새벽이 찾아온다...");
            gameSessionService.save(session);
            gameStateRepository.save(gameState);
            return new TurnResultDTO("WIN", log, toBattleMonsterDTOs(session.getMonsters()),
                    statusAssembler.toStatusDTO(gameState), null, true, session.getPhase());
        }

        // 보상 후보 생성 -> REWARD 단계
        session.setReward(buildRewardOptions(gameState, totalExp, totalGold));
        session.setPhase(GameSession.PHASE_REWARD);
        gameSessionService.save(session);
        gameStateRepository.save(gameState);

        return new TurnResultDTO("WIN", log, toBattleMonsterDTOs(session.getMonsters()),
                statusAssembler.toStatusDTO(gameState), toRewardView(session.getReward()), false, session.getPhase());
    }

    private RewardOptions buildRewardOptions(GameStateEntity gameState, int exp, int gold) {
        RewardOptions options = new RewardOptions();
        options.setExp(exp);
        options.setGold(gold);

        List<SkillEntity> skills = new ArrayList<>(skillRepository.findBySkillNotOwner(gameState.getGameStateId()));
        Collections.shuffle(skills, random);
        options.setSkillIds(skills.stream().limit(3).map(SkillEntity::getSkillId).collect(Collectors.toList()));

        List<RelicEntity> relics = new ArrayList<>(relicRepository.findByRelicNotOwner(gameState.getGameStateId()));
        Collections.shuffle(relics, random);
        options.setRelicIds(relics.stream().limit(1).map(RelicEntity::getRelicId).collect(Collectors.toList()));

        List<ConsumableEntity> consumables = new ArrayList<>(consumableRepository.findAll());
        Collections.shuffle(consumables, random);
        options.setConsumableIds(consumables.stream().limit(2).map(ConsumableEntity::getConsumableId).collect(Collectors.toList()));

        return options;
    }

    private void applyConsumableEffect(GameStateEntity gameState, GameSession session,
                                       GameCharacterEntity character, ConsumableEntity consumable, List<String> log) {
        int value = consumable.getEffectValue();
        switch (consumable.getEffectType()) {
            case RECOVERY -> {
                character.heal(value);
                log.add("%s 사용: HP %d 회복".formatted(consumable.getName(), value));
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

    private List<EncounterChoice> buildEncounterChoices(Encounter encounter, GameStateEntity gameState) {
        int stage = gameState.getCurrentStage();
        List<EncounterChoice> choices = new ArrayList<>();

        List<String> reasons = encounter.selections() == null ? List.of() : encounter.selections();
        String fleeReason = !reasons.isEmpty() ? reasons.get(0) : "위험을 감수할 이유가 없다";
        String negotiateReason = reasons.size() > 1 ? reasons.get(1) : "말이 통할지도 모른다";

        // 1. 도망: 안전하지만 얻는 것도 없다 (가끔 다침)
        choices.add(choice("도망친다 — " + fleeReason,
                random.nextDouble() < 0.35 ? "DAMAGE" : "NOTHING", 8 + stage * 2, null));

        // 2. 협상: 골드가 오간다
        double negotiateRoll = random.nextDouble();
        if (negotiateRoll < 0.4) choices.add(choice("협상한다 — " + negotiateReason, "GOLD_MINUS", 20 + stage * 10, null));
        else if (negotiateRoll < 0.7) choices.add(choice("협상한다 — " + negotiateReason, "GOLD_PLUS", 25 + stage * 12, null));
        else choices.add(choice("협상한다 — " + negotiateReason, "NOTHING", 0, null));

        // 3. 조사: 하이리스크 하이리턴
        double investigateRoll = random.nextDouble();
        if (investigateRoll < 0.22) {
            List<ConsumableEntity> pool = consumableRepository.findAll();
            if (!pool.isEmpty()) {
                ConsumableEntity picked = pool.get(random.nextInt(pool.size()));
                choices.add(choice("주변을 조사한다", "CONSUMABLE", 0, picked.getConsumableId()));
            } else {
                choices.add(choice("주변을 조사한다", "GOLD_PLUS", 30 + stage * 10, null));
            }
        } else if (investigateRoll < 0.32) {
            List<RelicEntity> pool = relicRepository.findByRelicNotOwner(gameState.getGameStateId());
            if (!pool.isEmpty()) {
                RelicEntity picked = pool.get(random.nextInt(pool.size()));
                choices.add(choice("주변을 조사한다", "RELIC", 0, picked.getRelicId()));
            } else {
                choices.add(choice("주변을 조사한다", "GOLD_PLUS", 30 + stage * 10, null));
            }
        } else if (investigateRoll < 0.55) {
            choices.add(choice("주변을 조사한다", "GOLD_PLUS", 30 + stage * 10, null));
        } else if (investigateRoll < 0.8) {
            choices.add(choice("주변을 조사한다", "DAMAGE", 12 + stage * 3, null));
        } else {
            choices.add(choice("주변을 조사한다", "HEAL", 20, null));
        }

        return choices;
    }

    private EncounterChoice choice(String label, String effect, int value, Long refId) {
        EncounterChoice choice = new EncounterChoice();
        choice.setLabel(label);
        choice.setEffect(effect);
        choice.setValue(value);
        choice.setRefId(refId);
        return choice;
    }

    private String applyEncounterEffect(GameStateEntity gameState, GameCharacterEntity character, EncounterChoice choice) {
        switch (choice.getEffect()) {
            case "HEAL" -> {
                int amount = character.getMaxHp() * choice.getValue() / 100;
                character.heal(amount);
                return "따스한 기운이 몸을 감싼다. HP %d 회복.".formatted(amount);
            }
            case "DAMAGE" -> {
                character.takeDamage(choice.getValue());
                return "함정이었다! HP %d 피해를 입었다.".formatted(choice.getValue());
            }
            case "GOLD_PLUS" -> {
                gameState.gainGold(choice.getValue());
                return "숨겨진 주머니를 발견했다. 골드 +%d.".formatted(choice.getValue());
            }
            case "GOLD_MINUS" -> {
                int loss = Math.min(gameState.getGold(), choice.getValue());
                gameState.gainGold(-loss);
                return "대가를 치르고 무사히 지나갔다. 골드 -%d.".formatted(loss);
            }
            case "CONSUMABLE" -> {
                addConsumable(gameState, choice.getRefId());
                String name = consumableRepository.findById(choice.getRefId()).map(ConsumableEntity::getName).orElse("무언가");
                return "낡은 상자 안에서 [%s]을(를) 발견했다!".formatted(name);
            }
            case "RELIC" -> {
                addRelic(gameState, choice.getRefId());
                String name = relicRepository.findById(choice.getRefId()).map(RelicEntity::getName).orElse("무언가");
                return "어둠 속에서 유물 [%s]이(가) 빛나고 있었다!".formatted(name);
            }
            default -> {
                return "아무 일도 일어나지 않았다. 조용히 발걸음을 옮긴다.";
            }
        }
    }

    private void addSkillToSlot(GameStateEntity gameState, Long skillId) {
        List<GameSkillSlotEntity> slots = gameState.getGameSkillSlot();
        if (slots.stream().anyMatch(slot -> slot.getSkill().getSkillId().equals(skillId))) {
            throw new IllegalStateException("이미 보유 중인 스킬입니다.");
        }
        if (slots.size() >= 8) {
            throw new IllegalStateException("스킬 슬롯이 가득 찼습니다. (최대 8개)");
        }
        SkillEntity skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new NoSuchElementException("스킬을 찾을 수 없습니다."));
        Set<Integer> used = slots.stream().map(GameSkillSlotEntity::getSlotIndex).collect(Collectors.toSet());
        int nextIndex = 0;
        while (used.contains(nextIndex)) nextIndex++;

        GameSkillSlotEntity newSlot = GameSkillSlotEntity.builder()
                .gameState(gameState).skill(skill).slotIndex(nextIndex).coolTime(0).build();
        slots.add(newSlot);
        gameStateRepository.save(gameState);
        // 별도 save: cascade가 없으므로 직접 저장
        gameSkillSlotSave(newSlot);
    }

    private final GameSkillSlotRepository gameSkillSlotRepository;

    private void gameSkillSlotSave(GameSkillSlotEntity slot) {
        gameSkillSlotRepository.save(slot);
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
            return "황폐한 왕궁을 헤매던 모험가의 이야기는 여기서 막을 내렸다.";
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
                        m.getMaxHp(), m.getCurrentHp(), m.getAtk(), m.getDef(), m.getBuffs(), m.getDescription()))
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
