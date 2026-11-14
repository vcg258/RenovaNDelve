package com.renovandelve.game.service;

import com.renovandelve.collection.domain.CollectionArmorEntity;
import com.renovandelve.collection.domain.CollectionConsumableEntity;
import com.renovandelve.collection.domain.CollectionHelmetEntity;
import com.renovandelve.collection.domain.CollectionRelicEntity;
import com.renovandelve.collection.domain.CollectionSkillEntity;
import com.renovandelve.collection.domain.CollectionWeaponEntity;
import com.renovandelve.game.domain.GameCharacterEntity;
import com.renovandelve.game.domain.GameEquipmentEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
import com.renovandelve.catalog.domain.consumableentity.ConsumableEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.catalog.domain.helmetentity.HelmetEntity;
import com.renovandelve.catalog.domain.relicentity.Rarity;
import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import com.renovandelve.game.engine.Pact;
import com.renovandelve.game.engine.ShopStockItem;
import com.renovandelve.catalog.repository.ArmorRepository;
import com.renovandelve.collection.repository.CollectionArmorRepository;
import com.renovandelve.collection.repository.CollectionConsumableRepository;
import com.renovandelve.collection.repository.CollectionHelmetRepository;
import com.renovandelve.collection.repository.CollectionRelicRepository;
import com.renovandelve.collection.repository.CollectionSkillRepository;
import com.renovandelve.collection.repository.CollectionWeaponRepository;
import com.renovandelve.catalog.repository.ConsumableRepository;
import com.renovandelve.game.repository.GameConsumableInventoryRepository;
import com.renovandelve.game.repository.GameEquipmentRepository;
import com.renovandelve.game.repository.GameRelicInventoryRepository;
import com.renovandelve.game.repository.GameSkillSlotRepository;
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.catalog.repository.HelmetRepository;
import com.renovandelve.catalog.repository.RelicRepository;
import com.renovandelve.catalog.repository.SkillRepository;
import com.renovandelve.catalog.repository.WeaponRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
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
    private final SkillSlotService skillSlotService;

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

    /**
     * 상점 진열품을 뽑는다: 소모품 2, 스킬 1, 유물 1, 장비 1. 깊이 내려갈수록, 탐욕의 세금 서약이면 값이 오른다.
     *
     * @param gameState 게임
     * @return 진열품
     */
    @Override
    public List<ShopStockItem> generateStock(GameStateEntity gameState) {
        int stage = gameState.getCurrentStage();
        double stageFactor = (1.0 + 0.15 * (stage - 1)) // 깊이 내려갈수록 물가 상승
                * (Pact.parse(gameState.getPacts()).contains(Pact.GREED_TAX) ? 1.3 : 1.0); // 서약: 탐욕의 세금

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

    /**
     * 구매를 적용한다 (스킬 교체 없음).
     */
    @Override
    @Transactional
    public void applyPurchase(GameStateEntity gameState, MemberEntity member, ShopStockItem stockItem) {
        applyPurchase(gameState, member, stockItem, null);
    }

    /**
     * 구매를 적용한다. 스킬이 가득 찼는데 버릴 슬롯이 없으면 골드를 건드리기 전에 거부한다.
     *
     * @param gameState     게임
     * @param member        회원 (도감 등록용)
     * @param stockItem     산 진열품
     * @param replaceSlotId 버릴 스킬 슬롯 (없으면 null)
     * @throws IllegalStateException 슬롯이 가득 찼는데 버릴 슬롯이 없으면
     * @throws IllegalArgumentException 골드가 부족하면
     */
    @Override
    @Transactional
    public void applyPurchase(GameStateEntity gameState, MemberEntity member, ShopStockItem stockItem,
                              Long replaceSlotId) {
        // 스킬 슬롯이 가득 찼는데 버릴 스킬을 고르지 않았다면 골드를 건드리기 전에 거부한다
        if ("SKILL".equals(stockItem.getItemType()) && replaceSlotId == null
                && gameState.getGameSkillSlot() != null
                && gameState.getGameSkillSlot().size() >= SkillSlotService.MAX_SLOTS) {
            throw new IllegalStateException(SkillSlotService.FULL_MESSAGE);
        }
        // 골드 차감 (부족 시 예외)
        gameState.deductGold(stockItem.getPrice());
        gameStateRepository.save(gameState);

        switch (stockItem.getItemType()) {
            case "WEAPON" -> purchaseWeapon(gameState, member, stockItem.getItemId());
            case "ARMOR" -> purchaseArmor(gameState, member, stockItem.getItemId());
            case "HELMET" -> purchaseHelmet(gameState, member, stockItem.getItemId());
            case "SKILL" -> purchaseSkill(gameState, member, stockItem.getItemId(), replaceSlotId);
            case "RELIC" -> purchaseRelic(gameState, member, stockItem.getItemId());
            case "CONSUMABLE" -> purchaseConsumable(gameState, member, stockItem.getItemId());
            default -> throw new IllegalArgumentException("알 수 없는 아이템 타입: " + stockItem.getItemType());
        }
        log.info("구매 완료 gameStateId={} type={} itemId={} price={}",
                gameState.getGameStateId(), stockItem.getItemType(), stockItem.getItemId(), stockItem.getPrice());
    }

    /* HELPER METHODS */

    /**
     * 무기를 사서 바로 장착한다. 이전 무기의 보너스를 빼고 새 보너스를 더해 공격력을 다시 계산하고 도감에 등록한다.
     *
     * @param gameState 게임
     * @param member    회원 (도감 등록용)
     * @param weaponId  무기 ID
     */
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

    /**
     * 갑옷를 사서 바로 장착한다. 이전 갑옷의 보너스를 빼고 새 보너스를 더해 방어력을 다시 계산하고 도감에 등록한다.
     *
     * @param gameState 게임
     * @param member    회원 (도감 등록용)
     * @param armorId   갑옷 ID
     */
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

    /**
     * 투구를 사서 바로 장착한다. 이전 투구의 보너스를 빼고 새 보너스를 더해 최대 HP을 다시 계산하고 도감에 등록한다.
     *
     * @param gameState 게임
     * @param member    회원 (도감 등록용)
     * @param helmetId  투구 ID
     */
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

    /**
     * 스킬을 배우고 도감에 등록한다.
     *
     * @param gameState     게임
     * @param member        회원
     * @param skillId       스킬 ID
     * @param replaceSlotId 버릴 스킬 슬롯 (없으면 null)
     */
    private void purchaseSkill(GameStateEntity gameState, MemberEntity member, Long skillId, Long replaceSlotId) {
        SkillEntity skill = skillSlotService.learn(gameState, skillId, replaceSlotId);

        if (!collectionSkillRepository.existsByMemberAndSkill(member, skill)) {
            collectionSkillRepository.save(CollectionSkillEntity.builder().member(member).skill(skill).build());
        }
    }

    /**
     * 유물을 얻고 도감에 등록한다.
     *
     * @param gameState 게임
     * @param member    회원
     * @param relicId   유물 ID
     * @throws IllegalStateException 이미 가진 유물이면
     */
    private void purchaseRelic(GameStateEntity gameState, MemberEntity member, Long relicId) {
        if (gameState.ownsRelic(relicId)) {
            throw new IllegalStateException("이미 보유 중인 유물입니다.");
        }

        RelicEntity relic = relicRepository.findById(relicId)
                .orElseThrow(() -> new NoSuchElementException("유물을 찾을 수 없습니다."));

        // cascade로도 저장되지만, 구매 응답에 담을 칸 ID를 지금 받기 위해 바로 저장한다
        gameRelicInventoryRepository.save(gameState.addRelic(relic));

        if (!collectionRelicRepository.existsByMemberAndRelic(member, relic)) {
            collectionRelicRepository.save(CollectionRelicEntity.builder().member(member).relic(relic).build());
        }
    }

    /**
     * 소모품을 얻고 도감에 등록한다.
     *
     * @param gameState    게임
     * @param member       회원
     * @param consumableId 소모품 ID
     */
    private void purchaseConsumable(GameStateEntity gameState, MemberEntity member, Long consumableId) {
        ConsumableEntity consumable = consumableRepository.findById(consumableId)
                .orElseThrow(() -> new NoSuchElementException("소모품을 찾을 수 없습니다."));

        gameConsumableInventoryRepository.save(gameState.addConsumable(consumable));

        if (!collectionConsumableRepository.existsByMemberAndConsumable(member, consumable)) {
            collectionConsumableRepository.save(CollectionConsumableEntity.builder().member(member).consumable(consumable).build());
        }
    }

    /**
     * 게임의 장비 슬롯.
     *
     * @param gameState 게임
     * @return 장비 슬롯
     */
    private GameEquipmentEntity findEquipment(GameStateEntity gameState) {
        return gameEquipmentRepository.findByGameState_GameStateId(gameState.getGameStateId())
                .stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("장비 슬롯을 찾을 수 없습니다."));
    }

    /**
     * 게임의 캐릭터.
     *
     * @param gameState 게임
     * @return 캐릭터
     */
    private GameCharacterEntity findCharacter(GameStateEntity gameState) {
        return gameState.getGameCharacter().stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("캐릭터 정보가 없습니다."));
    }

    /**
     * 진열품 한 칸을 만든다.
     *
     * @param type        품목 종류
     * @param id          품목 ID
     * @param name        이름
     * @param description 설명
     * @param price       가격
     * @return 진열품
     */
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

    /**
     * 기준 가격에 배율을 곱해 반올림한다.
     *
     * @param base   기준 가격
     * @param factor 배율
     * @return 가격
     */
    private int scale(int base, double factor) {
        return (int) Math.round(base * factor);
    }

    /**
     * 목록에서 무작위로 몇 개 고른다.
     *
     * @param list  후보
     * @param count 고를 수
     * @param <T>   항목 타입
     * @return 고른 항목 (후보가 적으면 전부)
     */
    private <T> List<T> pickRandom(List<T> list, int count) {
        if (list.size() <= count) return new ArrayList<>(list);
        List<T> shuffled = new ArrayList<>(list);
        Collections.shuffle(shuffled, random);
        return shuffled.subList(0, count);
    }
}
