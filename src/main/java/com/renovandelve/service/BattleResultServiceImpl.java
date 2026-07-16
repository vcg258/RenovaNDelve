package com.renovandelve.service;

import com.renovandelve.domain.*;
import com.renovandelve.domain.armorentity.ArmorEntity;
import com.renovandelve.domain.consumableentity.ConsumableEntity;
import com.renovandelve.domain.gamestateentity.Difficulty;
import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.domain.helmetentity.HelmetEntity;
import com.renovandelve.domain.relicentity.RelicEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.domain.weaponentity.WeaponEntity;
import com.renovandelve.dto.*;
import com.renovandelve.dto.response.IllustratedEncyclopediaDTO;
import com.renovandelve.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Log4j2
@RequiredArgsConstructor
public class BattleResultServiceImpl implements BattleResultService {

    private final CollectionSkillRepository collectionSkillRepository;
    private final CollectionWeaponRepository collectionWeaponRepository;
    private final CollectionArmorRepository collectionArmorRepository;
    private final CollectionHelmetRepository collectionHelmetRepository;
    private final CollectionRelicRepository collectionRelicRepository;
    private final CollectionConsumableRepository collectionConsumableRepository;
    private final RankingRepository rankingRepository;
    private final GameStateRepository gameStateRepository;

    @Override
    @Transactional
    public IllustratedEncyclopediaDTO registerCollection(GameStateEntity gameState) {
        MemberEntity member = gameState.getMember();

        // 스킬 도감 등록
        List<CollectionSkillDTO> skillList = gameState.getGameSkillSlot().stream()
                .map(GameSkillSlotEntity::getSkill)
                .filter(Objects::nonNull)
                .filter(skill -> !collectionSkillRepository.existsByMemberAndSkill(member, skill))
                .map(skill -> {
                    CollectionSkillEntity saved = collectionSkillRepository.save(
                            CollectionSkillEntity.builder().member(member).skill(skill).build());
                    return new CollectionSkillDTO(saved.getCollectionId(), saved.getAcquiredAt(), toMemberDTO(member), toSkillDTO(skill));
                }).toList();

        // 소모품 도감 등록
        List<CollectionConsumableDTO> consumableList = gameState.getGameConsumableInventory().stream()
                .map(GameConsumableInventoryEntity::getConsumable)
                .filter(Objects::nonNull)
                .distinct()
                .filter(consumable -> !collectionConsumableRepository.existsByMemberAndConsumable(member, consumable))
                .map(consumable -> {
                    CollectionConsumableEntity saved = collectionConsumableRepository.save(
                            CollectionConsumableEntity.builder().member(member).consumable(consumable).build());
                    return new CollectionConsumableDTO(saved.getCollectionId(), saved.getAcquiredAt(), toMemberDTO(member), toConsumableDTO(consumable));
                }).toList();

        // 장비 도감 등록
        GameEquipmentEntity equipment = gameState.getGameEquipment();
        List<CollectionWeaponDTO> weaponList = new ArrayList<>();
        List<CollectionArmorDTO> armorList = new ArrayList<>();
        List<CollectionHelmetDTO> helmetList = new ArrayList<>();

        if (equipment != null) {
            if (equipment.getWeapon() != null && !collectionWeaponRepository.existsByMemberAndWeapon(member, equipment.getWeapon())) {
                CollectionWeaponEntity saved = collectionWeaponRepository.save(
                        CollectionWeaponEntity.builder().member(member).weapon(equipment.getWeapon()).build());
                weaponList.add(new CollectionWeaponDTO(saved.getCollectionId(), saved.getAcquiredAt(), toMemberDTO(member), toWeaponDTO(equipment.getWeapon())));
            }
            if (equipment.getArmor() != null && !collectionArmorRepository.existsByMemberAndArmor(member, equipment.getArmor())) {
                CollectionArmorEntity saved = collectionArmorRepository.save(
                        CollectionArmorEntity.builder().member(member).armor(equipment.getArmor()).build());
                armorList.add(new CollectionArmorDTO(saved.getCollectionId(), saved.getAcquiredAt(), toMemberDTO(member), toArmorDTO(equipment.getArmor())));
            }
            if (equipment.getHelmet() != null && !collectionHelmetRepository.existsByMemberAndHelmet(member, equipment.getHelmet())) {
                CollectionHelmetEntity saved = collectionHelmetRepository.save(
                        CollectionHelmetEntity.builder().member(member).helmet(equipment.getHelmet()).build());
                helmetList.add(new CollectionHelmetDTO(saved.getCollectionId(), saved.getAcquiredAt(), toMemberDTO(member), toHelmetDTO(equipment.getHelmet())));
            }
        }

        // 유물 도감 등록
        List<CollectionRelicDTO> relicList = gameState.getGameRelicInventory().stream()
                .map(GameRelicInventoryEntity::getRelic)
                .filter(Objects::nonNull)
                .distinct()
                .filter(relic -> !collectionRelicRepository.existsByMemberAndRelic(member, relic))
                .map(relic -> {
                    CollectionRelicEntity saved = collectionRelicRepository.save(
                            CollectionRelicEntity.builder().member(member).relic(relic).build());
                    return new CollectionRelicDTO(saved.getCollectionId(), saved.getAcquiredAt(), toMemberDTO(member), toRelicDTO(relic));
                }).toList();

        log.info("[도감 등록] skills={} consumables={} weapons={} armors={} helmets={} relics={}",
                skillList.size(), consumableList.size(), weaponList.size(), armorList.size(), helmetList.size(), relicList.size());

        return new IllustratedEncyclopediaDTO(armorList, consumableList, helmetList, relicList, skillList, weaponList);
    }

    @Override
    @Transactional
    public RankingDTO updateRanking(GameStateEntity gameState, Integer clearTime) {
        if (gameState.getDifficulty() == Difficulty.EASY) {
            log.info("EASY 난이도는 랭킹에 등록하지 않음");
            return null;
        }

        MemberEntity member = gameState.getMember();
        Difficulty difficulty = gameState.getDifficulty();
        int newScore = gameState.getScore();

        RankingEntity ranking = rankingRepository.findByMemberAndDifficulty(member, difficulty)
                .map(rank -> {
                    if (newScore <= rank.getBestScore()) {
                        log.info("랭킹 미갱신 기존={} 신규={}", rank.getBestScore(), newScore);
                        return rank;
                    }
                    return RankingEntity.builder()
                            .rankingId(rank.getRankingId())
                            .difficulty(difficulty)
                            .bestScore(newScore)
                            .clearTime(clearTime)
                            .member(member)
                            .gameState(gameState)
                            .build();
                })
                .orElseGet(() -> RankingEntity.builder()
                        .difficulty(difficulty)
                        .bestScore(newScore)
                        .clearTime(clearTime)
                        .member(member)
                        .gameState(gameState)
                        .build());

        RankingEntity saved = rankingRepository.save(ranking);
        log.info("랭킹 갱신 rankId={} score={}", saved.getRankingId(), saved.getBestScore());

        return new RankingDTO(saved.getRankingId(), saved.getDifficulty(), saved.getBestScore(),
                saved.getClearTime(), saved.getAchievedAt(), toMemberDTO(member), toGameStateDTO(gameState));
    }

    @Override
    @Transactional
    public IllustratedEncyclopediaDTO terminateGame(GameStateEntity gameState, boolean cleared) {
        IllustratedEncyclopediaDTO encyclopedia = registerCollection(gameState);

        Integer clearTime = null;
        if (cleared && gameState.getCreatedAt() != null) {
            clearTime = (int) Duration.between(gameState.getCreatedAt(), LocalDateTime.now()).getSeconds();
        }
        updateRanking(gameState, clearTime);

        gameState.deactivate();
        gameStateRepository.save(gameState);
        log.info("게임 세션 종료 gameStateId={} cleared={}", gameState.getGameStateId(), cleared);

        return encyclopedia;
    }

    /* HELPER METHODS */

    private MemberDTO toMemberDTO(MemberEntity m) {
        return MemberDTO.builder().memberId(m.getMemberId()).loginId(m.getLoginId()).build();
    }

    private SkillDTO toSkillDTO(SkillEntity s) {
        return new SkillDTO(s.getName(), s.getTarget(), s.getAttackCount(), s.getCoefficient(), s.getCost(),
                s.getCooldown(), s.getStatusEffect(), s.getEffectStack(), s.getDescription());
    }

    private RelicDTO toRelicDTO(RelicEntity r) {
        return new RelicDTO(r.getRelicId(), r.getName(), r.getEffectDesc(), r.getRarity(), r.getScoreCost());
    }

    private ConsumableDTO toConsumableDTO(ConsumableEntity c) {
        return new ConsumableDTO(c.getConsumableId(), c.getName(), c.getEffectType(), c.getEffectValue(), c.getScoreCost(), c.getDescription());
    }

    private WeaponDTO toWeaponDTO(WeaponEntity w) {
        return new WeaponDTO(w.getWeaponId(), w.getName(), w.getType(), w.getAtkBonus(), w.getSpecialEffect(), w.getScoreCost(), w.getDescription());
    }

    private ArmorDTO toArmorDTO(ArmorEntity a) {
        return new ArmorDTO(a.getArmorId(), a.getName(), a.getType(), a.getDefBonus(), a.getSpecialEffect(), a.getScoreCost(), a.getDescription());
    }

    private HelmetDTO toHelmetDTO(HelmetEntity h) {
        return new HelmetDTO(h.getHelmetId(), h.getName(), h.getType(), h.getHpBonus(), h.getSpecialEffect(), h.getScoreCost(), h.getDescription());
    }

    private GameStateDTO toGameStateDTO(GameStateEntity g) {
        return GameStateDTO.builder()
                .gameStateId(g.getGameStateId())
                .difficulty(g.getDifficulty())
                .isActive(g.getIsActive())
                .currentStage(g.getCurrentStage())
                .currentNodeId(g.getCurrentNodeId())
                .score(g.getScore())
                .gold(g.getGold())
                .startingPoints(g.getStartingPoints())
                .hasPhoenix(g.getHasPhoenix())
                .createdAt(g.getCreatedAt())
                .updatedAt(g.getUpdatedAt())
                .build();
    }
}
