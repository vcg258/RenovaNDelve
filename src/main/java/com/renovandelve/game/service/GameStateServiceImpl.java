package com.renovandelve.game.service;

import com.renovandelve.global.exception.BusinessException;
import com.renovandelve.global.exception.ErrorCode;
import com.renovandelve.catalog.mapper.CatalogMapper;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
import com.renovandelve.game.domain.gamestateentity.Difficulty;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.catalog.domain.helmetentity.HelmetEntity;
import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import com.renovandelve.game.dto.request.NewGameRequest;
import com.renovandelve.game.dto.response.SetupOptionsDTO;
import com.renovandelve.game.engine.Pact;
import com.renovandelve.catalog.repository.ArmorRepository;
import com.renovandelve.collection.repository.CollectionArmorRepository;
import com.renovandelve.collection.repository.CollectionConsumableRepository;
import com.renovandelve.collection.repository.CollectionHelmetRepository;
import com.renovandelve.collection.repository.CollectionRelicRepository;
import com.renovandelve.collection.repository.CollectionSkillRepository;
import com.renovandelve.collection.repository.CollectionWeaponRepository;
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.catalog.repository.HelmetRepository;
import com.renovandelve.catalog.repository.RelicRepository;
import com.renovandelve.catalog.repository.SkillRepository;
import com.renovandelve.catalog.repository.WeaponRepository;
import com.renovandelve.game.service.redis.GameSessionService;
import org.springframework.transaction.annotation.Transactional;
import com.renovandelve.game.mapper.GameMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameStateServiceImpl implements GameStateService {
    private final GameStateRepository gameStateRepository;
    private final KingdomService kingdomService;
    private final ChronicleService chronicleService;
    private final WeaponRepository weaponRepository;
    private final ArmorRepository armorRepository;
    private final HelmetRepository helmetRepository;
    private final SkillRepository skillRepository;
    private final RelicRepository relicRepository;
    private final GameSessionService gameSessionService;
    private final CollectionWeaponRepository collectionWeaponRepository;
    private final CollectionArmorRepository collectionArmorRepository;
    private final CollectionHelmetRepository collectionHelmetRepository;
    private final CollectionSkillRepository collectionSkillRepository;
    private final CollectionRelicRepository collectionRelicRepository;
    private final CollectionConsumableRepository collectionConsumableRepository;
    private final CatalogMapper catalogMapper;
    private final GameMapper gameMapper;

    public static final int STARTING_BUDGET = 800;   // 스타팅 포인트 기본 예산
    // 메타 진행: 도감에 등록한 항목 1종마다 다음 판의 예산이 늘어난다 (죽어도 남는 성장)
    public static final int BUDGET_PER_COLLECTION = 5;
    public static final int MAX_BONUS_BUDGET = 200;
    private static final int MAX_START_SKILLS = 4;
    private static final String DEFAULT_SKILL_NAME = "기본 공격";

    /**
     * 원정 준비 화면의 선택지와 예산. 예산은 기본 800에 도감 수집 보너스(1종당 5, 최대 200)를 더한다.
     *
     * @param member 회원
     * @return 원정 준비 옵션
     */
    @Override
    public SetupOptionsDTO getSetupOptions(MemberEntity member) {
        int bonus = bonusBudget(member);
        return new SetupOptionsDTO(
                STARTING_BUDGET + bonus,
                bonus,
                catalogMapper.toWeaponDTOs(weaponRepository.findAll()),
                catalogMapper.toArmorDTOs(armorRepository.findAll()),
                catalogMapper.toHelmetDTOs(helmetRepository.findAll()),
                catalogMapper.toSkillInfoDTOs(skillRepository.findAll()),
                catalogMapper.toRelicDTOs(relicRepository.findAll()),
                Arrays.stream(Pact.values()).map(gameMapper::toPactDTO).toList()
        );
    }

    /**
     * 새 원정을 만든다. 진행 중인 원정은 포기 처리하고, 고른 장비·스킬·유물의 비용이 예산 안인지 검증한 뒤
     * 게임·장비·캐릭터를 만들고 스킬과 유물은 애그리거트 루트에 넣는다(cascade로 함께 저장).
     *
     * @param request 난이도, 장비, 스킬, 유물, 서약
     * @param member  회원
     * @return 새 게임
     * @throws BusinessException 예산 초과, 알 수 없는 장비·서약
     */
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

        // 직전 원정이 결과 화면(회고)을 거치지 않고 끝났다면 연대기의 그 장이 비어 있다 -> 서버가 사실만으로 채운다
        fillMissingChapter(member);

        // 장비 결정 (미선택 시 가장 저렴한 기본 장비)
        WeaponEntity weapon = resolveWeapon(request.weaponId());
        ArmorEntity armor = resolveArmor(request.armorId());
        HelmetEntity helmet = resolveHelmet(request.helmetId());

        // 스킬 결정 (기본 공격은 항상 포함)
        List<SkillEntity> skills = resolveSkills(request.skillIds());

        // 유물 결정
        List<RelicEntity> relics = resolveRelics(request.relicIds());

        // 서약 (알 수 없는 코드는 400)
        Set<Pact> pacts = Pact.fromCodes(request.pacts());

        // 예산 검증
        int cost = weapon.getScoreCost() + armor.getScoreCost() + helmet.getScoreCost()
                + skills.stream().mapToInt(SkillEntity::getCost).sum()
                + relics.stream().mapToInt(RelicEntity::getScoreCost).sum();
        int budget = STARTING_BUDGET + bonusBudget(member);
        if (cost > budget) {
            throw new BusinessException(ErrorCode.BUDGET_EXCEEDED, cost, budget);
        }

        // 게임 하나만 저장하면 장비·캐릭터·스킬 슬롯·유물은 cascade로 함께 저장된다
        GameStateEntity gameState = gameStateRepository.save(GameStateEntity.start(
                member, difficulty, cost, pacts, kingdomService.currentState(),
                weapon, armor, helmet, skills, relics));

        // 노드 세션 초기화 (EXPLORE)
        gameSessionService.delete(gameState.getGameStateId());
        gameSessionService.getOrCreate(gameState.getGameStateId());

        log.info("새 게임 준비 완료 gameStateId={} cost={}", gameState.getGameStateId(), cost);
        return gameState;
    }


    /**
     * 진행 중인 원정을 포기하고 Redis 세션을 지운다. 없으면 아무것도 하지 않는다.
     *
     * @param member 회원
     */
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

    /** 도감 수집 수에 비례한 스타팅 포인트 보너스 */
    /**
     * 직전에 끝난 원정의 연대기 장이 없으면 서버가 사실만으로 쓴다 (AI 호출 없음). 실패해도 새 원정은 시작한다.
     *
     * @param member 회원
     */
    private void fillMissingChapter(MemberEntity member) {
        try {
            gameStateRepository.findByMember_MemberIdOrderByCreatedAtDesc(member.getMemberId()).stream()
                    .filter(g -> "CLEAR".equals(g.getResult()) || "DEFEAT".equals(g.getResult()))
                    .findFirst()
                    .ifPresent(last -> chronicleService.record(last, "CLEAR".equals(last.getResult()), null));
        } catch (Exception e) {
            log.warn("연대기 빈 장 채우기 실패 memberId={} 원인={}", member.getMemberId(), e.getMessage());
        }
    }

    private int bonusBudget(MemberEntity member) {
        long collected = collectionWeaponRepository.countByMember(member)
                + collectionArmorRepository.countByMember(member)
                + collectionHelmetRepository.countByMember(member)
                + collectionSkillRepository.countByMember(member)
                + collectionRelicRepository.countByMember(member)
                + collectionConsumableRepository.countByMember(member);
        return (int) Math.min(MAX_BONUS_BUDGET, collected * BUDGET_PER_COLLECTION);
    }

    /**
     * 난이도 문자열을 바꾼다. 비어 있으면 보통.
     *
     * @param value 난이도 문자열
     * @return 난이도
     * @throws BusinessException 알 수 없는 값이면
     */
    private Difficulty parseDifficulty(String value) {
        if (value == null || value.isBlank()) return Difficulty.NORMAL;
        try {
            return Difficulty.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNKNOWN_DIFFICULTY, value);
        }
    }

    /**
     * 고른 무기. 고르지 않았으면 가장 싼 무기.
     *
     * @param id 무기 ID (없으면 null)
     * @return 무기
     */
    private WeaponEntity resolveWeapon(Long id) {
        if (id != null) {
            return weaponRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "존재하지 않는 무기입니다. weaponId=" + id));
        }
        return weaponRepository.findAll().stream()
                .min(Comparator.comparingInt(WeaponEntity::getScoreCost))
                .orElseThrow(() -> new IllegalStateException("무기 데이터가 없습니다."));
    }

    /**
     * 고른 갑옷. 고르지 않았으면 가장 싼 갑옷.
     *
     * @param id 갑옷 ID (없으면 null)
     * @return 갑옷
     */
    private ArmorEntity resolveArmor(Long id) {
        if (id != null) {
            return armorRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "존재하지 않는 갑옷입니다. armorId=" + id));
        }
        return armorRepository.findAll().stream()
                .min(Comparator.comparingInt(ArmorEntity::getScoreCost))
                .orElseThrow(() -> new IllegalStateException("갑옷 데이터가 없습니다."));
    }

    /**
     * 고른 투구. 고르지 않았으면 가장 싼 투구.
     *
     * @param id 투구 ID (없으면 null)
     * @return 투구
     */
    private HelmetEntity resolveHelmet(Long id) {
        if (id != null) {
            return helmetRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "존재하지 않는 투구입니다. helmetId=" + id));
        }
        return helmetRepository.findAll().stream()
                .min(Comparator.comparingInt(HelmetEntity::getScoreCost))
                .orElseThrow(() -> new IllegalStateException("투구 데이터가 없습니다."));
    }

    /**
     * 시작 스킬. 기본 공격을 항상 0번에 두고, 고른 스킬을 최대 3개까지 더한다(중복 제거).
     *
     * @param skillIds 고른 스킬 ID
     * @return 시작 스킬
     */
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
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "존재하지 않는 스킬입니다. skillId=" + id));
                selected.putIfAbsent(skill.getSkillId(), skill);
            }
        }

        if (selected.isEmpty()) {
            throw new IllegalStateException("스킬 데이터가 없습니다. 시드 데이터(db/migration/R__seed_data.sql)를 확인하세요.");
        }
        return new ArrayList<>(selected.values());
    }

    /**
     * 시작 유물 (중복 제거).
     *
     * @param relicIds 고른 유물 ID
     * @return 시작 유물
     */
    private List<RelicEntity> resolveRelics(List<Long> relicIds) {
        if (relicIds == null || relicIds.isEmpty()) return List.of();
        LinkedHashMap<Long, RelicEntity> selected = new LinkedHashMap<>();
        for (Long id : relicIds) {
            RelicEntity relic = relicRepository.findById(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST, "존재하지 않는 유물입니다. relicId=" + id));
            selected.putIfAbsent(relic.getRelicId(), relic);
        }
        return new ArrayList<>(selected.values());
    }
}
