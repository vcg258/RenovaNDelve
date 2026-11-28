package com.renovandelve.sim;

import com.renovandelve.catalog.domain.Rarity;

import com.renovandelve.catalog.domain.consumableentity.Type;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.catalog.domain.skillentity.Target;
import com.renovandelve.catalog.dto.ArmorDTO;
import com.renovandelve.catalog.dto.HelmetDTO;
import com.renovandelve.catalog.dto.WeaponDTO;
import com.renovandelve.game.dto.GameConsumableInventoryDTO;
import com.renovandelve.game.dto.GameSkillSlotDTO;
import com.renovandelve.game.dto.SkillInfoDTO;
import com.renovandelve.game.dto.request.ConsumableUseRequest;
import com.renovandelve.game.dto.request.EncounterChoiceRequest;
import com.renovandelve.game.dto.request.NewGameRequest;
import com.renovandelve.game.dto.request.PurchaseRequest;
import com.renovandelve.game.dto.request.RewardSelectRequest;
import com.renovandelve.game.dto.request.SkillUseRequest;
import com.renovandelve.game.dto.response.BattleMonsterDTO;
import com.renovandelve.game.dto.response.GameSummaryDTO;
import com.renovandelve.game.dto.response.GameViewDTO;
import com.renovandelve.game.dto.response.NodeDTO;
import com.renovandelve.game.dto.response.RewardViewDTO;
import com.renovandelve.game.dto.response.SetupOptionsDTO;
import com.renovandelve.game.dto.response.ShopItemDTO;
import com.renovandelve.game.dto.response.StatusDTO;
import com.renovandelve.game.engine.SkillSynergies;
import com.renovandelve.game.service.GameEngineService;
import com.renovandelve.game.service.GameStateService;
import com.renovandelve.member.domain.MemberEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 봇 한 판. 게임 엔진의 공개 서비스만 부른다(사람이 화면에서 누르는 것과 같은 행동).
 */
class BotRunner {
    private static final int MAX_ACTIONS = 4000; // 무한 루프 방지

    private final GameEngineService engine;
    private final GameStateService gameStateService;
    private final MemberEntity member;
    private final BotPolicy policy;
    private final String difficulty;

    private final RunResult result = new RunResult();
    private List<BattleMonsterDTO> monsters = List.of();
    private StatusDTO status;
    private boolean guardedLastTurn;

    BotRunner(GameEngineService engine, GameStateService gameStateService, MemberEntity member,
              BotPolicy policy, String difficulty, int runIndex) {
        this.result.runIndex = runIndex;
        this.engine = engine;
        this.gameStateService = gameStateService;
        this.member = member;
        this.policy = policy;
        this.difficulty = difficulty;
    }

    RunResult play() {
        try {
            gameStateService.createNewGame(setup(gameStateService.getSetupOptions(member)), member);
            for (int i = 0; i < MAX_ACTIONS; i++) {
                GameViewDTO view = engine.getView(member);
                status = view.status();
                trackAlignment();
                trackSynergies();
                switch (view.phase()) {
                    case "EXPLORE" -> explore();
                    case "BATTLE" -> {
                        monsters = view.node() == null ? List.of() : view.node().monsters();
                        battleTurn();
                    }
                    case "ENCOUNTER" -> encounter(view.node());
                    case "REWARD" -> reward(view.reward());
                    case "SHOP" -> shop();
                    case "REST" -> engine.chooseRest(member, hpRatio() < policy.restHealBelow ? "HEAL" : "TRAIN");
                    case "GAMEOVER", "CLEAR", "NONE" -> { return finish(); }
                    default -> throw new IllegalStateException("알 수 없는 단계 " + view.phase());
                }
            }
            result.error = "행동 수 초과";
        } catch (Exception e) {
            result.error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        try {
            gameStateService.abandonGame(member);
        } catch (Exception ignored) {
            // 이미 끝났다
        }
        return result;
    }

    private RunResult finish() {
        GameSummaryDTO summary = engine.getSummary(member);
        result.cleared = summary.cleared();
        result.stage = summary.stage();
        result.node = (int) summary.node();
        result.level = summary.level();
        if (summary.stats() != null) {
            result.turns = summary.stats().turns();
            result.battles = summary.stats().battlesWon() + summary.stats().battlesFled();
        }
        return result;
    }

    /* ==================== 원정 준비 ==================== */

    /** 예산 안에서 무기 → 갑옷 → 투구 → 스킬 순으로 가장 비싼 것을 산다 (비싼 것이 좋다고 본다) */
    private NewGameRequest setup(SetupOptionsDTO options) {
        int budget = options.budget();
        var locked = options.locked();
        WeaponDTO weapon = best(options.weapons().stream().filter(w -> !locked.weapons().contains(w.weaponId())).toList(),
                WeaponDTO::scoreCost, (int) (budget * 0.35));
        budget -= weapon == null ? 0 : weapon.scoreCost();
        ArmorDTO armor = best(options.armors().stream().filter(a -> !locked.armors().contains(a.armorId())).toList(),
                ArmorDTO::scoreCost, (int) (options.budget() * 0.25));
        budget -= armor == null ? 0 : armor.scoreCost();
        HelmetDTO helmet = best(options.helmets().stream().filter(h -> !locked.helmets().contains(h.helmetId())).toList(),
                HelmetDTO::scoreCost, (int) (options.budget() * 0.15));
        budget -= helmet == null ? 0 : helmet.scoreCost();
        List<Long> skills = new ArrayList<>();
        List<SkillInfoDTO> chosen = new ArrayList<>();
        List<SkillInfoDTO> candidates = new ArrayList<>(options.skills().stream()
                .filter(sk -> !locked.skills().contains(sk.skillId())).toList());
        while (chosen.size() < 3) {
            int left = budget;
            SkillInfoDTO pick = candidates.stream()
                    .filter(sk -> sk.cost() > 0 && sk.cost() <= left && !chosen.contains(sk))
                    .max(Comparator.comparingDouble(sk -> skillScore(sk, chosen))).orElse(null);
            if (pick == null) break;
            chosen.add(pick);
            skills.add(pick.skillId());
            budget -= pick.cost();
        }
        return new NewGameRequest(difficulty, weapon == null ? null : weapon.weaponId(),
                armor == null ? null : armor.armorId(), helmet == null ? null : helmet.helmetId(),
                skills, List.of(), List.of());
    }

    private static <T> T best(List<T> items, java.util.function.ToIntFunction<T> cost, int limit) {
        return items.stream().filter(i -> cost.applyAsInt(i) <= limit)
                .max(Comparator.comparingInt(cost)).orElse(null);
    }

    /* ==================== 탐험 ==================== */

    private void explore() {
        // 체력이 낮으면 탐험 중에 물약을 마신다
        if (hpRatio() < 0.5 && drinkPotion()) return;
        String path = "SAFE";
        if (policy.dangerPath && hpRatio() > 0.6) path = "DANGER";
        else if (policy.ambushWhenHurt && (policy == BotPolicy.AMBUSH_ONLY || hpRatio() < 0.45)) path = "AMBUSH";
        result.nodesVisited++;
        double hpBefore = hpRatio();
        NodeDTO node = engine.nextNode(member, path);
        if ("WRAITH".equals(node.nodeType())) result.wraiths++;
        if ("BOSS".equals(node.nodeType())) {
            result.bossEntryHp += hpBefore;
            result.bossEntries++;
            result.lastBossEntryHp = hpBefore;
        } else if ("BATTLE".equals(node.nodeType()) || "ELITE".equals(node.nodeType())) {
            result.battleEntryHp += hpBefore;
            result.battleEntries++;
        }
    }

    /* ==================== 전투 ==================== */

    private void battleTurn() {
        List<BattleMonsterDTO> alive = monsters.stream().filter(m -> m.currentHp() > 0).toList();
        result.turnsTaken++;

        if (hpRatio() < 0.35 && hasPotion()) {
            drinkPotion();
            return;
        }

        int incoming = expectedIncoming(alive);
        int hp = status.getCharacterDTO().getCurrentHp();
        // 사람처럼 막는다: 위험한 턴에만, 그리고 두 턴 연속으로는 막지 않는다 (막기만 하면 전투가 끝나지 않는다)
        boolean danger = incoming >= hp * policy.guardIfIncomingOver && !guardedLastTurn;
        GameSkillSlotDTO skill = bestReadySkill(alive.size());

        if (danger || skill == null) {
            if (skill == null && allCooling()) {
                engine.passTurn(member);
                guardedLastTurn = false;
            } else {
                engine.guard(member);
                guardedLastTurn = true;
            }
            return;
        }
        guardedLastTurn = false;
        BattleMonsterDTO target = alive.stream()
                .filter(m -> !"GUARD".equals(m.intent()))
                .min(Comparator.comparingInt(BattleMonsterDTO::currentHp))
                .orElse(alive.get(0));
        engine.useSkill(member, new SkillUseRequest(skill.getSlotId(), List.of(target.key())));
    }

    /** 이번 턴 예상 피해 (방어 무시, 강공 1.6배, 기절·방어 태세 제외) */
    private int expectedIncoming(List<BattleMonsterDTO> alive) {
        int defense = status.getCharacterDTO().getDefense();
        int total = 0;
        for (BattleMonsterDTO m : alive) {
            if (m.buffs() != null && m.buffs().getOrDefault("STUN", 0) > 0) continue;
            if ("GUARD".equals(m.intent())) continue;
            int atk = "HEAVY".equals(m.intent()) ? (int) Math.round(m.atk() * 1.6) : m.atk();
            total += Math.max((int) Math.ceil(atk * 0.15), atk - defense);
        }
        return total;
    }

    private GameSkillSlotDTO bestReadySkill(int aliveCount) {
        return status.getSkillSlotDTO().stream()
                .filter(s -> s.getSkill() != null && s.getCoolTime() <= 0)
                .max(Comparator.comparingDouble(s -> slotPower(s, aliveCount)))
                .orElse(null);
    }

    private boolean allCooling() {
        return status.getSkillSlotDTO().stream().allMatch(s -> s.getCoolTime() > 0);
    }

    private static double slotPower(GameSkillSlotDTO slot, int aliveCount) {
        double coefficient = slot.getSkill().coefficient() == null ? 1 : slot.getSkill().coefficient().doubleValue();
        double hits = Math.max(1, slot.getSkill().attackCount());
        double targets = slot.getSkill().target() == Target.EnemyAll ? aliveCount : 1;
        return coefficient * hits * targets + slot.getSkill().effectStack() * 0.2;
    }

    static double skillPower(SkillInfoDTO skill) {
        double coefficient = skill.coefficient() == null ? 1 : skill.coefficient().doubleValue();
        double hits = Math.max(1, skill.attackCount());
        double targets = skill.target() == Target.EnemyAll ? 1.8 : 1;
        return coefficient * hits * targets / Math.max(1, skill.cooldown() * 0.5 + 0.5) + skill.effectStack() * 0.2;
    }

    /* ==================== 스킬 조합 ==================== */

    /** 시너지 하나의 값. 스킬 위력(대개 1~3)과 같은 단위로 본다 */
    private static final double SYNERGY_WEIGHT = 1.5;

    /** 스킬 점수: 위력 + (조합을 보는 봇이면) 이 스킬로 새로 켜지는 시너지 */
    private double skillScore(SkillInfoDTO skill, List<SkillInfoDTO> owned) {
        double power = skillPower(skill);
        if (!policy.seekSynergy) return power;
        List<SkillInfoDTO> after = new ArrayList<>(owned);
        after.add(skill);
        return power + SYNERGY_WEIGHT * (synergyCount(after) - synergyCount(owned));
    }

    private double loadoutValue(List<SkillInfoDTO> skills) {
        return skills.stream().mapToDouble(BotRunner::skillPower).sum() + SYNERGY_WEIGHT * synergyCount(skills);
    }

    /** 실제 엔진의 판정({@link SkillSynergies})으로 센다 */
    private static int synergyCount(List<SkillInfoDTO> skills) {
        return SkillSynergies.of(skills.stream().map(BotRunner::toEntity).toList()).getActiveSynergies().size();
    }

    private static SkillEntity toEntity(SkillInfoDTO dto) {
        return SkillEntity.builder().skillId(dto.skillId()).name(dto.name()).target(dto.target())
                .attackCount(dto.attackCount()).coefficient(dto.coefficient()).statusEffect(dto.statusEffect())
                .effectStack(dto.effectStack()).build();
    }

    private List<SkillInfoDTO> ownedSkills() {
        return status.getSkillSlotDTO().stream().map(GameSkillSlotDTO::getSkill).filter(java.util.Objects::nonNull)
                .map(BotRunner::toInfo).toList();
    }

    private static SkillInfoDTO toInfo(com.renovandelve.catalog.dto.SkillDTO sk) {
        return new SkillInfoDTO(null, sk.name(), sk.target(), sk.attackCount(), sk.coefficient(), sk.cost(),
                sk.cooldown(), sk.statusEffect(), sk.effectStack(), sk.description(), sk.basic(), sk.rarity());
    }

    /* ==================== 가호 ==================== */

    /** 원정 중 한 번이라도 켜진 스킬 시너지를 모은다 (끝난 뒤의 상태에는 스킬 정보가 없다) */
    private void trackSynergies() {
        if (status == null || status.getSkillSynergies() == null || status.getSkillSynergies().isEmpty()) return;
        List<String> names = status.getSkillSynergies().stream().map(s -> s.split(" — ")[0]).toList();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>(result.skillSynergies);
        seen.addAll(names);
        result.skillSynergies = List.copyOf(seen);
        if (names.contains("빈틈 찌르기") && names.contains("재앙의 조율")) result.heavyAndAfflictedTogether = true;
    }

    private void trackAlignment() {
        if (result.tier3AtNode >= 0 || status == null || status.getAlignment() == null) return;
        if (status.getAlignment().tier() >= 3) result.tier3AtNode = result.nodesVisited;
    }

    /* ==================== 조우 ==================== */

    private void encounter(NodeDTO node) {
        List<String> factions = node.encounter() == null ? List.of() : node.encounter().factions();
        int choice;
        if ("CONTRACT".equals(node.nodeType())) {
            boolean sign = policy.signContracts;
            choice = sign ? 0 : 1;
            if (sign) result.contracts++;
        } else {
            choice = Math.max(0, factions.indexOf(policy.faction));
            if (result.tier3AtNode >= 0) result.encountersAfterTier3++;
        }
        engine.chooseEncounter(member, new EncounterChoiceRequest(choice));
    }

    /* ==================== 보상 / 상점 ==================== */

    private void reward(RewardViewDTO reward) {
        if (reward == null) {
            engine.selectReward(member, new RewardSelectRequest("SKIP", null, null));
            return;
        }
        if (!reward.relics().isEmpty()) {
            var relic = reward.relics().stream().max(Comparator.comparingInt(r -> rarityRank(r.rarity()))).orElseThrow();
            engine.selectReward(member, new RewardSelectRequest("RELIC", relic.relicId(), null));
            return;
        }
        List<SkillInfoDTO> owned = ownedSkills();
        SkillInfoDTO bestSkill = reward.skills().stream().max(Comparator.comparingDouble(sk -> skillScore(sk, owned))).orElse(null);
        if (bestSkill != null && tryLearn(bestSkill, "SKILL", false)) return;
        var potion = reward.consumables().stream().filter(c -> c.effectType() == Type.RECOVERY).findFirst();
        if (potion.isPresent()) {
            engine.selectReward(member, new RewardSelectRequest("CONSUMABLE", potion.get().consumableId(), null));
            return;
        }
        engine.selectReward(member, new RewardSelectRequest("SKIP", null, null));
    }

    /** 스킬을 배운다. 가득 찼으면 가장 약한 스킬과 바꾸되, 더 약하면 배우지 않는다 */
    private boolean tryLearn(SkillInfoDTO skill, String type, boolean shop) {
        List<GameSkillSlotDTO> slots = status.getSkillSlotDTO();
        Long replace = null;
        int max = status.getMaxSkillSlots() == 0 ? 8 : status.getMaxSkillSlots();
        if (slots.size() >= max && policy.seekSynergy) {
            // 조합을 보는 봇: 바꾼 뒤의 (스킬 위력 합 + 시너지) 가 가장 큰 칸과 바꾸고, 나아지지 않으면 배우지 않는다
            List<SkillInfoDTO> owned = ownedSkills();
            double before = loadoutValue(owned);
            GameSkillSlotDTO bestSlot = null;
            double bestValue = before;
            for (GameSkillSlotDTO slot : slots) {
                if (slot.getSkill() == null || slot.getSkill().cost() == 0) continue;
                List<SkillInfoDTO> after = new ArrayList<>(owned);
                after.remove(toInfo(slot.getSkill()));
                after.add(skill);
                double value = loadoutValue(after);
                if (value > bestValue) {
                    bestValue = value;
                    bestSlot = slot;
                }
            }
            if (bestSlot == null) return false;
            replace = bestSlot.getSlotId();
        } else if (slots.size() >= max) {
            GameSkillSlotDTO weakest = slots.stream().filter(s -> s.getSkill() != null && s.getSkill().cost() > 0)
                    .min(Comparator.comparingDouble(s -> slotPower(s, 1))).orElse(null);
            if (weakest == null || slotPower(weakest, 1) >= skillPower(skill)) return false;
            replace = weakest.getSlotId();
        }
        if (shop) {
            engine.buyItem(member, new PurchaseRequest(type, skill.skillId(), replace));
        } else {
            engine.selectReward(member, new RewardSelectRequest(type, skill.skillId(), replace));
        }
        return true;
    }

    /**
     * 상점: 사람처럼 쓸모 있는 것부터 산다.
     * 1) 지금 장비보다 보너스가 큰 장비 (오르는 폭이 큰 것부터) 2) 유물 (등급이 높은 것부터)
     * 3) 지금 가진 가장 약한 스킬보다 센 스킬 4) 회복 물약 (2개까지). 물약 하나 값은 끝까지 남긴다
     */
    private void shop() {
        List<ShopItemDTO> items = new ArrayList<>(engine.getShop(member));
        items.sort(Comparator.comparingInt(this::shopPriority));
        int reserve = items.stream().filter(BotRunner::isPotionName).mapToInt(ShopItemDTO::price).min().orElse(0);
        for (ShopItemDTO item : items) {
            if (item.sold()) continue;
            int gold = status.getStateDTO().getGold();
            boolean affordable = item.price() <= gold - (isPotionName(item) ? 0 : reserve);
            boolean buy = affordable && switch (item.itemType()) {
                case "WEAPON", "ARMOR", "HELMET" -> item.bonus() != null && item.bonus() > nullToZero(item.currentBonus());
                case "RELIC" -> true;
                case "CONSUMABLE" -> potionCount() < 2 && isPotionName(item);
                default -> false;
            };
            try {
                if ("SKILL".equals(item.itemType()) && affordable) {
                    SkillInfoDTO skill = skillOf(item);
                    if (skill != null && tryLearn(skill, "SKILL", true)) status = engine.getView(member).status();
                } else if (buy) {
                    status = engine.buyItem(member, new PurchaseRequest(item.itemType(), item.itemId(), null));
                }
            } catch (Exception ignored) {
                // 살 수 없었다 (골드 부족 등)
            }
        }
        engine.leaveShop(member);
    }

    private int shopPriority(ShopItemDTO item) {
        return switch (item.itemType()) {
            case "WEAPON", "ARMOR", "HELMET" -> -(nullToZero(item.bonus()) - nullToZero(item.currentBonus())) * 10;
            case "RELIC" -> 1000 - rarityRank(item.rarity()) * 10;
            case "SKILL" -> 2000;
            default -> 3000;
        };
    }

    /** 상점의 스킬 진열품을 카탈로그 스킬로 (능력 비교용) */
    private SkillInfoDTO skillOf(ShopItemDTO item) {
        if (skillCatalog == null) skillCatalog = gameStateService.getSetupOptions(member).skills();
        return skillCatalog.stream().filter(sk -> sk.skillId().equals(item.itemId())).findFirst().orElse(null);
    }

    private List<SkillInfoDTO> skillCatalog;

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static int rarityRank(Rarity rarity) {
        return rarity == null ? 0 : rarity.ordinal();
    }

    private static boolean isPotionName(ShopItemDTO item) {
        return item.name() != null && item.name().contains("회복");
    }

    /* ==================== 소모품 ==================== */

    private boolean hasPotion() {
        return potionCount() > 0;
    }

    private int potionCount() {
        return (int) status.getConsumableInventoryDTO().stream()
                .filter(c -> c.getConsumable() != null && c.getConsumable().effectType() == Type.RECOVERY).count();
    }

    private boolean drinkPotion() {
        GameConsumableInventoryDTO potion = status.getConsumableInventoryDTO().stream()
                .filter(c -> c.getConsumable() != null && c.getConsumable().effectType() == Type.RECOVERY)
                .findFirst().orElse(null);
        if (potion == null) return false;
        engine.useConsumable(member, new ConsumableUseRequest(potion.getSlotId()));
        result.potions++;
        return true;
    }

    private double hpRatio() {
        var c = status.getCharacterDTO();
        return c == null || c.getMaxHp() == 0 ? 1 : (double) c.getCurrentHp() / c.getMaxHp();
    }
}
