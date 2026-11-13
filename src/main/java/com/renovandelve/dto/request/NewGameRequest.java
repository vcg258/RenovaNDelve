package com.renovandelve.dto.request;

import java.util.List;

/**
 * 새 게임 생성 요청.
 * 장비/스킬/유물은 ID로만 받고, 실제 검증(존재 여부, 스타팅 포인트 예산)은 서버가 수행한다.
 */
public record NewGameRequest(
        String difficulty,      // EASY / NORMAL / HARD / HELL
        Long weaponId,          // null이면 기본 무기(scoreCost 0)
        Long armorId,           // null이면 기본 갑옷
        Long helmetId,          // null이면 기본 투구
        @jakarta.validation.constraints.Size(max = 10, message = "선택한 스킬이 너무 많습니다.")
        List<Long> skillIds,    // 비어있으면 기본 공격만
        @jakarta.validation.constraints.Size(max = 10, message = "선택한 서약이 너무 많습니다.")
        List<String> pacts,     // 서약 코드 목록 (선택 사항)

        @jakarta.validation.constraints.Size(max = 20, message = "선택한 유물이 너무 많습니다.")
        List<Long> relicIds     // 선택 사항
) {
}
