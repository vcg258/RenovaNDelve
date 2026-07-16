package com.renovandelve.service;

import com.renovandelve.domain.*;
import com.renovandelve.domain.armorentity.ArmorEntity;
import com.renovandelve.domain.consumableentity.ConsumableEntity;
import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.domain.helmetentity.HelmetEntity;
import com.renovandelve.domain.relicentity.Rarity;
import com.renovandelve.domain.relicentity.RelicEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.domain.weaponentity.WeaponEntity;
import com.renovandelve.engine.ShopStockItem;
import com.renovandelve.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Log4j2
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {
    private final WeaponRepository weaponRepository;
    private final ArmorRepository armorRepository;
    private final HelmetRepository helmetRepository;
    private final SkillRepository skillRepository;
    private final RelicRepository relicRepository;
    private final ConsumableRepository consumableRepository;
    private final GameStateRepository gameStateRepository;
    private final GameEquipmentRepository gameEquipmentRepository;
    private final GameSkillSlotRepository gameSkillSlotRepository;
    private final GameRelicInventoryRepository gameRelicInventoryRepository;
    private final GameConsumableInventoryRepository gameConsumableInventoryRepository;
    private final CollectionWeaponRepository collectionWeaponRepository;
    private final CollectionArmorRepository collectionArmorRepository;
    private final CollectionHelmetRepository collectionHelmetRepository;
    private final CollectionSkillRepository collectionSkillRepository;
    private final CollectionRelicRepository collectionRelicRepository;
    private final CollectionConsumableRepository collectionConsumableRepository;

    private final Random random = new Random();

    // 기본 가격 (스테이지에 따라 가산)
    private static final int PRICE_WEAPON = 300;
    private static final int PRICE_ARMOR = 220;
    private static final int PRICE_HELMET = 200;
    private static final int PRICE_SKILL = 250;
    private static final Map<Rarity, Integer> RELIC_PRICE = Map.of(
            Rarity.COMMON, 250,
            Rarity.RARE, 420,
            Rarity.EPIC, 700
    );

    @Override
    public List<ShopStockItem> generateStock(GameStateEntity gameState) {
        int stage = gameState.getCurrentStage();
        double stageFactor = 1.0 + 0.15 * (stage - 1); // 깊이 내려갈수록 물가 상승

        List<ShopStockItem> stock = new ArrayList<>();

        // 소모품 2종 랜덤
        pickRandom(consumableRepository.findAll(), 2).forEach(c ->
                stock.add(stockItem("CONSUMABLE", c.getConsumableId(), c.getName(), c.getDescription(),
                        scale(40 + c.getScoreCost(), stageFactor))));

        // 미보유 스킬 2종
        pickRandom(skillRepository.findBySkillNotOwner(gameState.getGameStateId()), 2).forEach(s ->
                stock.add(stockItem("SKILL", s.getSkillId(), s.getName(), s.getDescription(),
                        scale(PRICE_SKILL + s.getCost() / 2, stageFactor))));

        // 미보유 유물 2종 (희귀도별 가격)
        pickRandom(relicRepository.findByRelicNotOwner(gameState.getGameStateId()), 2).forEach(r ->
                stock.add(stockItem("RELIC", r.getRelicId(), r.getName(), r.getEffectDesc(),
                        scale(RELIC_PRICE.getOrDefault(r.getRarity(), 250), stageFactor))));

        // 장비 각 1종 (교체 방식)
        pickRandom(weaponRepository.findAll(), 1).forEach(w ->
                stock.add(stockItem("WEAPON", w.getWeaponId(), w.getName(), w.getDescription(),
                        scale(PRICE_WEAPON + w.getScoreCost() / 2, stageFactor))));
        pickRandom(armorRepository.findAll(), 1).forEach(a ->
                stock.add(stockItem("ARMOR", a.getArmorId(), a.getName(), a.getDescription(),
                        scale(PRICE_ARMOR + a.getScoreCost() / 2, stageFactor))));
        pickRandom(helmetRepository.findAll(), 1).forEach(h ->
                stock.add(stockItem("HELMET", h.getHelmetId(), h.getName(), h.getDescription(),
                        scale(PRICE_HELMET + h.getScoreCost() / 2, stageFactor))));

        return stock;
    }

    @Override
    @Transactional
    public void applyPurchase(GameStateEntity gameState, MemberEntity member, ShopStockItem stockItem) {
        // 골드 차감 (부족 시 예외)
        gameState.deductGold(stockItem.getPrice());
        gameStateRepository.save(gameState);

        switch (stockItem.getItemType()) {
            case "WEAPON" -> purchaseWeapon(gameState, member, stockItem.getItemId());
            case "ARMOR" -> purchaseArmor(gameState, member, stockItem.getItemId());
            case "HELMET" -> purchaseHelmet(gameState, member, stockItem.getItemId());
            case "SKILL" -> purchaseSkill(gameState, member, stockItem.getItemId());
            case "RELIC" -> purchaseRelic(gameState, member, stockItem.getItemId());
            case "CONSUMABLE" -> purchaseConsumable(gameState, member, stockItem.getItemId());
            default -> throw new IllegalArgumentException("알 수 없는 아이템 타입: " + stockItem.getItemType());
        }
        log.info("구매 완료 gameStateId={} type={} itemId={} price={}",
                gameState.getGameStateId(), stockItem.getItemType(), stockItem.getItemId(), stockItem.getPrice());
    }

    /* HELPER METHODS */

    private void purchaseWeapon(GameStateEntity gameState, MemberEntity member, Long weaponId) {
        GameEquipmentEntity equipment = findEquipment(gameState);
        WeaponEntity weapon = weaponRepository.findById(weaponId)
                .orElseThrow(() -> new NoSuchElementException("무기를 찾을 수 없습니다."));

        // 무기 교체 -> 공격력 재계산
        GameCharacterEntity character = findCharacter(gameState);
        int oldBonus = equipment.getWeapon() == null ? 0 : equipment.getWeapon().getAtkBonus();
        character.applyAttackDelta(weapon.getAtkBonus() - oldBonus);

        equipment.equipWeapon(weapon);
        gameEquipmentRepository.save(equipment);

        if (!collectionWeaponRepository.existsByMemberAndWeapon(member, weapon)) {
            collectionWeaponRepository.save(CollectionWeaponEntity.builder().member(member).weapon(weapon).build());
        }
    }

    private void purchaseArmor(GameStateEntity gameState, MemberEntity member, Long armorId) {
        GameEquipmentEntity equipment = findEquipment(gameState);
        ArmorEntity armor = armorRepository.findById(armorId)
                .orElseThrow(() -> new NoSuchElementException("갑옷을 찾을 수 없습니다."));

        GameCharacterEntity character = findCharacter(gameState);
        int oldBonus = equipment.getArmor() == null ? 0 : equipment.getArmor().getDefBonus();
        character.applyDefenseDelta(armor.getDefBonus() - oldBonus);

        equipment.equipArmor(armor);
        gameEquipmentRepository.save(equipment);

        if (!collectionArmorRepository.existsByMemberAndArmor(member, armor)) {
            collectionArmorRepository.save(CollectionArmorEntity.builder().member(member).armor(armor).build());
        }
    }

    private void purchaseHelmet(GameStateEntity gameState, MemberEntity member, Long helmetId) {
        GameEquipmentEntity equipment = findEquipment(gameState);
        HelmetEntity helmet = helmetRepository.findById(helmetId)
                .orElseThrow(() -> new NoSuchElementException("투구를 찾을 수 없습니다."));

        GameCharacterEntity character = findCharacter(gameState);
        int oldBonus = equipment.getHelmet() == null ? 0 : equipment.getHelmet().getHpBonus();
        character.applyMaxHpDelta(helmet.getHpBonus() - oldBonus);

        equipment.equipHelmet(helmet);
        gameEquipmentRepository.save(equipment);

        if (!collectionHelmetRepository.existsByMemberAndHelmet(member, helmet)) {
            collectionHelmetRepository.save(CollectionHelmetEntity.builder().member(member).helmet(helmet).build());
        }
    }

    private void purchaseSkill(GameStateEntity gameState, MemberEntity member, Long skillId) {
        List<GameSkillSlotEntity> currentSlots = gameSkillSlotRepository
                .findByGameState_GameStateIdOrderBySlotIndexAsc(gameState.getGameStateId());

        if (currentSlots.stream().anyMatch(slot -> slot.getSkill().getSkillId().equals(skillId))) {
            throw new IllegalStateException("이미 보유 중인 스킬입니다.");
        }
        if (currentSlots.size() >= 8) {
            throw new IllegalStateException("스킬 슬롯이 가득 찼습니다. (최대 8개)");
        }

        Set<Integer> usedIndexes = new HashSet<>();
        currentSlots.forEach(slot -> usedIndexes.add(slot.getSlotIndex()));
        int nextIndex = 0;
        while (usedIndexes.contains(nextIndex)) nextIndex++;

        SkillEntity skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new NoSuchElementException("스킬을 찾을 수 없습니다."));

        gameSkillSlotRepository.save(GameSkillSlotEntity.builder()
                .gameState(gameState).skill(skill).slotIndex(nextIndex).coolTime(0).build());

        if (!collectionSkillRepository.existsByMemberAndSkill(member, skill)) {
            collectionSkillRepository.save(CollectionSkillEntity.builder().member(member).skill(skill).build());
        }
    }

    private void purchaseRelic(GameStateEntity gameState, MemberEntity member, Long relicId) {
        boolean alreadyOwned = gameRelicInventoryRepository
                .findByGameState_GameStateId(gameState.getGameStateId()).stream()
                .anyMatch(r -> r.getRelic().getRelicId().equals(relicId));
        if (alreadyOwned) {
            throw new IllegalStateException("이미 보유 중인 유물입니다.");
        }

        RelicEntity relic = relicRepository.findById(relicId)
                .orElseThrow(() -> new NoSuchElementException("유물을 찾을 수 없습니다."));

        gameRelicInventoryRepository.save(GameRelicInventoryEntity.builder()
                .gameState(gameState).relic(relic).build());

        if (!collectionRelicRepository.existsByMemberAndRelic(member, relic)) {
            collectionRelicRepository.save(CollectionRelicEntity.builder().member(member).relic(relic).build());
        }
    }

    private void purchaseConsumable(GameStateEntity gameState, MemberEntity member, Long consumableId) {
        ConsumableEntity consumable = consumableRepository.findById(consumableId)
                .orElseThrow(() -> new NoSuchElementException("소모품을 찾을 수 없습니다."));

        gameConsumableInventoryRepository.save(GameConsumableInventoryEntity.builder()
                .gameState(gameState).consumable(consumable).build());

        if (!collectionConsumableRepository.existsByMemberAndConsumable(member, consumable)) {
            collectionConsumableRepository.save(CollectionConsumableEntity.builder().member(member).consumable(consumable).build());
        }
    }

    private GameEquipmentEntity findEquipment(GameStateEntity gameState) {
        return gameEquipmentRepository.findByGameState_GameStateId(gameState.getGameStateId())
                .stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("장비 슬롯을 찾을 수 없습니다."));
    }

    private GameCharacterEntity findCharacter(GameStateEntity gameState) {
        return gameState.getGameCharacter().stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("캐릭터 정보가 없습니다."));
    }

    private ShopStockItem stockItem(String type, Long id, String name, String description, int price) {
        ShopStockItem item = new ShopStockItem();
        item.setItemType(type);
        item.setItemId(id);
        item.setName(name);
        item.setDescription(description);
        item.setPrice(price);
        item.setSold(false);
        return item;
    }

    private int scale(int base, double factor) {
        return (int) Math.round(base * factor);
    }

    private <T> List<T> pickRandom(List<T> list, int count) {
        if (list.size() <= count) return new ArrayList<>(list);
        List<T> shuffled = new ArrayList<>(list);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, count);
    }
}
