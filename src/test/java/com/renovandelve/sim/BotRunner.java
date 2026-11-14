package com.renovandelve.sim;

import com.renovandelve.catalog.domain.consumableentity.Type;
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

    BotRunner(GameEngineService engine, GameStateService gameStateService, MemberEntity member,
              BotPolicy policy, String difficulty) {
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
        WeaponDTO weapon = best(options.weapons(), WeaponDTO::scoreCost, (int) (budget * 0.35));
        budget -= weapon == null ? 0 : weapon.scoreCost();
        ArmorDTO armor = best(options.armors(), ArmorDTO::scoreCost, (int) (options.budget() * 0.25));
        budget -= armor == null ? 0 : armor.scoreCost();
        HelmetDTO helmet = best(options.helmets(), HelmetDTO::scoreCost, (int) (options.budget() * 0.15));
        budget -= helmet == null ? 0 : helmet.scoreCost();
        List<Long> skills = new ArrayList<>();
        List<SkillInfoDTO> candidates = new ArrayList<>(options.skills());
        candidates.sort(Comparator.comparingDouble(BotRunner::skillPower).reversed());
        for (SkillInfoDTO skill : candidates) {
            if (skills.size() >= 3) break;
            if (skill.cost() <= budget && skill.cost() > 0) {
                skills.add(skill.skillId());
                budget -= skill.cost();
            }
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
        else if (policy.ambushWhenHurt && hpRatio() < 0.45) path = "AMBUSH";
        NodeDTO node = engine.nextNode(member, path);
        if ("WRAITH".equals(node.nodeType())) result.wraiths++;
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
        boolean danger = incoming >= hp * policy.guardIfIncomingOver;
        GameSkillSlotDTO skill = bestReadySkill(alive.size());

        if (danger || skill == null) {
            if (skill == null && allCooling()) {
                engine.passTurn(member);
            } else {
                engine.guard(member);
            }
            return;
        }
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
            engine.selectReward(member, new RewardSelectRequest("RELIC", reward.relics().get(0).relicId(), null));
            return;
        }
        SkillInfoDTO bestSkill = reward.skills().stream().max(Comparator.comparingDouble(BotRunner::skillPower)).orElse(null);
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
        if (slots.size() >= max) {
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

    private void shop() {
        List<ShopItemDTO> items = engine.getShop(member);
        for (ShopItemDTO item : items) {
            if (item.sold()) continue;
            int gold = status.getStateDTO().getGold();
            boolean buy = switch (item.itemType()) {
                case "RELIC" -> item.price() <= gold;
                case "CONSUMABLE" -> item.price() <= gold && potionCount() < 2 && isPotionName(item);
                default -> false;
            };
            if (buy) {
                try {
                    status = engine.buyItem(member, new PurchaseRequest(item.itemType(), item.itemId(), null));
                } catch (Exception ignored) {
                    // 살 수 없었다 (골드 부족 등)
                }
            }
        }
        engine.leaveShop(member);
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
