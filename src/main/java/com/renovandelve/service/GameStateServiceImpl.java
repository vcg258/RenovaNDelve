package com.renovandelve.service;

import com.renovandelve.config.RecordMapper;
import com.renovandelve.domain.*;
import com.renovandelve.domain.armorentity.ArmorEntity;
import com.renovandelve.domain.gamestateentity.Difficulty;
import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.domain.helmetentity.HelmetEntity;
import com.renovandelve.domain.relicentity.RelicEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.domain.weaponentity.WeaponEntity;
import com.renovandelve.dto.*;
import com.renovandelve.dto.request.NewGameRequest;
import com.renovandelve.dto.response.SetupOptionsDTO;
import com.renovandelve.repository.*;
import com.renovandelve.service.redis.GameSessionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Log4j2
@RequiredArgsConstructor
public class GameStateServiceImpl implements GameStateService {
    private final GameStateRepository gameStateRepository;
    private final WeaponRepository weaponRepository;
    private final ArmorRepository armorRepository;
    private final HelmetRepository helmetRepository;
    private final SkillRepository skillRepository;
    private final RelicRepository relicRepository;
    private final GameCharacterRepository gameCharacterRepository;
    private final GameEquipmentRepository gameEquipmentRepository;
    private final GameSkillSlotRepository gameSkillSlotRepository;
    private final GameRelicInventoryRepository gameRelicInventoryRepository;
    private final GameSessionService gameSessionService;

    public static final int STARTING_BUDGET = 800;   // 스타팅 포인트 예산
    private static final int BASE_MAX_HP = 100;
    private static final int BASE_ATTACK = 10;
    private static final int BASE_DEFENSE = 5;
    private static final int MAX_START_SKILLS = 4;
    private static final String DEFAULT_SKILL_NAME = "기본 공격";

    @Override
    public SetupOptionsDTO getSetupOptions() {
        return new SetupOptionsDTO(
                STARTING_BUDGET,
                RecordMapper.mapList(weaponRepository.findAll(), WeaponDTO.class),
                RecordMapper.mapList(armorRepository.findAll(), ArmorDTO.class),
                RecordMapper.mapList(helmetRepository.findAll(), HelmetDTO.class),
                skillRepository.findAll().stream().map(SkillInfoDTO::from).toList(),
                RecordMapper.mapList(relicRepository.findAll(), RelicDTO.class)
        );
    }

    @Override
    @Transactional
    public GameStateEntity createNewGame(NewGameRequest request, MemberEntity member) {
        log.info("새 게임 생성 memberId={}", member.getMemberId());

        // 기존 진행 중 게임은 종료 처리
        gameStateRepository.findByMember_MemberIdAndIsActiveTrue(member.getMemberId())
                .ifPresent(old -> {
                    old.deactivate();
                    gameStateRepository.save(old);
                    gameSessionService.delete(old.getGameStateId());
                    log.info("기존 게임 세션 종료 gameStateId={}", old.getGameStateId());
                });

        Difficulty difficulty = parseDifficulty(request.difficulty());

        // 장비 결정 (미선택 시 가장 저렴한 기본 장비)
        WeaponEntity weapon = resolveWeapon(request.weaponId());
        ArmorEntity armor = resolveArmor(request.armorId());
        HelmetEntity helmet = resolveHelmet(request.helmetId());

        // 스킬 결정 (기본 공격은 항상 포함)
        List<SkillEntity> skills = resolveSkills(request.skillIds());

        // 유물 결정
        List<RelicEntity> relics = resolveRelics(request.relicIds());

        // 예산 검증
        int cost = weapon.getScoreCost() + armor.getScoreCost() + helmet.getScoreCost()
                + skills.stream().mapToInt(SkillEntity::getCost).sum()
                + relics.stream().mapToInt(RelicEntity::getScoreCost).sum();
        if (cost > STARTING_BUDGET) {
            throw new IllegalArgumentException("스타팅 포인트 예산 초과: 사용 " + cost + " / 예산 " + STARTING_BUDGET);
        }

        // 세션 생성
        GameStateEntity gameState = gameStateRepository.save(GameStateEntity.builder()
                .member(member)
                .difficulty(difficulty)
                .currentStage(1)
                .currentNodeId(0L)
                .gold(100)
                .startingPoints(cost)
                .build());

        // 장비 장착
        gameEquipmentRepository.save(GameEquipmentEntity.builder()
                .gameState(gameState).weapon(weapon).armor(armor).helmet(helmet).build());

        // 캐릭터 생성
        int maxHp = BASE_MAX_HP + helmet.getHpBonus();
        int attack = BASE_ATTACK + weapon.getAtkBonus();
        int defense = BASE_DEFENSE + armor.getDefBonus();
        gameCharacterRepository.save(GameCharacterEntity.builder()
                .gameState(gameState).maxHp(maxHp).currentHp(maxHp)
                .attack(attack).defense(defense).build());

        // 스킬 슬롯
        for (int i = 0; i < skills.size(); i++) {
            gameSkillSlotRepository.save(GameSkillSlotEntity.builder()
                    .gameState(gameState).skill(skills.get(i)).slotIndex(i).coolTime(0).build());
        }

        // 유물 인벤토리
        for (RelicEntity relic : relics) {
            gameRelicInventoryRepository.save(GameRelicInventoryEntity.builder()
                    .gameState(gameState).relic(relic).build());
        }

        // 노드 세션 초기화 (EXPLORE)
        gameSessionService.delete(gameState.getGameStateId());
        gameSessionService.getOrCreate(gameState.getGameStateId());

        log.info("새 게임 준비 완료 gameStateId={} cost={}", gameState.getGameStateId(), cost);
        return gameState;
    }

    @Override
    public Optional<GameStateEntity> findActiveGame(MemberEntity member) {
        return gameStateRepository.findByMember_MemberIdAndIsActiveTrue(member.getMemberId());
    }

    @Override
    @Transactional
    public void abandonGame(MemberEntity member) {
        gameStateRepository.findByMember_MemberIdAndIsActiveTrue(member.getMemberId())
                .ifPresent(gameState -> {
                    gameState.deactivate();
                    gameStateRepository.save(gameState);
                    gameSessionService.delete(gameState.getGameStateId());
                    log.info("게임 포기 gameStateId={}", gameState.getGameStateId());
                });
    }

    /* HELPER METHODS */

    private Difficulty parseDifficulty(String value) {
        if (value == null || value.isBlank()) return Difficulty.NORMAL;
        try {
            return Difficulty.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 난이도: " + value);
        }
    }

    private WeaponEntity resolveWeapon(Long id) {
        if (id != null) {
            return weaponRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 무기입니다. weaponId=" + id));
        }
        return weaponRepository.findAll().stream()
                .min(Comparator.comparingInt(WeaponEntity::getScoreCost))
                .orElseThrow(() -> new IllegalStateException("무기 데이터가 없습니다."));
    }

    private ArmorEntity resolveArmor(Long id) {
        if (id != null) {
            return armorRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 갑옷입니다. armorId=" + id));
        }
        return armorRepository.findAll().stream()
                .min(Comparator.comparingInt(ArmorEntity::getScoreCost))
                .orElseThrow(() -> new IllegalStateException("갑옷 데이터가 없습니다."));
    }

    private HelmetEntity resolveHelmet(Long id) {
        if (id != null) {
            return helmetRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 투구입니다. helmetId=" + id));
        }
        return helmetRepository.findAll().stream()
                .min(Comparator.comparingInt(HelmetEntity::getScoreCost))
                .orElseThrow(() -> new IllegalStateException("투구 데이터가 없습니다."));
    }

    private List<SkillEntity> resolveSkills(List<Long> skillIds) {
        LinkedHashMap<Long, SkillEntity> selected = new LinkedHashMap<>();

        // 기본 공격은 항상 0번 슬롯 (이름이 없으면 최저 비용 스킬로 폴백)
        skillRepository.findByName(DEFAULT_SKILL_NAME)
                .or(() -> skillRepository.findAll().stream()
                        .min(Comparator.comparingInt(SkillEntity::getCost)))
                .ifPresent(basic -> selected.put(basic.getSkillId(), basic));

        if (skillIds != null) {
            for (Long id : skillIds) {
                if (selected.size() >= MAX_START_SKILLS) break;
                SkillEntity skill = skillRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 스킬입니다. skillId=" + id));
                selected.putIfAbsent(skill.getSkillId(), skill);
            }
        }

        if (selected.isEmpty()) {
            throw new IllegalStateException("스킬 데이터가 없습니다. data.sql 시드 데이터를 확인하세요.");
        }
        return new ArrayList<>(selected.values());
    }

    private List<RelicEntity> resolveRelics(List<Long> relicIds) {
        if (relicIds == null || relicIds.isEmpty()) return List.of();
        LinkedHashMap<Long, RelicEntity> selected = new LinkedHashMap<>();
        for (Long id : relicIds) {
            RelicEntity relic = relicRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유물입니다. relicId=" + id));
            selected.putIfAbsent(relic.getRelicId(), relic);
        }
        return new ArrayList<>(selected.values());
    }
}
