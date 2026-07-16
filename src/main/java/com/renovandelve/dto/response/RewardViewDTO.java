package com.renovandelve.dto.response;

import com.renovandelve.dto.ConsumableDTO;
import com.renovandelve.dto.RelicDTO;
import com.renovandelve.dto.SkillInfoDTO;

import java.util.List;

/**
 * 전투 승리 보상. exp / gold는 즉시 적용되며, 아이템은 하나만 선택 가능.
 */
public record RewardViewDTO(
        int exp,
        int gold,
        List<SkillInfoDTO> skills,
        List<RelicDTO> relics,
        List<ConsumableDTO> consumables
) {
}
