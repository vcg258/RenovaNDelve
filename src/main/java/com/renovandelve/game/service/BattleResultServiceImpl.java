package com.renovandelve.game.service;

import com.renovandelve.ranking.mapper.RankingMapper;
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
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.ranking.repository.RankingRepository;
import org.springframework.transaction.annotation.Transactional;
import com.renovandelve.collection.service.CollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class BattleResultServiceImpl implements BattleResultService {

    private final CollectionService collectionService;
    private final RankingRepository rankingRepository;
    private final GameStateRepository gameStateRepository;
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
                .map(skill -> collectionService.registerSkill(member, skill))
                .flatMap(Optional::stream)
                .toList();

        // 소모품 도감 등록
        List<CollectionConsumableDTO> consumableList = gameState.getGameConsumableInventory().stream()
                .map(GameConsumableInventoryEntity::getConsumable)
                .filter(Objects::nonNull)
                .distinct()
                .map(consumable -> collectionService.registerConsumable(member, consumable))
                .flatMap(Optional::stream)
                .toList();

        // 장비 도감 등록
        GameEquipmentEntity equipment = gameState.getGameEquipment();
        List<CollectionWeaponDTO> weaponList = new ArrayList<>();
        List<CollectionArmorDTO> armorList = new ArrayList<>();
        List<CollectionHelmetDTO> helmetList = new ArrayList<>();
        if (equipment != null) {
            collectionService.registerWeapon(member, equipment.getWeapon()).ifPresent(weaponList::add);
            collectionService.registerArmor(member, equipment.getArmor()).ifPresent(armorList::add);
            collectionService.registerHelmet(member, equipment.getHelmet()).ifPresent(helmetList::add);
        }

        // 유물 도감 등록
        List<CollectionRelicDTO> relicList = gameState.getGameRelicInventory().stream()
                .map(GameRelicInventoryEntity::getRelic)
                .filter(Objects::nonNull)
                .distinct()
                .map(relic -> collectionService.registerRelic(member, relic))
                .flatMap(Optional::stream)
                .toList();

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

        Optional<RankingEntity> existing = rankingRepository.findByMemberAndDifficulty(
                gameState.getMember(), gameState.getDifficulty());
        RankingEntity ranking;
        if (existing.isPresent()) {
            ranking = existing.get();
            // 관리 중인 엔티티라 바꾸기만 하면 커밋 때 UPDATE된다 (변경 감지)
            if (!ranking.renewIfBetter(gameState, clearTime)) {
                log.info("랭킹 미갱신 기존={} 신규={}", ranking.getBestScore(), gameState.getScore());
            }
        } else {
            ranking = rankingRepository.save(RankingEntity.first(gameState, clearTime));
        }
        log.info("랭킹 기록 rankId={} score={}", ranking.getRankingId(), ranking.getBestScore());

        return rankingMapper.toDTO(ranking);
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
