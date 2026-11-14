package com.renovandelve.service;

import com.renovandelve.domain.GameSkillSlotEntity;
import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.repository.GameSkillSlotRepository;
import com.renovandelve.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 스킬 슬롯에 스킬을 넣는 규칙 (전투 보상과 상점 구매가 함께 쓴다).
 *
 * 슬롯은 최대 8개다. 빈 슬롯이 있으면 새 슬롯에 넣고,
 * 버릴 슬롯(replaceSlotId)을 지정하면 그 슬롯의 스킬을 새 스킬로 바꾼다.
 * 가득 찼는데 버릴 슬롯을 지정하지 않으면 거부한다 -> 클라이언트가 버릴 스킬을 고르게 한다.
 */
@Service
@RequiredArgsConstructor
public class SkillSlotService {
    public static final int MAX_SLOTS = 8;
    /** 가득 찬 상태에서 버릴 슬롯 없이 요청했을 때의 안내 문구 */
    public static final String FULL_MESSAGE = "스킬 슬롯이 가득 찼습니다. 버릴 스킬을 골라주세요. (최대 " + MAX_SLOTS + "개)";

    /** 기본 공격은 스킬을 이만큼(기본 공격 포함) 가졌을 때만 버리거나 바꿀 수 있다 */
    public static final int MIN_SLOTS_TO_DROP_BASIC = 3;
    public static final String BASIC_LOCKED_MESSAGE =
            "기본 공격은 스킬이 " + MIN_SLOTS_TO_DROP_BASIC + "개 이상일 때만 버릴 수 있습니다.";
    public static final String LAST_SKILL_MESSAGE = "마지막 남은 스킬은 버릴 수 없습니다.";

    /**
     * 기본 공격인지. 시드 데이터에서 스타팅 포인트 비용이 0인 스킬은 기본 공격 하나뿐이고,
     * 원정 준비 화면도 같은 기준(비용 0)으로 기본 공격을 구분한다.
     */
    public static boolean isBasic(SkillEntity skill) {
        return skill != null && skill.getCost() == 0;
    }

    /** 이 슬롯의 스킬을 버려도(또는 다른 스킬로 바꿔도) 되는지 확인한다 */
    private void requireDroppable(List<GameSkillSlotEntity> slots, GameSkillSlotEntity target) {
        // 기본 공격은 쿨타임이 없는 유일한 스킬이다. 스킬이 한두 개뿐일 때 버리면 매 턴 쓸 스킬이 없어진다
        if (isBasic(target.getSkill()) && slots.size() < MIN_SLOTS_TO_DROP_BASIC) {
            throw new IllegalStateException(BASIC_LOCKED_MESSAGE);
        }
    }

    /** 남의 게임 슬롯을 지정하지 못하도록 이 게임의 슬롯 안에서만 찾는다 */
    private GameSkillSlotEntity ownSlot(List<GameSkillSlotEntity> slots, Long slotId) {
        return slots.stream()
                .filter(slot -> slotId.equals(slot.getSlotId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("보유하지 않은 스킬 슬롯입니다. slotId=" + slotId));
    }

    /**
     * 스킬 하나를 버린다 (새 스킬을 얻지 않고 슬롯만 비운다).
     * @return 버린 스킬
     */
    public SkillEntity discard(GameStateEntity gameState, Long slotId) {
        List<GameSkillSlotEntity> slots = gameState.getGameSkillSlot();
        GameSkillSlotEntity target = ownSlot(slots, slotId);
        if (slots.size() <= 1) {
            throw new IllegalStateException(LAST_SKILL_MESSAGE);
        }
        requireDroppable(slots, target);

        slots.remove(target);
        gameSkillSlotRepository.delete(target);
        return target.getSkill();
    }

    private final GameSkillSlotRepository gameSkillSlotRepository;
    private final SkillRepository skillRepository;

    /**
     * @param replaceSlotId 버릴 슬롯의 ID. null이면 빈 슬롯에 추가한다
     * @return 새로 배운 스킬
     */
    public SkillEntity learn(GameStateEntity gameState, Long skillId, Long replaceSlotId) {
        List<GameSkillSlotEntity> slots = gameState.getGameSkillSlot();

        if (slots.stream().anyMatch(slot -> slot.getSkill() != null && slot.getSkill().getSkillId().equals(skillId))) {
            throw new IllegalStateException("이미 보유 중인 스킬입니다.");
        }
        SkillEntity skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new NoSuchElementException("스킬을 찾을 수 없습니다."));

        if (replaceSlotId != null) {
            GameSkillSlotEntity target = ownSlot(slots, replaceSlotId);
            requireDroppable(slots, target);
            target.setSkill(skill);
            target.setCoolTime(0);
            gameSkillSlotRepository.save(target);
            return skill;
        }

        if (slots.size() >= MAX_SLOTS) {
            throw new IllegalStateException(FULL_MESSAGE);
        }

        Set<Integer> used = slots.stream().map(GameSkillSlotEntity::getSlotIndex).collect(Collectors.toSet());
        int nextIndex = 0;
        while (used.contains(nextIndex)) nextIndex++;

        GameSkillSlotEntity newSlot = GameSkillSlotEntity.builder()
                .gameState(gameState).skill(skill).slotIndex(nextIndex).coolTime(0).build();
        slots.add(newSlot);
        // game_state 쪽에 cascade가 없으므로 직접 저장한다
        gameSkillSlotRepository.save(newSlot);
        return skill;
    }
}
