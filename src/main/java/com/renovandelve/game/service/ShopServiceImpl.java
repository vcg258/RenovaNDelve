package com.renovandelve.game.service;

import com.renovandelve.global.exception.BusinessException;
import com.renovandelve.global.exception.ErrorCode;
import com.renovandelve.game.domain.GameEquipmentEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
import com.renovandelve.catalog.domain.consumableentity.ConsumableEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.catalog.domain.helmetentity.HelmetEntity;
import com.renovandelve.catalog.domain.Rarity;
import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import com.renovandelve.game.engine.Pact;
import com.renovandelve.game.engine.RarityTable;
import com.renovandelve.game.domain.GameRelicInventoryEntity;
import com.renovandelve.game.engine.RelicEffects;
import com.renovandelve.game.engine.ShopStockItem;
import com.renovandelve.catalog.repository.ArmorRepository;
import com.renovandelve.catalog.repository.ConsumableRepository;
import com.renovandelve.game.repository.GameConsumableInventoryRepository;
import com.renovandelve.game.repository.GameRelicInventoryRepository;
import com.renovandelve.catalog.repository.HelmetRepository;
import com.renovandelve.catalog.repository.RelicRepository;
import com.renovandelve.catalog.repository.SkillRepository;
import com.renovandelve.catalog.repository.WeaponRepository;
import org.springframework.transaction.annotation.Transactional;
import com.renovandelve.collection.service.CollectionService;
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
    private final GameRelicInventoryRepository gameRelicInventoryRepository;
    private final GameConsumableInventoryRepository gameConsumableInventoryRepository;
    private final SkillSlotService skillSlotService;
    private final CollectionService collectionService;

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
                * (Pact.parse(gameState.getPacts()).contains(Pact.GREED_TAX) ? 1.3 : 1.0) // 서약: 탐욕의 세금
                * (1.0 - relicDiscount(gameState)); // 유물: 상인의 반지

        List<ShopStockItem> stock = new ArrayList<>();

        // 소모품 2종 랜덤
        pickRandom(consumableRepository.findAll(), 2).forEach(c ->
                stock.add(stockItem("CONSUMABLE", c.getConsumableId(), c.getName(), c.getDescription(),
                        scale(40 + c.getScoreCost(), stageFactor))));

        // 스킬·유물·장비는 스테이지별 등급 확률로 고른다 (깊을수록 희귀·영웅이 잦다)
        // 미보유 스킬 2종
        RarityTable.pick(skillRepository.findBySkillNotOwner(gameState.getGameStateId()), SkillEntity::getRarity, 2, stage, random)
                .forEach(sk -> stock.add(withRarity(stockItem("SKILL", sk.getSkillId(), sk.getName(), sk.getDescription(),
                        scale(PRICE_SKILL + sk.getCost() / 2, stageFactor)), sk.getRarity())));

        // 미보유 유물 2종 (희귀도별 가격)
        RarityTable.pick(relicRepository.findByRelicNotOwner(gameState.getGameStateId()), RelicEntity::getRarity, 2, stage, random)
                .forEach(r -> stock.add(withRarity(stockItem("RELIC", r.getRelicId(), r.getName(), r.getEffectDesc(),
                        scale(RELIC_PRICE.getOrDefault(r.getRarity(), 250), stageFactor)), r.getRarity())));

        // 장비 각 1종 (교체 방식). 지금 착용 중인 장비는 사도 달라지는 게 없으니 진열하지 않는다
        GameEquipmentEntity equipped = gameState.getGameEquipment();
        Long wornWeapon = equipped == null || equipped.getWeapon() == null ? null : equipped.getWeapon().getWeaponId();
        Long wornArmor = equipped == null || equipped.getArmor() == null ? null : equipped.getArmor().getArmorId();
        Long wornHelmet = equipped == null || equipped.getHelmet() == null ? null : equipped.getHelmet().getHelmetId();

        RarityTable.pick(weaponRepository.findAll().stream().filter(w -> !w.getWeaponId().equals(wornWeapon)).toList(),
                WeaponEntity::getRarity, 1, stage, random).forEach(w ->
                stock.add(withRarity(equipmentItem("WEAPON", w.getWeaponId(), w.getName(), w.getDescription(),
                        scale(PRICE_WEAPON + w.getScoreCost() / 2, stageFactor), w.getAtkBonus()), w.getRarity())));
        RarityTable.pick(armorRepository.findAll().stream().filter(a -> !a.getArmorId().equals(wornArmor)).toList(),
                ArmorEntity::getRarity, 1, stage, random).forEach(a ->
                stock.add(withRarity(equipmentItem("ARMOR", a.getArmorId(), a.getName(), a.getDescription(),
                        scale(PRICE_ARMOR + a.getScoreCost() / 2, stageFactor), a.getDefBonus()), a.getRarity())));
        RarityTable.pick(helmetRepository.findAll().stream().filter(h -> !h.getHelmetId().equals(wornHelmet)).toList(),
                HelmetEntity::getRarity, 1, stage, random).forEach(h ->
                stock.add(withRarity(equipmentItem("HELMET", h.getHelmetId(), h.getName(), h.getDescription(),
                        scale(PRICE_HELMET + h.getScoreCost() / 2, stageFactor), h.getHpBonus()), h.getRarity())));

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
     * @throws BusinessException 슬롯이 가득 찼는데 버릴 슬롯이 없으면, 골드가 부족하면
     */
    @Override
    @Transactional
    public void applyPurchase(GameStateEntity gameState, MemberEntity member, ShopStockItem stockItem,
                              Long replaceSlotId) {
        // 스킬 슬롯이 가득 찼는데 버릴 스킬을 고르지 않았다면 골드를 건드리기 전에 거부한다
        if ("SKILL".equals(stockItem.getItemType()) && replaceSlotId == null
                && gameState.getGameSkillSlot() != null
                && gameState.getGameSkillSlot().size() >= SkillSlotService.MAX_SLOTS) {
            throw new BusinessException(ErrorCode.SKILL_SLOT_FULL, SkillSlotService.MAX_SLOTS);
        }
        // 골드 차감 (부족 시 예외)
        gameState.deductGold(stockItem.getPrice()); // 같은 트랜잭션에서 읽은 엔티티라 변경 감지로 반영된다

        switch (stockItem.getItemType()) {
            case "WEAPON" -> purchaseWeapon(gameState, member, stockItem.getItemId());
            case "ARMOR" -> purchaseArmor(gameState, member, stockItem.getItemId());
            case "HELMET" -> purchaseHelmet(gameState, member, stockItem.getItemId());
            case "SKILL" -> purchaseSkill(gameState, member, stockItem.getItemId(), replaceSlotId);
            case "RELIC" -> purchaseRelic(gameState, member, stockItem.getItemId());
            case "CONSUMABLE" -> purchaseConsumable(gameState, member, stockItem.getItemId());
            default -> throw new IllegalStateException("알 수 없는 아이템 타입: " + stockItem.getItemType()); // 진열품은 서버가 만든다
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
        WeaponEntity weapon = weaponRepository.findById(weaponId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND, "무기를 찾을 수 없습니다."));

        // 장비 교체와 능력치 재계산은 루트가 맡는다. 관리 중인 엔티티라 커밋 때 UPDATE된다
        gameState.equipWeapon(weapon);

        collectionService.registerWeapon(member, weapon);
    }

    /**
     * 갑옷를 사서 바로 장착한다. 이전 갑옷의 보너스를 빼고 새 보너스를 더해 방어력을 다시 계산하고 도감에 등록한다.
     *
     * @param gameState 게임
     * @param member    회원 (도감 등록용)
     * @param armorId   갑옷 ID
     */
    private void purchaseArmor(GameStateEntity gameState, MemberEntity member, Long armorId) {
        ArmorEntity armor = armorRepository.findById(armorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND, "갑옷을 찾을 수 없습니다."));

        // 장비 교체와 능력치 재계산은 루트가 맡는다. 관리 중인 엔티티라 커밋 때 UPDATE된다
        gameState.equipArmor(armor);

        collectionService.registerArmor(member, armor);
    }

    /**
     * 투구를 사서 바로 장착한다. 이전 투구의 보너스를 빼고 새 보너스를 더해 최대 HP을 다시 계산하고 도감에 등록한다.
     *
     * @param gameState 게임
     * @param member    회원 (도감 등록용)
     * @param helmetId  투구 ID
     */
    private void purchaseHelmet(GameStateEntity gameState, MemberEntity member, Long helmetId) {
        HelmetEntity helmet = helmetRepository.findById(helmetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND, "투구를 찾을 수 없습니다."));

        // 장비 교체와 능력치 재계산은 루트가 맡는다. 관리 중인 엔티티라 커밋 때 UPDATE된다
        gameState.equipHelmet(helmet);

        collectionService.registerHelmet(member, helmet);
    }

    /**
     * 스킬을 배운다 (도감 등록 포함).
     *
     * @param gameState     게임
     * @param member        회원
     * @param skillId       스킬 ID
     * @param replaceSlotId 버릴 스킬 슬롯 (없으면 null)
     */
    private void purchaseSkill(GameStateEntity gameState, MemberEntity member, Long skillId, Long replaceSlotId) {
        // 도감 등록은 learn이 배우는 순간 한다
        skillSlotService.learn(gameState, skillId, replaceSlotId);
    }

    /**
     * 유물을 얻고 도감에 등록한다.
     *
     * @param gameState 게임
     * @param member    회원
     * @param relicId   유물 ID
     * @throws BusinessException 이미 가진 유물이면
     */
    private void purchaseRelic(GameStateEntity gameState, MemberEntity member, Long relicId) {
        if (gameState.ownsRelic(relicId)) {
            throw new BusinessException(ErrorCode.RELIC_ALREADY_OWNED);
        }

        RelicEntity relic = relicRepository.findById(relicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND, "유물을 찾을 수 없습니다."));

        // cascade로도 저장되지만, 구매 응답에 담을 칸 ID를 지금 받기 위해 바로 저장한다
        gameRelicInventoryRepository.save(gameState.addRelic(relic));

        collectionService.registerRelic(member, relic);
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
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND, "소모품을 찾을 수 없습니다."));

        gameConsumableInventoryRepository.save(gameState.addConsumable(consumable));

        collectionService.registerConsumable(member, consumable);
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
     * 장비 진열품. 착용 중인 장비와 비교해 보여 줄 수 있게 보너스를 함께 담는다.
     *
     * @param type        WEAPON / ARMOR / HELMET
     * @param id          장비 ID
     * @param name        이름
     * @param description 설명
     * @param price       가격
     * @param bonus       능력치 보너스
     * @return 진열품
     */
    private ShopStockItem equipmentItem(String type, Long id, String name, String description, int price, int bonus) {
        ShopStockItem item = stockItem(type, id, name, description, price);
        item.setBonus(bonus);
        return item;
    }

    /**
     * 보유 유물의 상점 할인율.
     *
     * @param gameState 게임
     * @return 할인율 (0 ~ {@link RelicEffects#MAX_SHOP_DISCOUNT})
     */
    private static double relicDiscount(GameStateEntity gameState) {
        List<GameRelicInventoryEntity> inventory = gameState.getGameRelicInventory();
        if (inventory == null || inventory.isEmpty()) return 0.0;
        return RelicEffects.of(inventory.stream().map(i -> i.getRelic().getName()).toList()).getShopDiscount();
    }

    /**
     * 진열품에 등급을 붙인다.
     *
     * @param item   진열품
     * @param rarity 등급
     * @return 같은 진열품
     */
    private static ShopStockItem withRarity(ShopStockItem item, Rarity rarity) {
        item.setRarity(rarity);
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
