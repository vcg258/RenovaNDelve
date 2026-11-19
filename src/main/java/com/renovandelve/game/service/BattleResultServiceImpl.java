package com.renovandelve.game.service;

import com.renovandelve.ranking.mapper.RankingMapper;
import com.renovandelve.collection.mapper.CollectionMapper;
import com.renovandelve.collection.domain.CollectionArmorEntity;
import com.renovandelve.collection.domain.CollectionConsumableEntity;
import com.renovandelve.collection.domain.CollectionHelmetEntity;
import com.renovandelve.collection.domain.CollectionRelicEntity;
import com.renovandelve.collection.domain.CollectionSkillEntity;
import com.renovandelve.collection.domain.CollectionWeaponEntity;
import com.renovandelve.game.domain.GameConsumableInventoryEntity;
import com.renovandelve.game.domain.GameEquipmentEntity;
import com.renovandelve.game.domain.GameRelicInventoryEntity;
import com.renovandelve.game.domain.GameSkillSlotEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.ranking.domain.RankingEntity;
import com.renovandelve.game.domain.gamestateentity.Difficulty;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.collection.dto.CollectionArmorDTO;
import com.renovandelve.collection.dto.CollectionConsumableDTO;
import com.renovandelve.collection.dto.CollectionHelmetDTO;
import com.renovandelve.collection.dto.CollectionRelicDTO;
import com.renovandelve.collection.dto.CollectionSkillDTO;
import com.renovandelve.collection.dto.CollectionWeaponDTO;
import com.renovandelve.ranking.dto.RankingDTO;
import com.renovandelve.collection.dto.response.IllustratedEncyclopediaDTO;
import com.renovandelve.collection.repository.CollectionArmorRepository;
import com.renovandelve.collection.repository.CollectionConsumableRepository;
import com.renovandelve.collection.repository.CollectionHelmetRepository;
import com.renovandelve.collection.repository.CollectionRelicRepository;
import com.renovandelve.collection.repository.CollectionSkillRepository;
import com.renovandelve.collection.repository.CollectionWeaponRepository;
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.ranking.repository.RankingRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
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
    private final CollectionMapper collectionMapper;
    private final RankingMapper rankingMapper;

    /**
     * 이번 판에 손에 넣은 스킬·유물·소모품·장비를 회원 도감에 등록한다. 이미 등록된 것은 건너뛴다.
     *
     * @param gameState 끝난 게임
     * @return 이번에 새로 등록된 항목
     */
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
                    return collectionMapper.toDTO(saved);
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
                    return collectionMapper.toDTO(saved);
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
                weaponList.add(collectionMapper.toDTO(saved));
            }
            if (equipment.getArmor() != null && !collectionArmorRepository.existsByMemberAndArmor(member, equipment.getArmor())) {
                CollectionArmorEntity saved = collectionArmorRepository.save(
                        CollectionArmorEntity.builder().member(member).armor(equipment.getArmor()).build());
                armorList.add(collectionMapper.toDTO(saved));
            }
            if (equipment.getHelmet() != null && !collectionHelmetRepository.existsByMemberAndHelmet(member, equipment.getHelmet())) {
                CollectionHelmetEntity saved = collectionHelmetRepository.save(
                        CollectionHelmetEntity.builder().member(member).helmet(equipment.getHelmet()).build());
                helmetList.add(collectionMapper.toDTO(saved));
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
                    return collectionMapper.toDTO(saved);
                }).toList();

        log.info("[도감 등록] skills={} consumables={} weapons={} armors={} helmets={} relics={}",
                skillList.size(), consumableList.size(), weaponList.size(), armorList.size(), helmetList.size(), relicList.size());

        return new IllustratedEncyclopediaDTO(armorList, consumableList, helmetList, relicList, skillList, weaponList);
    }

    /**
     * 난이도별 랭킹에 기록한다. 쉬움 난이도는 등록하지 않고, 기존 기록보다 높을 때만 바꾼다.
     *
     * @param gameState 끝난 게임
     * @param clearTime 클리어까지 걸린 초 (클리어하지 못했으면 null)
     * @return 랭킹 기록, 등록하지 않았으면 null
     */
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

        return rankingMapper.toDTO(saved);
    }

    /**
     * 게임을 끝낸다: 도감 등록, 랭킹 기록, 결과(CLEAR/DEFEAT) 저장.
     *
     * @param gameState 끝낼 게임
     * @param cleared   클리어했는지
     * @return 이번에 새로 등록된 도감 항목
     */
    @Override
    @Transactional
    public IllustratedEncyclopediaDTO terminateGame(GameStateEntity gameState, boolean cleared) {
        IllustratedEncyclopediaDTO encyclopedia = registerCollection(gameState);

        Integer clearTime = null;
        if (cleared && gameState.getCreatedAt() != null) {
            clearTime = (int) Duration.between(gameState.getCreatedAt(), LocalDateTime.now()).getSeconds();
        }
        updateRanking(gameState, clearTime);

        gameState.finish(cleared);
        gameStateRepository.save(gameState);
        log.info("게임 세션 종료 gameStateId={} cleared={}", gameState.getGameStateId(), cleared);

        return encyclopedia;
    }
}
