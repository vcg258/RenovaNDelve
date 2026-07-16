package com.renovandelve.dto.response;

import com.renovandelve.dto.*;

import java.util.List;

/**
 * 새 게임 세팅 화면에서 선택 가능한 장비 / 스킬 / 유물 목록과 스타팅 포인트 예산.
 */
public record SetupOptionsDTO(
        int budget,
        List<WeaponDTO> weapons,
        List<ArmorDTO> armors,
        List<HelmetDTO> helmets,
        List<SkillInfoDTO> skills,
        List<RelicDTO> relics
) {
}
