package com.renovandelve.game.service;

import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.game.domain.GameCharacterEntity;
import com.renovandelve.game.domain.GameRelicInventoryEntity;
import com.renovandelve.game.domain.GameSkillSlotEntity;
import com.renovandelve.game.domain.WraithEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.dto.response.WraithDTO;
import com.renovandelve.game.engine.Faction;
import com.renovandelve.game.engine.SessionMonster;
import com.renovandelve.game.repository.WraithRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * 전생의 망령: 패배한 원정을 기록하고, 다음 원정에서 같은 자리에 적으로 세운다.
 *
 * 규칙은 전부 서버가 정한다. AI는 망령의 대사만 쓴다({@code WraithVoiceService}).
 * - 회원당 기다리는 망령은 하나. 새로 쓰러지면 이전 망령은 흩어진다
 * - 쓰러진 스테이지의 쓰러진 노드(2~9로 맞춤)에 강적으로 나타난다
 * - 처치하면 그 원정에서 가졌던 유물 하나를 보상 후보로 돌려받는다
 * - 세 번 달아나면 흩어진다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WraithService {

    /** 망령의 이름. 화면은 이 이름으로 망령 스프라이트를 고른다 */
    public static final String WRAITH_NAME = "전생의 망령";
    /** 같은 스테이지 강적보다 조금 더 단단하다 */
    static final double HP_MULTIPLIER = 1.25;
    static final int SCORE_REWARD = 200;
    private static final int MAX_SKILLS = 3;

    private final WraithRepository wraithRepository;
    private final Random random = new Random();

    /**
     * 패배한 원정을 망령으로 남긴다. 기다리던 망령이 있으면 흩어진다.
     *
     * @param gameState  패배한 원정
     * @param killerName 쓰러뜨린 적 (조우에서 쓰러졌으면 null)
     * @param memories   그 원정에서 내린 선택 몇 줄
     * @return 새 망령
     */
    @Transactional
    public WraithEntity recordDeath(GameStateEntity gameState, String killerName, List<String> memories) {
        Long memberId = gameState.getMember().getMemberId();
        wraithRepository.findByMemberIdAndStatus(memberId, WraithEntity.Status.ACTIVE).forEach(WraithEntity::fade);

        int deathNode = gameState.getCurrentNodeId() == null ? 1 : gameState.getCurrentNodeId().intValue();
        Faction dominant = gameState.alignment().dominant();
        WraithEntity wraith = WraithEntity.builder()
                .memberId(memberId)
                .sourceGameStateId(gameState.getGameStateId())
                .stage(gameState.getCurrentStage())
                .node(WraithEntity.appearNode(deathNode))
                .level(level(gameState))
                .killerName(killerName)
                .skills(mainSkills(gameState.getGameSkillSlot()))
                .relicIds(relicIds(gameState.getGameRelicInventory()))
                .faction(dominant == null ? null : dominant.name())
                .memories(memories)
                .build();
        wraithRepository.save(wraith);
        log.info("망령 기록 memberId={} stage={} node={}", memberId, wraith.getStage(), wraith.getNode());
        return wraith;
    }

    /**
     * 이 자리에서 기다리는 망령을 찾는다. 같은 원정이 남긴 망령은 그 원정에 나오지 않는다.
     *
     * @param memberId    회원 ID
     * @param gameStateId 지금 원정
     * @param stage       스테이지
     * @param node        노드
     * @return 망령
     */
    @Transactional(readOnly = true)
    public Optional<WraithEntity> waitingAt(Long memberId, Long gameStateId, int stage, int node) {
        return wraithRepository.findFirstByMemberIdAndStatusOrderByCreatedAtDesc(memberId, WraithEntity.Status.ACTIVE)
                .filter(w -> w.getStage() == stage && w.getNode() == node)
                .filter(w -> !Objects.equals(w.getSourceGameStateId(), gameStateId));
    }

    /**
     * 원정 준비 화면에 보여 줄, 기다리는 망령.
     *
     * @param memberId 회원 ID
     * @return 망령 안내
     */
    @Transactional(readOnly = true)
    public Optional<WraithDTO> current(Long memberId) {
        return wraithRepository.findFirstByMemberIdAndStatusOrderByCreatedAtDesc(memberId, WraithEntity.Status.ACTIVE)
                .map(WraithDTO::from);
    }

    /**
     * 망령을 처치했다. 그 원정에서 가졌던 유물 중 지금 없는 것 하나를 돌려준다.
     *
     * @param wraithId    망령 ID
     * @param ownedRelics 지금 가진 유물 ID
     * @return 돌려받을 유물 ID (돌려줄 것이 없으면 비어 있음)
     */
    @Transactional
    public Optional<Long> slay(Long wraithId, List<Long> ownedRelics) {
        return wraithRepository.findById(wraithId).filter(WraithEntity::isActive).flatMap(wraith -> {
            wraith.slay();
            List<Long> candidates = wraith.relicIdList().stream().filter(id -> !ownedRelics.contains(id)).toList();
            return candidates.isEmpty() ? Optional.empty()
                    : Optional.of(candidates.get(random.nextInt(candidates.size())));
        });
    }

    /**
     * 망령에게서 달아났다.
     *
     * @param wraithId 망령 ID
     * @return 이번에 흩어졌으면 true
     */
    @Transactional
    public boolean escape(Long wraithId) {
        return wraithRepository.findById(wraithId).filter(WraithEntity::isActive)
                .map(WraithEntity::escapeFrom).orElse(false);
    }

    /**
     * 기준 능력치로 만든 강적 몬스터에 망령의 모습을 입힌다.
     *
     * @param monster 서버가 스테이지·난이도로 능력치를 정한 강적
     * @param wraith  망령
     */
    public static void dress(SessionMonster monster, WraithEntity wraith) {
        int hp = Math.max(1, (int) Math.round(monster.getMaxHp() * HP_MULTIPLIER));
        monster.setMaxHp(hp);
        monster.setCurrentHp(hp);
        monster.setName(WRAITH_NAME);
        monster.setMonsterId(null);
        monster.setScoreReward(SCORE_REWARD);
        monster.setMoves(wraith.skillNames());
        monster.setDescription("Lv.%d. 지난 원정에서 이곳에 쓰러진 당신이다.%s".formatted(wraith.getLevel(),
                wraith.getKillerName() == null ? "" : " 마지막 상대는 " + wraith.getKillerName() + "였다."));
    }

    /** 계수가 높은 순으로 기본 공격이 아닌 스킬 이름 3개. 없으면 기본 공격이라도 */
    static List<String> mainSkills(List<GameSkillSlotEntity> slots) {
        if (slots == null) return List.of();
        List<SkillEntity> skills = slots.stream().map(GameSkillSlotEntity::getSkill).filter(Objects::nonNull).toList();
        List<String> main = skills.stream().filter(s -> !s.isBasic())
                .sorted(Comparator.comparing((SkillEntity s) -> s.getCoefficient() == null ? BigDecimal.ZERO : s.getCoefficient())
                        .reversed())
                .limit(MAX_SKILLS).map(SkillEntity::getName).toList();
        return main.isEmpty() ? skills.stream().limit(1).map(SkillEntity::getName).toList() : main;
    }

    private static List<Long> relicIds(List<GameRelicInventoryEntity> inventory) {
        if (inventory == null) return List.of();
        return inventory.stream().map(i -> i.getRelic().getRelicId()).toList();
    }

    private static int level(GameStateEntity gameState) {
        if (gameState.getGameCharacter() == null) return 1;
        return gameState.getGameCharacter().stream().findFirst().map(GameCharacterEntity::getLevel).orElse(1);
    }
}
