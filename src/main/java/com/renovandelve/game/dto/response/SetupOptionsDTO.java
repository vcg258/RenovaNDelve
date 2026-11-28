package com.renovandelve.game.dto.response;

import com.renovandelve.catalog.dto.ArmorDTO;
import com.renovandelve.catalog.dto.HelmetDTO;
import com.renovandelve.catalog.dto.RelicDTO;
import com.renovandelve.game.dto.SkillInfoDTO;
import com.renovandelve.catalog.dto.WeaponDTO;

import com.renovandelve.game.domain.gamestateentity.Difficulty;

import java.util.List;

/**
 * 새 게임 세팅 화면에서 선택 가능한 장비 / 스킬 / 유물 목록과 스타팅 포인트 예산.
 */
public record SetupOptionsDTO(
        int budget,       // 총 예산 (기본 + 도감 보너스)
        int bonusBudget,  // 그중 도감 수집으로 얻은 보너스
        int maxSkillPicks, // 고를 수 있는 스킬 수 (기본 공격 제외)
        List<WeaponDTO> weapons,
        List<ArmorDTO> armors,
        List<HelmetDTO> helmets,
        List<SkillInfoDTO> skills,
        List<RelicDTO> relics,
        List<PactDTO> pacts,  // 선택 가능한 서약 (스스로 거는 제약, 점수 보너스)
        SetupLocksDTO locked, // 아직 고를 수 없는 항목 (희귀·영웅은 도감에 등록하면 해금)
        int budgetPerCollection, // 도감 1종마다 늘어나는 예산
        int maxBonusBudget,      // 도감 보너스 상한
        Difficulty bestCleared   // 클리어한 가장 높은 난이도 (없으면 null). 화면이 다음 목표를 보여 준다
) {
}
