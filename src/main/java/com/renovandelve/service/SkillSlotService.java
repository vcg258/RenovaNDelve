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
            // 남의 게임 슬롯을 지정하지 못하도록 이 게임의 슬롯 안에서만 찾는다
            GameSkillSlotEntity target = slots.stream()
                    .filter(slot -> replaceSlotId.equals(slot.getSlotId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("보유하지 않은 스킬 슬롯입니다. slotId=" + replaceSlotId));
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
