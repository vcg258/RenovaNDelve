package com.renovandelve.game.dto.response;

import com.renovandelve.catalog.dto.ArmorDTO;
import com.renovandelve.catalog.dto.HelmetDTO;
import com.renovandelve.catalog.dto.RelicDTO;
import com.renovandelve.game.dto.SkillInfoDTO;
import com.renovandelve.catalog.dto.WeaponDTO;

import java.util.List;

/**
 * 새 게임 세팅 화면에서 선택 가능한 장비 / 스킬 / 유물 목록과 스타팅 포인트 예산.
 */
public record SetupOptionsDTO(
        int budget,       // 총 예산 (기본 + 도감 보너스)
        int bonusBudget,  // 그중 도감 수집으로 얻은 보너스
        List<WeaponDTO> weapons,
        List<ArmorDTO> armors,
        List<HelmetDTO> helmets,
        List<SkillInfoDTO> skills,
        List<RelicDTO> relics,
        List<PactDTO> pacts   // 선택 가능한 서약 (스스로 거는 제약, 점수 보너스)
) {
}
